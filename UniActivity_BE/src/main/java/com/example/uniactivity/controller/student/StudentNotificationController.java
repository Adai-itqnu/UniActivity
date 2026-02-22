package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.Notification;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller xử lý thông báo cho Student
 * Phase 3: Notification UI
 */
@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentNotificationController {
    
    private final NotificationService notificationService;
    
    /**
     * Trang danh sách thông báo
     */
    @GetMapping("/notifications")
    public String notificationsPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("unreadCount", notificationService.getUnreadCount(userDetails.getUser().getId()));
        return "student/notifications";
    }
    
    /**
     * API lấy danh sách thông báo (có phân trang cho infinite scroll)
     */
    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<Notification> notifications = notificationService.getNotifications(userDetails.getUser().getId(), page, size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("notifications", notifications.getContent());
        response.put("currentPage", notifications.getNumber());
        response.put("totalPages", notifications.getTotalPages());
        response.put("totalElements", notifications.getTotalElements());
        response.put("hasMore", notifications.hasNext());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * API lấy số thông báo chưa đọc
     */
    @GetMapping("/api/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = notificationService.getUnreadCount(userDetails.getUser().getId());
        return ResponseEntity.ok(Map.of("count", count));
    }
    
    /**
     * API đánh dấu một thông báo là đã đọc
     */
    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        
        boolean success = notificationService.markAsRead(id, userDetails.getUser().getId());
        long newCount = notificationService.getUnreadCount(userDetails.getUser().getId());
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "unreadCount", newCount
        ));
    }
    
    /**
     * API đánh dấu tất cả thông báo là đã đọc
     */
    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        int count = notificationService.markAllAsRead(userDetails.getUser().getId());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "markedCount", count
        ));
    }
}
