package org.example.config;

import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// 強制指定測試用的屬性，確保使用 H2 且自動建表
@TestPropertySource(properties = {
    // 關鍵修正：加入 ;NON_KEYWORDS=USER 以允許 H2 使用 'user' 作為資料表名稱
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
class AdminInitializerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("整合測試：應用程式啟動後，資料庫應包含預設管理員")
    void testAdminCreatedOnStartup() {
        // Debug: 列印出目前資料庫中的所有使用者
        List<User> allUsers = userRepository.findAll();
        System.out.println("DEBUG: Current users in DB count: " + allUsers.size());
        allUsers.forEach(u -> System.out.println("User: " + u.getEmail() + ", Role: " + u.getRole()));

        // Act
        Optional<User> adminUser = userRepository.findByEmail("admin@test.com");

        // Assert
        assertTrue(adminUser.isPresent(), "管理員帳號應該在啟動時自動建立。目前用戶數: " + allUsers.size());
        
        // 關鍵修正：呼叫 getRealName() 來取得 username 欄位，而非 getUsername() (它回傳 email)
        assertEquals("admin", adminUser.get().getRealName());
        
        assertEquals(Role.ADMIN, adminUser.get().getRole());
        
        System.out.println("✅ 整合測試通過：已確認資料庫中存在 admin@test.com");
    }
}
