package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {
    private Integer userId;
    private String username;
    private String email;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
