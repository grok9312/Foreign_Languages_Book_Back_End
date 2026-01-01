package org.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext(); // 確保每個測試開始前 Context 是乾淨的
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // 測試結束後清理
    }

    @Test
    @DisplayName("過濾器測試：沒有 Authorization Header 應直接放行")
    void testDoFilterInternal_NoAuthHeader() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("過濾器測試：Header 格式錯誤 (不是 Bearer 開頭) 應直接放行")
    void testDoFilterInternal_InvalidHeaderFormat() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNzd29yZA==");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("過濾器測試：Token 有效應設定 SecurityContext")
    void testDoFilterInternal_ValidToken() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        String userEmail = "test@example.com";
        UserDetails userDetails = new User(userEmail, "password", Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(userEmail);
        when(userDetailsService.loadUserByUsername(userEmail)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userEmail, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    @DisplayName("過濾器測試：Token 無效不應設定 SecurityContext")
    void testDoFilterInternal_InvalidToken() throws ServletException, IOException {
        // Arrange
        String token = "invalid.jwt.token";
        String userEmail = "test@example.com";
        UserDetails userDetails = new User(userEmail, "password", Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(userEmail);
        when(userDetailsService.loadUserByUsername(userEmail)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(false); // Token 無效

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication()); // 驗證沒有設定認證資訊
    }

    @Test
    @DisplayName("過濾器測試：shouldNotFilter 邏輯 (OPTIONS 請求應跳過)")
    void testShouldNotFilter_Options() throws ServletException {
        // Arrange
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getRequestURI()).thenReturn("/api/any");

        // Act
        boolean shouldNotFilter = jwtAuthenticationFilter.shouldNotFilter(request);

        // Assert
        assertTrue(shouldNotFilter);
    }

    @Test
    @DisplayName("過濾器測試：shouldNotFilter 邏輯 (公開路徑應跳過)")
    void testShouldNotFilter_PublicPath() throws ServletException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        // Act
        boolean shouldNotFilter = jwtAuthenticationFilter.shouldNotFilter(request);

        // Assert
        assertTrue(shouldNotFilter);
    }
    
    @Test
    @DisplayName("過濾器測試：shouldNotFilter 邏輯 (受保護路徑不應跳過)")
    void testShouldNotFilter_ProtectedPath() throws ServletException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/orders");

        // Act
        boolean shouldNotFilter = jwtAuthenticationFilter.shouldNotFilter(request);

        // Assert
        assertFalse(shouldNotFilter);
    }
}
