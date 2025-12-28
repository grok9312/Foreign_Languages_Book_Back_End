package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails; // 引入 UserDetails

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import
// ⚠️ 這裡需要定義一個 Role 枚舉類 (例如 Role.USER, Role.ADMIN)

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user")
// 讓 User 實現 UserDetails 介面
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items"}) // 移除 "items" 可能導致前端無法看到訂單明細
public class User implements UserDetails{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId; // 關鍵：使用 userId 確保與 Controller 的 getCurrentUserId 方法一致
    private String username;

    @Column(unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role; // 假設您有一個 Role 枚舉

    // =======================================================
    // 實現 UserDetails 介面方法
    // =======================================================
    // 🎯 新增欄位：帳號啟用狀態
    private boolean isActive = true; // 預設值為 true (啟用)
    /**
     * 獲取用戶的角色/權限 (必填)
     */

    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 將 Role 枚舉轉換為 Spring Security 的 SimpleGrantedAuthority
        // 注意：角色名稱必須以 "ROLE_" 為前綴 (e.g., ROLE_ADMIN, ROLE_USER)
        if (this.role == null) {
            return List.of();
        }

        // 檢查點：使用 toUpperCase() 確保字串匹配
        String roleName = this.role.name().toUpperCase();
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    /**
     * 獲取密碼 (必填)
     */

    public String getPassword() {
        return password;
    }

    /**
     * 獲取用戶名，這裡使用 Email 作為用戶登入名 (必填)
     */

    @Override
    public String getUsername() {
        return email;
    }
    // 🎯 新增這個方法，給前端或 DTO 抓取「真正的名字」
    public String getRealName() {
        return username; // 回傳資料庫中真正的名字欄位
    }
    // 帳號是否未過期 (通常返回 true)

    public boolean isAccountNonExpired() {
        return true;
    }

    // 帳號是否未鎖定 (通常返回 true)

    public boolean isAccountNonLocked() {
        return true;
    }

    // 憑證 (密碼) 是否未過期 (通常返回 true)

    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }

    // 帳號是否啟用 (通常返回 true)
    public boolean getIsActive() {
        return isActive;
    }
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    // 手動添加這兩個方法，將其指向 password 欄位
    public String getPasswordHash() {
        return this.password;
    }
    public void setPasswordHash(String hashedPassword) {
        this.password = hashedPassword;
    }

    public Role getRole() {
        return role;
    }
    public void setRole(Role role) {
        this.role = role;
    }

    @CreationTimestamp // 🎯 這會讓 Hibernate 在存檔時自動填入當前時間
    @Column(updatable = false) // 註冊時間一旦寫入就不該被更改
    private LocalDateTime createdAt;

}
