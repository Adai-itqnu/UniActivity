package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates bearer JWTs from the Authorization header.
 * API and SSE routes never fall back to a form/OAuth session principal;
 * legacy MVC routes may continue using their existing session authentication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // API endpoints are bearer-only. Never let a form/OAuth session principal
        // bypass JWT type, status, or token-version checks.
        if (isBearerOnlyApiRequest(request)) {
            SecurityContextHolder.clearContext();
        }

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // Không cho phép refresh token để truy cập API
                if (!jwtTokenProvider.isAccessToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

                User user = userRepository.findById(userId).orElse(null);
                if (user != null
                        && user.getStatus() == UserStatus.ACTIVE
                        && user.getTokenVersion() == jwtTokenProvider.getTokenVersion(jwt)) {
                    CustomUserDetails userDetails = new CustomUserDetails(user);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication from JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Trích xuất access token chỉ từ header Authorization. */
    private boolean isBearerOnlyApiRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }
        if (!StringUtils.hasText(path)) {
            return false;
        }

        return path.equals("/api")
                || path.startsWith("/api/")
                || path.endsWith("/api")
                || path.contains("/api/")
                || path.equals("/sse")
                || path.startsWith("/sse/");
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        // 1. Lấy từ header Authorization
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
