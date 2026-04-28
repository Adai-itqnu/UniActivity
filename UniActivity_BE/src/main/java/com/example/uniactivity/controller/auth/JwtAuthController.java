package com.example.uniactivity.controller.auth;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API endpoint cho JWT authentication.
 * 
 * - POST /api/auth/login       → Đăng nhập, trả về accessToken + refreshToken
 * - POST /api/auth/refresh     → Dùng refreshToken để lấy accessToken mới
 * - POST /api/auth/logout-jwt  → Logout (client-side xóa token)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     * Response: { accessToken, refreshToken, tokenType, expiresIn, user: {...} }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập tên đăng nhập và mật khẩu."));
        }

        try {
            // Authenticate using Spring Security's AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Lấy user từ database (sau khi authenticate thành công)
            User user = userRepository.findByUsernameOrEmail(username, username)
                    .orElseThrow(() -> new RuntimeException("User not found after authentication"));

            // Generate tokens
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), user.getUsername(), user.getRole().name()
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", jwtTokenProvider.getAccessTokenExpirationMs() / 1000); // seconds

            // User info
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("fullName", user.getFullName());
            userInfo.put("email", user.getEmail());
            userInfo.put("phone", user.getPhone());
            userInfo.put("role", user.getRole().name());
            userInfo.put("avatarUrl", user.getAvatarUrl());
            userInfo.put("provider", user.getProvider());
            userInfo.put("status", user.getStatus().name());
            response.put("user", userInfo);

            log.info("JWT login success: {} (role: {})", user.getUsername(), user.getRole());
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Tên đăng nhập hoặc mật khẩu không đúng."));
        } catch (AuthenticationException ex) {
            log.error("Authentication failed for user {}: {}", username, ex.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Đăng nhập thất bại: " + ex.getMessage()));
        }
    }

    /**
     * POST /api/auth/refresh
     * Body: { "refreshToken": "..." }
     * Response: { accessToken, tokenType, expiresIn }
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required."));
        }

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token không hợp lệ hoặc đã hết hạn."));
        }

        // Kiểm tra đúng loại refresh token
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Token không phải refresh token."));
        }

        // Lấy user
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Người dùng không tồn tại."));
        }

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtTokenProvider.getAccessTokenExpirationMs() / 1000);

        log.debug("Token refreshed for user: {}", user.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/logout-jwt
     * JWT logout là client-side (xóa token khỏi localStorage).
     * Endpoint này chỉ để frontend có thể gọi cho consistency.
     */
    @PostMapping("/logout-jwt")
    public ResponseEntity<?> logoutJwt() {
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công."));
    }
}
