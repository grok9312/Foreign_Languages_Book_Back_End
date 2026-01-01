package org.example.service;

import org.example.dto.AuthenticationRequest;
import org.example.dto.AuthenticationResponse;
import org.example.dto.RegisterRequest;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private AuthenticationRequest authRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        authRequest = new AuthenticationRequest();
        authRequest.setEmail("test@example.com");
        authRequest.setPassword("password123");

        mockUser = User.builder()
                .userId(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .role(Role.USER)
                .build();
    }

    // ==========================================
    // 測試 register (註冊)
    // ==========================================

    @Test
    @DisplayName("註冊成功：第一位使用者應自動成為 ADMIN")
    void testRegister_FirstUserIsAdmin() {
        // Arrange
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L); // 模擬這是第一位使用者
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");
        
        // 攔截 save 動作以驗證 Role
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            return savedUser; // 直接回傳，方便後續驗證
        });

        // Act
        AuthenticationResponse response = authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("ADMIN", response.getRole()); // 驗證回傳的 Role 是 ADMIN

        // 驗證存入 DB 的 User Role 也是 ADMIN
        verify(userRepository).save(argThat(user -> user.getRole() == Role.ADMIN));
    }

    @Test
    @DisplayName("註冊成功：後續使用者應為 USER")
    void testRegister_SubsequentUserIsUser() {
        // Arrange
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L); // 模擬已經有使用者了
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        // Act
        AuthenticationResponse response = authService.register(registerRequest);

        // Assert
        assertEquals("USER", response.getRole()); // 驗證回傳的 Role 是 USER
        
        // 驗證存入 DB 的 User Role 是 USER
        verify(userRepository).save(argThat(user -> user.getRole() == Role.USER));
    }

    @Test
    @DisplayName("註冊失敗：Email 已存在")
    void testRegister_EmailAlreadyExists() {
        // Arrange
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.of(mockUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            authService.register(registerRequest);
        });

        assertEquals("Email already exists.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==========================================
    // 測試 authenticate (登入)
    // ==========================================

    @Test
    @DisplayName("登入成功：回傳 JWT Token")
    void testAuthenticate_Success() {
        // Arrange
        // 1. 模擬認證成功 (不拋出異常即代表成功)
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        // 2. 模擬從 DB 找到使用者
        when(userRepository.findByEmail(authRequest.getEmail())).thenReturn(Optional.of(mockUser));
        // 3. 模擬生成 Token
        when(jwtService.generateToken(mockUser)).thenReturn("mock-jwt-token");

        // Act
        AuthenticationResponse response = authService.authenticate(authRequest);

        // Assert
        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("USER", response.getRole());
    }

    @Test
    @DisplayName("登入失敗：密碼錯誤 (由 AuthenticationManager 拋出異常)")
    void testAuthenticate_BadCredentials() {
        // Arrange
        // 模擬認證失敗
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> {
            authService.authenticate(authRequest);
        });

        // 確保不會執行後續的 DB 查詢或 Token 生成
        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    @DisplayName("登入失敗：使用者不存在 (雖然通過認證但 DB 找不到人 - 極端情況)")
    void testAuthenticate_UserNotFound() {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        // 模擬 DB 找不到該 Email
        when(userRepository.findByEmail(authRequest.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            authService.authenticate(authRequest);
        });

        assertEquals("User not found", exception.getMessage());
    }
}
