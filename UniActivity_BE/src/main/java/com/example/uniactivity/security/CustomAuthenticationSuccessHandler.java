package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý sau khi đăng nhập thành công (cả form login lẫn Google OAuth2).
 * 
 * Với JWT flow:
 * - Form login: KHÔNG đi qua handler này (dùng JwtAuthController trả JSON)
 * - Google OAuth2: Redirect về React frontend kèm JWT tokens trong URL params
 *   React frontend sẽ parse tokens từ URL, lưu vào localStorage, rồi navigate.
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // Lấy user từ authentication principal
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Encode user info để gửi qua URL
        String userJson = URLEncoder.encode(
                String.format("{\"id\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"avatarUrl\":\"%s\",\"provider\":\"%s\",\"status\":\"%s\"}",
                        user.getId(),
                        escapeJson(user.getUsername()),
                        escapeJson(user.getFullName()),
                        escapeJson(user.getEmail()),
                        user.getRole().name(),
                        user.getAvatarUrl() != null ? escapeJson(user.getAvatarUrl()) : "",
                        user.getProvider(),
                        user.getStatus().name()
                ),
                StandardCharsets.UTF_8
        );

        // Redirect về frontend với tokens
        // React sẽ parse tokens từ URL params tại trang /login
        String redirectUrl = String.format("%s/login?token=%s&refreshToken=%s&user=%s",
                frontendUrl, accessToken, refreshToken, userJson);

        response.sendRedirect(redirectUrl);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r");
    }
}
