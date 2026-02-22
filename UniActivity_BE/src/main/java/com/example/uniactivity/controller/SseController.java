package com.example.uniactivity.controller;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller xử lý SSE subscriptions
 * Endpoint duy nhất: GET /sse/subscribe
 * Phase 2: Basic subscription endpoint
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
@Slf4j
public class SseController {
    
    private final SseEmitterService sseEmitterService;
    
    /**
     * Subscribe vào SSE stream để nhận real-time events
     * Client sẽ dùng EventSource('/sse/subscribe') để kết nối
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("Attempted SSE subscription without authentication");
            throw new IllegalStateException("User must be authenticated");
        }
        
        User user = userDetails.getUser();
        log.info("User {} (ID: {}) subscribing to SSE", user.getUsername(), user.getId());
        return sseEmitterService.subscribe(user.getId());
    }
    
    /**
     * Health check endpoint cho SSE service
     */
    @GetMapping("/status")
    public String getStatus() {
        return String.format("SSE Service Active - Users: %d, Connections: %d",
            sseEmitterService.getActiveUserCount(),
            sseEmitterService.getActiveConnectionCount());
    }
}
