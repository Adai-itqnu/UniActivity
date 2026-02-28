package com.example.uniactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý khi đăng nhập OAuth2 (Google) thất bại.
 * Redirect về trang login của React frontend kèm thông báo lỗi,
 * thay vì hiển thị trang Thymeleaf mặc định của backend.
 */
@Component
public class CustomOAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorMessage = exception.getMessage() != null
                ? exception.getMessage()
                : "Đăng nhập Google thất bại";

        String encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        // Redirect về React frontend login với thông tin lỗi
        response.sendRedirect(frontendUrl + "/login?error=google&message=" + encodedMessage);
    }
}
