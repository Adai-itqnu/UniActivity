package com.example.uniactivity.service;

import com.example.uniactivity.entity.Notification;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.NotificationType;
import com.example.uniactivity.repository.NotificationRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service xử lý thông báo cho Student và Manager
 * Phase 1: Basic CRUD operations
 * Phase 4: Integration với SseEmitterService - gửi real-time
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;
    
    /**
     * Tạo thông báo mới cho một user và gửi qua SSE
     */
    @Transactional
    public Notification create(Long userId, NotificationType type, String title, String message, String link) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .isRead(false)
                .build();
        
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification [{}] for user {}: {}", type, userId, title);
        
        // Gửi real-time qua SSE
        sendViaSSE(saved);
        
        return saved;
    }
    
    /**
     * Tạo thông báo cho nhiều users (batch) và gửi qua SSE
     */
    @Transactional
    public void createForUsers(Set<Long> userIds, NotificationType type, String title, String message, String link) {
        for (Long userId : userIds) {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .link(link)
                    .isRead(false)
                    .build();
            
            Notification saved = notificationRepository.save(notification);
            sendViaSSE(saved);
        }
        log.info("Created notification [{}] for {} users: {}", type, userIds.size(), title);
    }
    
    /**
     * Gửi notification qua SSE
     */
    private void sendViaSSE(Notification notification) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", notification.getId());
            payload.put("type", notification.getType().name());
            payload.put("title", notification.getTitle());
            payload.put("message", notification.getMessage());
            payload.put("link", notification.getLink());
            payload.put("createdAt", notification.getCreatedAt().toString());
            payload.put("read", notification.getIsRead());
            
            sseEmitterService.sendToUser(notification.getUserId(), "notification", payload);
        } catch (Exception e) {
            log.error("Failed to send SSE notification to user {}: {}", notification.getUserId(), e.getMessage());
        }
    }
    
    // ==================== Helper methods cho các use cases cụ thể ====================
    
    /**
     * Thông báo: Yêu cầu tham gia lớp đã được duyệt
     */
    public void notifyJoinRequestApproved(User student, String className) {
        create(student.getId(), 
               NotificationType.JOIN_REQUEST_APPROVED,
               "Yêu cầu tham gia đã được duyệt",
               "Bạn đã được thêm vào lớp " + className,
               "/student/my-class");
    }
    
    /**
     * Thông báo: Yêu cầu tham gia lớp bị từ chối
     */
    public void notifyJoinRequestRejected(User student, String className) {
        create(student.getId(),
               NotificationType.JOIN_REQUEST_REJECTED,
               "Yêu cầu tham gia bị từ chối",
               "Yêu cầu tham gia lớp " + className + " đã bị từ chối",
               "/student/my-class");
    }
    
    /**
     * Thông báo: Bị xóa khỏi lớp
     */
    public void notifyRemovedFromClass(User student, String className) {
        create(student.getId(),
               NotificationType.REMOVED_FROM_CLASS,
               "Bạn đã bị xóa khỏi lớp",
               "Bạn đã bị xóa khỏi lớp " + className,
               "/student/home");
    }
    
    /**
     * Thông báo cho Manager: Có yêu cầu tham gia lớp mới
     */
    public void notifyNewJoinRequest(User manager, String studentName) {
        create(manager.getId(),
               NotificationType.JOIN_REQUEST_SUBMITTED,
               "Yêu cầu tham gia lớp mới",
               studentName + " đã gửi yêu cầu tham gia lớp",
               "/manager/join-requests");
    }
    
    /**
     * Thông báo: Yêu cầu điểm đã được duyệt
     */
    public void notifyPointRequestApproved(User student, String criteriaName, int score) {
        create(student.getId(),
               NotificationType.POINT_REQUEST_APPROVED,
               "Yêu cầu điểm được duyệt",
               "Yêu cầu điểm '" + criteriaName + "' +" + score + " điểm đã được chấp nhận",
               "/student/my-scores");
    }
    
    /**
     * Thông báo: Yêu cầu điểm bị từ chối
     */
    public void notifyPointRequestRejected(User student, String criteriaName, String reason) {
        String message = "Yêu cầu điểm '" + criteriaName + "' đã bị từ chối";
        if (reason != null && !reason.isEmpty()) {
            message += ". Lý do: " + reason;
        }
        create(student.getId(),
               NotificationType.POINT_REQUEST_REJECTED,
               "Yêu cầu điểm bị từ chối",
               message,
               "/student/my-scores");
    }
    
    /**
     * Thông báo cho Manager: Có yêu cầu điểm mới
     */
    public void notifyNewPointRequest(User manager, String studentName, String criteriaName) {
        create(manager.getId(),
               NotificationType.POINT_REQUEST_SUBMITTED,
               "Yêu cầu điểm mới",
               studentName + " đã gửi yêu cầu cộng điểm '" + criteriaName + "'",
               "/manager/point-requests");
    }
    
    /**
     * Thông báo cho Manager: Sinh viên đã check-in
     */
    public void notifyStudentCheckedIn(User manager, String studentName, String activityName) {
        create(manager.getId(),
               NotificationType.STUDENT_CHECKED_IN,
               "Sinh viên check-in",
               studentName + " đã check-in hoạt động: " + activityName,
               "/manager/activities");
    }
    
    /**
     * Thông báo: Có hoạt động mới
     */
    public void notifyNewActivity(User student, String activityName) {
        create(student.getId(),
               NotificationType.NEW_ACTIVITY,
               "Hoạt động mới",
               "Hoạt động mới: " + activityName,
               "/student/activities");
    }
    
    /**
     * Lấy danh sách thông báo của user với pagination
     */
    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    /**
     * Lấy danh sách thông báo chưa đọc
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Đếm số thông báo chưa đọc
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
    
    /**
     * Đánh dấu một thông báo là đã đọc
     */
    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        return notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .map(n -> {
                    n.setIsRead(true);
                    notificationRepository.save(n);
                    log.debug("Marked notification {} as read for user {}", notificationId, userId);
                    return true;
                })
                .orElse(false);
    }
    
    /**
     * Đánh dấu tất cả thông báo của user là đã đọc
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        int count = notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user {}", count, userId);
        return count;
    }
    
    /**
     * Lấy tổng số thông báo của user
     */
    @Transactional(readOnly = true)
    public long getTotalCount(Long userId) {
        return notificationRepository.countByUserId(userId);
    }
}
