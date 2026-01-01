package org.example.service;

import org.example.dto.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setUserId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setUsername("Test User");
        mockUser.setPassword("encodedPassword");
        mockUser.setRole(Role.USER);
        mockUser.setIsActive(true);
        mockUser.setCreatedAt(LocalDateTime.now());
    }

    // ==========================================
    // 測試 registerUser (註冊)
    // ==========================================

    @Test
    @DisplayName("註冊成功：應建立新用戶並加密密碼")
    void testRegisterUser_Success() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setUsername("New User");
        req.setPassword("password123");

        when(userRepo.existsByEmail(req.getEmail())).thenReturn(false);
        when(encoder.encode(req.getPassword())).thenReturn("encodedPassword123");
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.registerUser(req);

        // Assert
        assertNotNull(result);
        assertEquals(req.getEmail(), result.getEmail());
        assertEquals("encodedPassword123", result.getPassword());
        assertEquals(Role.USER, result.getRole());
        assertTrue(result.getIsActive());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    @DisplayName("註冊失敗：Email 已存在")
    void testRegisterUser_EmailExists() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");

        when(userRepo.existsByEmail(req.getEmail())).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.registerUser(req);
        });

        assertEquals("Email 已存在", exception.getMessage());
        verify(userRepo, never()).save(any(User.class));
    }

    // ==========================================
    // 測試 loginUser (登入)
    // ==========================================

    @Test
    @DisplayName("登入成功：應回傳 Token")
    void testLoginUser_Success() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("password");

        when(userRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(mockUser));
        when(encoder.matches(req.getPassword(), mockUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(mockUser)).thenReturn("mock-jwt-token");

        // Act
        String[] result = userService.loginUser(req);

        // Assert
        assertNotNull(result);
        assertEquals("mock-jwt-token", result[0]);
        assertEquals("USER", result[1]);
    }

    @Test
    @DisplayName("登入失敗：密碼錯誤")
    void testLoginUser_WrongPassword() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("wrongPassword");

        when(userRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(mockUser));
        when(encoder.matches(req.getPassword(), mockUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.loginUser(req);
        });

        assertEquals("密碼錯誤", exception.getMessage());
    }

    @Test
    @DisplayName("登入失敗：帳號已停用")
    void testLoginUser_AccountDeactivated() {
        // Arrange
        mockUser.setIsActive(false);
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("password");

        when(userRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(mockUser));
        when(encoder.matches(req.getPassword(), mockUser.getPasswordHash())).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.loginUser(req);
        });

        assertEquals("帳號已被停用", exception.getMessage());
    }

    // ==========================================
    // 測試 getProfileByEmail (查詢個人資料)
    // ==========================================

    @Test
    @DisplayName("查詢個人資料成功")
    void testGetProfileByEmail_Success() {
        // Arrange
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));

        // Act
        UserProfileResponse response = userService.getProfileByEmail("test@example.com");

        // Assert
        assertNotNull(response);
        assertEquals("Test User", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
    }

    // ==========================================
    // 測試 updateProfileByEmail (更新個人資料)
    // ==========================================

    @Test
    @DisplayName("更新個人資料成功：修改暱稱")
    void testUpdateProfileByEmail_Success() {
        // Arrange
        ProfileDto dto = new ProfileDto();
        dto.setUsername("Updated Name");

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User updatedUser = userService.updateProfileByEmail("test@example.com", dto);

        // Assert
        // 關鍵修正：使用 getRealName() 來驗證暱稱，因為 getUsername() 被覆寫為回傳 Email
        assertEquals("Updated Name", updatedUser.getRealName());
    }

    // ==========================================
    // 測試 changePassword (修改密碼)
    // ==========================================

    @Test
    @DisplayName("修改密碼成功")
    void testChangePassword_Success() {
        // Arrange
        String oldPass = "oldPass";
        String newPass = "newPass";

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(encoder.matches(oldPass, mockUser.getPasswordHash())).thenReturn(true);
        when(encoder.encode(newPass)).thenReturn("encodedNewPass");

        // Act
        userService.changePassword("test@example.com", oldPass, newPass);

        // Assert
        assertEquals("encodedNewPass", mockUser.getPassword());
        verify(userRepo).save(mockUser);
    }

    @Test
    @DisplayName("修改密碼失敗：舊密碼錯誤")
    void testChangePassword_WrongOldPassword() {
        // Arrange
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(encoder.matches("wrongOld", mockUser.getPasswordHash())).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.changePassword("test@example.com", "wrongOld", "newPass");
        });

        assertEquals("舊密碼輸入錯誤，請重新確認。", exception.getMessage());
    }

    // ==========================================
    // 測試 Admin 功能 (停用/切換狀態/修改角色)
    // ==========================================

    @Test
    @DisplayName("管理員停用用戶成功")
    void testDeactivateUser_Success() {
        // Arrange
        when(userRepo.findById(1L)).thenReturn(Optional.of(mockUser));

        // Act
        userService.deactivateUser(1L);

        // Assert
        assertFalse(mockUser.getIsActive());
        verify(userRepo).save(mockUser);
    }

    @Test
    @DisplayName("管理員切換用戶狀態成功")
    void testToggleUserActive_Success() {
        // Arrange
        mockUser.setIsActive(true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(mockUser));

        // Act
        userService.toggleUserActive(1);

        // Assert
        assertFalse(mockUser.getIsActive()); // 應變為 false
        verify(userRepo).save(mockUser);
    }

    @Test
    @DisplayName("管理員修改用戶角色成功")
    void testUpdateUserRole_Success() {
        // Arrange
        when(userRepo.findById(1L)).thenReturn(Optional.of(mockUser));

        // Act
        userService.updateUserRole(1, "ADMIN");

        // Assert
        assertEquals(Role.ADMIN, mockUser.getRole());
        verify(userRepo).save(mockUser);
    }
}
