package com.example.uniactivity.entity;

import com.example.uniactivity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ thông báo cho user
 * Áp dụng cho Student và Manager, không áp dụng cho Admin
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "userId"),
    @Index(name = "idx_notification_created_at", columnList = "createdAt"),
    @Index(name = "idx_notification_user_read", columnList = "userId, isRead")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** ID của user nhận thông báo */
    @Column(nullable = false)
    private Long userId;
    
    /** Loại thông báo */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;
    
    /** Tiêu đề thông báo */
    @Column(nullable = false, length = 200)
    private String title;
    
    /** Nội dung chi tiết */
    @Column(columnDefinition = "TEXT")
    private String message;
    
    /** Link điều hướng khi click vào thông báo */
    @Column(length = 500)
    private String link;
    
    /** Trạng thái đã đọc */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;
    
    /** Thời gian tạo */
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) {
            isRead = false;
        }
    }
}
