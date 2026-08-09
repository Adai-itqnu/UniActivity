package com.example.uniactivity.controller;

import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.security.JwtTokenProvider;
import com.example.uniactivity.service.SseEmitterService;
import com.example.uniactivity.service.SseTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Cấp purpose ticket ngắn hạn và mở kết nối SSE bằng ticket đó.
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
@Slf4j
public class SseController {
    
    private final SseEmitterService sseEmitterService;
    private final SseTicketService sseTicketService;

    @PostMapping("/ticket")
    public Map<String, Object> issueTicket(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return Map.of(
                "ticket", sseTicketService.issue(userDetails.getUser()),
                "expiresIn", JwtTokenProvider.SSE_TICKET_EXPIRATION_SECONDS
        );
    }
    
    /**
     * Subscribe vào SSE stream để nhận real-time events
     * Client dùng EventSource('/sse/subscribe?ticket=...') để kết nối.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("ticket") String ticket) {
        long userId = sseTicketService.resolveUserId(ticket)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SSE ticket"));
        log.info("User ID {} subscribing to SSE", userId);
        return sseEmitterService.subscribe(userId);
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
