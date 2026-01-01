package org.example.config;

import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminInitializer adminInitializer;

    @Test
    @DisplayName("初始化測試：當管理員不存在時，應自動建立 Admin 帳號")
    void testInitAdmin_CreateIfNotExist() throws Exception {
        // Arrange
        String adminEmail = "admin@test.com";
        when(userRepository.existsByEmail(adminEmail)).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");

        // Act
        // 獲取 Bean 定義的 CommandLineRunner
        CommandLineRunner runner = adminInitializer.initAdmin(userRepository, passwordEncoder);
        // 手動執行 run 方法
        runner.run();

        // Assert
        verify(userRepository, times(1)).save(argThat(user -> 
            user.getEmail().equals(adminEmail) &&
            user.getRole() == Role.ADMIN &&
            user.getPassword().equals("encodedPassword")
        ));
    }

    @Test
    @DisplayName("初始化測試：當管理員已存在時，不應重複建立")
    void testInitAdmin_SkipIfExist() throws Exception {
        // Arrange
        String adminEmail = "admin@test.com";
        when(userRepository.existsByEmail(adminEmail)).thenReturn(true);

        // Act
        CommandLineRunner runner = adminInitializer.initAdmin(userRepository, passwordEncoder);
        runner.run();

        // Assert
        // 驗證 save 從未被呼叫
        verify(userRepository, never()).save(any(User.class));
    }
}
