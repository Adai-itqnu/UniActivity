package com.example.uniactivity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Service quản lý SSE (Server-Sent Events) connections
 * Hỗ trợ multi-tab: một user có thể có nhiều SseEmitter
 * Phase 2: Basic connection management + heartbeat
 */
@Service
@Slf4j
public class SseEmitterService {
    
    /** Timeout 30 phút */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    
    /** Heartbeat interval 30 giây */
    private static final long HEARTBEAT_INTERVAL = 30 * 1000L;
    
    /** Map lưu connections: userId -> Set<SseEmitter> */
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    
    /** Scheduled executor cho heartbeat */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public SseEmitterService() {
        // Bắt đầu gửi heartbeat mỗi 30 giây
        scheduler.scheduleAtFixedRate(this::sendHeartbeatToAll, 
            HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
        log.info("SseEmitterService initialized with heartbeat interval: {}s", HEARTBEAT_INTERVAL / 1000);
    }
    
    /**
     * Subscribe user vào SSE stream
     * @param userId ID của user
     * @return SseEmitter để trả về client
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Thêm vào map
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        log.info("User {} subscribed to SSE (total connections: {})", userId, emitters.get(userId).size());
        
        // Xử lý completion (bình thường hoặc timeout)
        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
            log.debug("SSE completed for user {}", userId);
        });
        
        // Xử lý timeout
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            log.debug("SSE timeout for user {}", userId);
        });
        
        // Xử lý error
        emitter.onError((ex) -> {
            removeEmitter(userId, emitter);
            log.warn("SSE error for user {}: {}", userId, ex.getMessage());
        });
        
        // Gửi event kết nối thành công
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("SSE connection established"));
        } catch (IOException e) {
            log.error("Failed to send connected event to user {}", userId, e);
            removeEmitter(userId, emitter);
        }
        
        return emitter;
    }
    
    /**
     * Gửi event đến một user cụ thể (tất cả tabs của user đó)
     */
    public void sendToUser(Long userId, String eventType, Object data) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("No active SSE connections for user {}", userId);
            return;
        }
        
        Set<SseEmitter> deadEmitters = ConcurrentHashMap.newKeySet();
        
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
            } catch (IOException e) {
                log.warn("Failed to send event {} to user {}, marking for removal", eventType, userId);
                deadEmitters.add(emitter);
            }
        }
        
        // Xóa các emitters đã chết
        deadEmitters.forEach(emitter -> removeEmitter(userId, emitter));
        
        log.debug("Sent event [{}] to {} tabs of user {}", eventType, userEmitters.size() - deadEmitters.size(), userId);
    }
    
    /**
     * Gửi event đến nhiều users
     */
    public void sendToUsers(Set<Long> userIds, String eventType, Object data) {
        userIds.forEach(userId -> sendToUser(userId, eventType, data));
    }
    
    /**
     * Broadcast event đến tất cả users đang kết nối
     */
    public void broadcast(String eventType, Object data) {
        emitters.keySet().forEach(userId -> sendToUser(userId, eventType, data));
    }
    
    /**
     * Gửi heartbeat đến tất cả connections
     */
    private void sendHeartbeatToAll() {
        int totalConnections = emitters.values().stream().mapToInt(Set::size).sum();
        if (totalConnections == 0) {
            return;
        }
        
        log.trace("Sending heartbeat to {} active connections", totalConnections);
        emitters.keySet().forEach(userId -> 
            sendToUser(userId, "heartbeat", System.currentTimeMillis())
        );
    }
    
    /**
     * Xóa emitter khỏi map
     */
    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
                log.info("User {} has no more active SSE connections", userId);
            }
        }
    }
    
    /**
     * Đếm số connections đang active
     */
    public int getActiveConnectionCount() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }
    
    /**
     * Đếm số users đang kết nối
     */
    public int getActiveUserCount() {
        return emitters.size();
    }
    
    /**
     * Cleanup khi shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SseEmitterService...");
        scheduler.shutdown();
        emitters.values().forEach(set -> set.forEach(SseEmitter::complete));
        emitters.clear();
    }
}
