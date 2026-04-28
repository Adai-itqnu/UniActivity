package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
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
 * Filter chạy trước mọi request, kiểm tra JWT token trong header Authorization.
 * Nếu token hợp lệ → set Authentication vào SecurityContext.
 * Nếu không có token hoặc token không hợp lệ → bỏ qua (cho Session-based auth xử lý).
 * 
 * Đây là cơ chế "JWT-first, fallback-to-session" giúp backward-compatible
 * trong giai đoạn chuyển đổi.
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

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // Không cho phép refresh token để truy cập API
                if (jwtTokenProvider.isRefreshToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
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

    /**
     * Trích xuất JWT từ header: Authorization: Bearer <token>
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
