package com.example.uniactivity.controller.auth;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.security.JwtTokenProvider;
import com.example.uniactivity.service.OAuthExchangeCodeService;
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
 * - POST /api/auth/logout-jwt  → Thu hồi toàn bộ token hiện tại của người dùng
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final OAuthExchangeCodeService exchangeCodeService;

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
                    user.getId(), user.getUsername(), user.getRole().name(), user.getTokenVersion()
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getTokenVersion());

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
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Tên đăng nhập hoặc mật khẩu không đúng."));
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
        Long userId;
        long tokenVersion;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            tokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token claims."));
        }
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Người dùng không tồn tại."));
        }

        if (user.getStatus() != UserStatus.ACTIVE || user.getTokenVersion() != tokenVersion) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token revoked or account locked."));
        }

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getTokenVersion()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtTokenProvider.getAccessTokenExpirationMs() / 1000);

        log.debug("Token refreshed for user: {}", user.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Exchanges a short-lived, single-use Google OAuth code for normal JWT credentials.
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<?> exchangeOAuthCode(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OAuth exchange code is required."));
        }

        User user = exchangeCodeService.consume(code).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "OAuth exchange code is invalid or expired."));
        }

        return ResponseEntity.ok(buildJwtResponse(user));
    }

    private Map<String, Object> buildJwtResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getTokenVersion()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getTokenVersion());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtTokenProvider.getAccessTokenExpirationMs() / 1000);

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
        return response;
    }

    /**
     * POST /api/auth/logout-jwt
     * Thu hồi toàn bộ access/refresh token đã phát bằng cách tăng tokenVersion.
     */
    @PostMapping("/logout-jwt")
    public ResponseEntity<?> logoutJwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated."));
        }

        int updatedRows = userRepository.incrementTokenVersionById(userDetails.getUser().getId());
        if (updatedRows != 1) {
            return ResponseEntity.status(401).body(Map.of("error", "Unable to revoke token."));
        }

        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công."));
    }
}
