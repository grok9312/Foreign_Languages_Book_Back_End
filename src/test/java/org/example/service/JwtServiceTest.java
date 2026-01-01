package org.example.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.entity.Role;
import org.example.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils; // 修正 import 路徑

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // 使用與 application.properties 相同的配置，確保測試環境一致
    private final String secretKey = "ODhDNVg3dER1Z1E4V21rMmdWc3lQdkt0Y3JqZEdhSExoY1hXcG5yWThMOUU3PQ==";
    private final long jwtExpiration = 86400000; // 24 hours

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 關鍵修正：使用 ReflectionTestUtils 來設定 @Value 注入的私有欄位
        ReflectionTestUtils.setField(jwtService, "secretKey", secretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", jwtExpiration);

        // 建立一個模擬的 UserDetails 物件
        userDetails = User.builder()
                .email("test@example.com")
                .password("password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("產生 Token 並成功解析出用戶名")
    void testGenerateTokenAndExtractUsername() {
        // Act
        // 關鍵修正：將 UserDetails 強制轉型為 User
        String token = jwtService.generateToken((User) userDetails);
        String extractedUsername = jwtService.extractUsername(token);

        // Assert
        assertNotNull(token);
        assertEquals("test@example.com", extractedUsername);
    }

    @Test
    @DisplayName("Token 驗證：對於剛產生的 Token 應為有效")
    void testIsTokenValid_ValidToken() {
        // Arrange
        String token = jwtService.generateToken((User) userDetails);

        // Act
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Token 驗證：用戶名不匹配應為無效")
    void testIsTokenValid_UsernameMismatch() {
        // Arrange
        String token = jwtService.generateToken((User) userDetails);
        UserDetails otherUser = User.builder().email("other@example.com").build();

        // Act
        boolean isValid = jwtService.isTokenValid(token, otherUser);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Token 驗證：過期的 Token 應為無效")
    void testIsTokenValid_ExpiredToken() throws InterruptedException {
        // Arrange
        // 產生一個 1 毫秒後就過期的 Token
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "USER");
        String expiredToken = Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1)) // 1ms expiration
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

        // 等待 10 毫秒，確保 Token 已經過期
        Thread.sleep(10);

        // Act
        boolean isValid = jwtService.isTokenValid(expiredToken, userDetails);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("角色提取：應能正確解析出 Token 中的角色")
    void testExtractRole() {
        // Arrange
        String token = jwtService.generateToken((User) userDetails);

        // Act
        String role = jwtService.extractRole(token);

        // Assert
        assertEquals("USER", role.toString());
    }

    // 輔助方法：從 secret key 取得簽名金鑰
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
