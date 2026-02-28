package com.example.uniactivity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Xử lý sau khi đăng nhập thành công (cả form login lẫn Google OAuth2).
 * Redirect thẳng về React frontend theo role.
 * Cookie session tự động chia sẻ giữa các port trên cùng localhost.
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        if (roles.contains("ROLE_ADMIN")) {
            response.sendRedirect(frontendUrl + "/admin/dashboard");
        } else if (roles.contains("ROLE_MANAGER")) {
            response.sendRedirect(frontendUrl + "/manager/dashboard");
        } else if (roles.contains("ROLE_STUDENT")) {
            response.sendRedirect(frontendUrl + "/student/home");
        } else {
            response.sendRedirect(frontendUrl + "/");
        }
    }
}

