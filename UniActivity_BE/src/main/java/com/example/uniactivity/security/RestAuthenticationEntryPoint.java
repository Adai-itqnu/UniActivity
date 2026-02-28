package com.example.uniactivity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom AuthenticationEntryPoint phân biệt API requests và browser page requests:
 * - API/XHR requests → trả về 401 JSON (không redirect)
 * - Browser page requests → redirect tới /login (hành vi mặc định)
 *
 * Giải quyết vấn đề: khi React SPA gọi API qua fetch(), Spring Security redirect
 * tới /login (HTML) thay vì trả 401, khiến frontend không xử lý được.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        if (isApiRequest(request)) {
            // API request → trả 401 JSON
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 401);
            body.put("error", "Unauthorized");
            body.put("message", "Bạn chưa đăng nhập hoặc phiên đã hết hạn");
            body.put("path", request.getRequestURI());
            body.put("timestamp", LocalDateTime.now().toString());

            objectMapper.writeValue(response.getOutputStream(), body);
        } else {
            // Browser page request → redirect tới login
            response.sendRedirect("/login");
        }
    }

    /**
     * Phát hiện request là API call (fetch/XHR) hay browser page navigation
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");

        // Kiểm tra URL pattern: các endpoint API
        if (uri.contains("/api/") || uri.contains("/api?")) {
            return true;
        }

        // XHR request (từ Axios/jQuery)
        if ("XMLHttpRequest".equals(xRequestedWith)) {
            return true;
        }

        // Accept header chứa application/json (fetch với JSON)
        if (accept != null && accept.contains("application/json") && !accept.contains("text/html")) {
            return true;
        }

        return false;
    }
}
