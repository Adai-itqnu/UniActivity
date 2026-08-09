package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.service.OAuthExchangeCodeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Completes Google OAuth login without placing reusable credentials in the URL.
 * Local username/password login is handled by {@code JwtAuthController}.
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthExchangeCodeService exchangeCodeService;
    private final String frontendUrl;

    public CustomAuthenticationSuccessHandler(
            OAuthExchangeCodeService exchangeCodeService,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.exchangeCodeService = exchangeCodeService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        String exchangeCode = exchangeCodeService.issue(user);
        String redirectUrl = frontendUrl + "/login?code="
                + URLEncoder.encode(exchangeCode, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
