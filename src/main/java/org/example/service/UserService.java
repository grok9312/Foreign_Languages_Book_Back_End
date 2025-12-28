package org.example.service;
import org.example.dto.*;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    /**
     * 處理會員註冊邏輯
     * @param req 註冊請求 DTO
     * @return 註冊成功的 User 實體
     */
    @Transactional
    public User registerUser(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email 已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));
        // 強制所有前台註冊者角色為 MEMBER
        user.setRole(Role.USER);

        return userRepo.save(user);
    }

    /**
     * 處理會員登入邏輯
     * @param req 登入請求 DTO
     * @return 包含 JWT Token 和角色的字串陣列 [token, role]
     */
    public String[] loginUser(LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("帳號不存在"));

        if (!encoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("密碼錯誤");
        }

        if (!user.getIsActive()) {
            throw new RuntimeException("帳號已被停用");
        }

        String token = jwtService.generateToken(user);

        // 返回 token 和 role
        return new String[] {token, String.valueOf(user.getRole())};
    }

    // =========================================================
    // 受保護 API 邏輯 (會員資料)
    // =========================================================

    /**
     * 🚨 新增：根據 Email 讀取用戶資料，並轉換為 Response DTO。
     * @param email 當前登入用戶的 Email
     * @return UserProfileResponse 包含用戶名、Email、Role
     */
    public UserProfileResponse getProfileByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("無法找到用戶: " + email));

        // 轉換為 DTO 返回
        return UserProfileResponse.builder()
                .email(user.getEmail())
                .username(user.getRealName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt()) // 🎯 補上這一行，魔法才會生效！
                .build();
    }

    /**
     * 🚨 新增：根據 Email 更新用戶的個人資料。
     * @param email 當前登入用戶的 Email
     * @param profileDto 包含新資料的 DTO (只包含要更新的欄位)
     * @return 更新後的 User 實體
     */
    @Transactional
    public User updateProfileByEmail(String email, ProfileDto profileDto) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("認證錯誤，找不到用戶。"));

        // 只更新非空或非空白的欄位
        if (profileDto.getUsername() != null && !profileDto.getUsername().isBlank()) {
            user.setUsername(profileDto.getUsername());
        }

        // 如果 ProfileDto 有其他欄位，在此處添加更新邏輯

        return userRepo.save(user);
    }

    /**
     * 停用用戶帳號（軟刪除 Soft Delete）。
     * 將帳號的 isActive 狀態設為 false，保留所有歷史數據。
     * @param userId 欲停用的用戶 ID
     */
    @Transactional
    public void deactivateUser(Long userId) {
        // 1. 根據 ID 找到 User
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User ID: " + userId + " 不存在，無法停用。"));

        // 2. 檢查帳號是否已經停用，避免重複操作
        if (!user.getIsActive()) {
            // 如果已經停用，可以選擇靜默返回或拋出異常，這裡選擇拋出異常提醒
            throw new RuntimeException("帳號已經是停用狀態。");
        }

        // 3. 執行停用操作 (將 isActive 設為 false)
        user.setIsActive(false);

        // 4. 儲存變更。在 @Transactional 註解下，方法結束時會自動提交變更。
        userRepo.save(user);

        // ⚠️ 備註：如果是管理員執行此操作，可能需要記錄操作日誌 (Auditing)
    }
    /**
     * 🎯 管理員專用：獲取所有會員清單
     * 將所有 User 實體轉換為 UserResponse DTO，隱藏敏感資訊
     */
    public List<UserResponse> getAllUsers() {
        return userRepo.findAll().stream().map(user -> {
            UserResponse dto = new UserResponse();
            dto.setUserId(Math.toIntExact(user.getUserId()));
            // ❌ 這裡原本寫了兩次 setUsername
            // dto.setUsername(user.getUsername()); // 這會拿到 email
            // dto.setUsername(user.getRealName()); // 這會覆蓋掉上面，雖然結果是對的但邏輯混亂

            // ✅ 建議改成這樣，清爽又精確：
            dto.setUsername(user.getRealName());
            dto.setEmail(user.getEmail());
            dto.setCreatedAt(user.getCreatedAt());
            dto.setRole(String.valueOf(user.getRole()));
            dto.setIsActive(user.getIsActive());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 🎯 管理員專用：切換用戶狀態 (啟用/停權)
     * 比起單純的 deactivate，toggle 更加靈活，適合管理後台切換開關
     */
    @Transactional
    public void toggleUserActive(Integer userId) {
        User user = userRepo.findById(Long.valueOf(userId))
                .orElseThrow(() -> new RuntimeException("用戶 ID " + userId + " 不存在"));

        // 直接反轉狀態
        user.setIsActive(!user.getIsActive());
        userRepo.save(user);
    }

    /**
     * 🎯 管理員專用：修改用戶角色
     * @param userId 目標用戶 ID
     * @param newRoleName 字串格式的角色名稱 (如 "ADMIN", "USER")
     */
    @Transactional
    public void updateUserRole(Integer userId, String newRoleName) {
        User user = userRepo.findById(Long.valueOf(userId))
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        try {
            // 將字串轉回 Enum (Role)
            Role role = Role.valueOf(newRoleName.toUpperCase());
            user.setRole(role);
            userRepo.save(user);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("無效的角色名稱: " + newRoleName);
        }
    }
    // 在 UserService.java 中加入
    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 1. 驗證舊密碼是否正確 (注意：密碼是加密存儲的，必須用 encoder.matches)
        if (!encoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("舊密碼輸入錯誤，請重新確認。");
        }

        // 2. 檢查新密碼是否與舊密碼相同 (選配，但對安全性有幫助)
        if (oldPassword.equals(newPassword)) {
            throw new RuntimeException("新密碼不能與舊密碼相同。");
        }

        // 3. 加密並更新新密碼
        user.setPassword(encoder.encode(newPassword));
        userRepo.save(user);
    }
}
