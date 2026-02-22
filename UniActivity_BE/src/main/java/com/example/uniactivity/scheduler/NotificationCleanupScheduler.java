package com.example.uniactivity.scheduler;

import com.example.uniactivity.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler để dọn dẹp thông báo cũ (hơn 30 ngày)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupScheduler {
    
    private final NotificationRepository notificationRepository;
    
    /**
     * Chạy lúc 3:00 AM mỗi ngày để xóa thông báo cũ hơn 30 ngày
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        
        log.info("Starting notification cleanup for records older than {}", cutoffDate);
        
        int deletedCount = notificationRepository.deleteByCreatedAtBefore(cutoffDate);
        
        log.info("Deleted {} old notifications", deletedCount);
    }
    
    /**
     * Chạy thủ công (có thể gọi từ admin endpoint nếu cần)
     */
    @Transactional
    public int manualCleanup(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        int deletedCount = notificationRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Manual cleanup: deleted {} notifications older than {} days", deletedCount, daysOld);
        return deletedCount;
    }
}
