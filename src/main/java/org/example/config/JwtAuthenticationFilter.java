package org.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.service.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 驗證過濾器：在每個請求進入之前檢查 JWT 憑證
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // 🚨 新增：定義公開路徑白名單 (必須是完整的 API 路徑前綴)
    private static final String AUTH_PATH_PREFIX = "/api/auth";

    // 🚨 新增：覆寫 shouldNotFilter 檢查方法，用於排除公開路徑
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. 放行所有 OPTIONS 預檢請求 (CORS 必備)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 2. 放行所有公開 API 路徑 (包含 /api/auth 和 /api/public)
        // 這樣讀取書籍列表就不會進入 JWT 檢查邏輯
        return path.startsWith("/api/auth") || path.startsWith("/api/public");
    }

    // org.example.config.JwtAuthenticationFilter.java

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String userEmail = null;

        // 1. 檢查 Header 是否存在
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return; // 🎯 短路返回：如果沒有 Token，直接放行給 Spring Security 處理 (通常會被放行或被拒絕)
        }

        jwt = authHeader.substring(7);

        // 2. 提取用戶 Email (可能因 Token 無效而拋出異常)
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // 🚨 Token 無效或無法提取，打印除錯資訊後，Token 無效，但我們繼續讓請求進入 Filter Chain
            System.out.println("DEBUG JWT EXCEPTION: Token extraction failed: " + e.getMessage());

            // 這裡不再調用 filterChain.doFilter(request, response);
            // 讓程式碼繼續執行到方法末尾，或讓 Spring Security 處理未認證的請求
        }

        // 3. 檢查用戶 Email 是否有效，且當前 Spring Security 上下文中尚未認證
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = null;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            } catch (UsernameNotFoundException e) {
                System.out.println("DEBUG JWT: User not found: " + userEmail);
                // 如果用戶在數據庫中不存在，則不會設置 context
            }

            // 4. 驗證 Token 是否有效
            if (userDetails != null && jwtService.isTokenValid(jwt, userDetails)) {
                // 設置認證通過的 Token
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                System.out.println("DEBUG: User authenticated and setting context for: " + userDetails.getUsername());

                SecurityContextHolder.getContext().setAuthentication(authToken);
                // JwtAuthenticationFilter.java (在設置完 SecurityContextHolder 後)

// ...
                System.out.println("DEBUG: User authenticated and setting context for: " + userDetails.getUsername());

                SecurityContextHolder.getContext().setAuthentication(authToken);

                // =========================================================
                // 🎯 最終偵錯程式碼：打印設置到 Context 中的實際權限
                System.out.println("=================================================");
                System.out.println("!!! FINAL CHECK Authorities in Context: " +
                        SecurityContextHolder.getContext().getAuthentication().getAuthorities());
                System.out.println("=================================================");
                // =========================================================
            }  else if (userDetails != null) {
                boolean isExpired = jwtService.isTokenExpired(jwt); // 假設您在 JwtService 中公開了這個方法
                System.out.println("DEBUG: Token validation failed for user: " + userDetails.getUsername() +
                        ". Expired: " + isExpired +
                        ". Username match: " + userDetails.getUsername().equals(jwtService.extractUsername(jwt)));
            }
        }

        // 5. 無論如何，最後都必須將請求傳遞給鏈中的下一個 Filter
        filterChain.doFilter(request, response);
    }
}
