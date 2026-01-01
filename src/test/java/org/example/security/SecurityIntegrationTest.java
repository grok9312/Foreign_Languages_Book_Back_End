package org.example.security;

import org.example.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// 使用 H2 資料庫配置，避免連線真實 DB
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("公開路徑測試：未登入應可訪問 /api/public/**")
    void testPublicEndpoint_AccessibleWithoutLogin() throws Exception {
        // 修正：使用一個存在的公開 API 路徑，例如搜尋書籍
        mockMvc.perform(get("/api/public/books/search?keyword=test"))
                .andExpect(status().isOk()); 
    }

    @Test
    @DisplayName("受保護路徑測試：未登入訪問 /api/user/** 應被拒絕 (403)")
    void testProtectedEndpoint_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/user/orders"))
                .andExpect(status().isForbidden()); // Spring Security 預設未登入訪問受保護資源回傳 403 (或 401 取決於配置)
    }

    @Test
    @DisplayName("CORS 測試：OPTIONS 預檢請求應被放行")
    void testCorsOptions_ShouldBeAllowed() throws Exception {
        mockMvc.perform(options("/api/user/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()); // 預期 200 OK，表示 CORS 放行
    }

    @Test
    @DisplayName("Token 驗證測試：攜帶有效 Token 應可訪問受保護資源")
    void testProtectedEndpoint_WithValidToken() throws Exception {
        // Arrange
        String validToken = "valid-jwt-token";
        String userEmail = "test@example.com";
        UserDetails userDetails = User.withUsername(userEmail)
                .password("password")
                .authorities("ROLE_USER")
                .build();

        // 模擬 JwtService 行為
        when(jwtService.extractUsername(validToken)).thenReturn(userEmail);
        when(jwtService.isTokenValid(anyString(), any(UserDetails.class))).thenReturn(true);
        
        // 模擬 UserDetailsService 載入用戶
        when(userDetailsService.loadUserByUsername(userEmail)).thenReturn(userDetails);

        // Act & Assert
        mockMvc.perform(get("/api/user/orders")
                        .header("Authorization", "Bearer " + validToken))
                // 這裡我們預期不是 403/401。
                // 如果 Controller 邏輯拋出其他錯誤 (如 404 或 500)，也代表通過了 Security 這一關。
                // 為了保險，我們驗證它 "不是" 403 Forbidden
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403 || status == 401) {
                        throw new AssertionError("Should not return 401/403 with valid token. Status: " + status);
                    }
                });
    }
}
