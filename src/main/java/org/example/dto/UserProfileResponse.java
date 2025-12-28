package org.example.dto;

import lombok.Builder;
import lombok.Data;
import org.example.entity.Role;

import java.time.LocalDateTime;

/**
 * 用於 GET /api/user/profile 返回給前端的用戶資料結構。
 */
@Data
@Builder
public class UserProfileResponse {

    private String username;
    private String email;
    private Role role; // 角色資訊
    private LocalDateTime createdAt;
    // 如果您有其他欄位（如地址、電話），也可以在此添加
}
