package com.example.uniactivity.enums;

/**
 * Loại thông báo trong hệ thống
 * Phân chia theo target role: STUDENT hoặc MANAGER
 */
public enum NotificationType {
    
    // ===== Thông báo cho STUDENT =====
    
    /** Yêu cầu tham gia lớp được duyệt */
    JOIN_REQUEST_APPROVED,
    
    /** Yêu cầu tham gia lớp bị từ chối */
    JOIN_REQUEST_REJECTED,
    
    /** Yêu cầu điểm rèn luyện được duyệt */
    POINT_REQUEST_APPROVED,
    
    /** Yêu cầu điểm rèn luyện bị từ chối */
    POINT_REQUEST_REJECTED,
    
    /** Minh chứng hoạt động được duyệt */
    EVIDENCE_APPROVED,
    
    /** Minh chứng hoạt động bị từ chối */
    EVIDENCE_REJECTED,
    
    /** Bị xóa khỏi lớp */
    REMOVED_FROM_CLASS,
    
    /** Có hoạt động mới dành cho lớp */
    NEW_ACTIVITY,
    
    // ===== Thông báo cho MANAGER =====
    
    /** Có yêu cầu tham gia lớp mới */
    JOIN_REQUEST_SUBMITTED,
    
    /** Có yêu cầu điểm rèn luyện mới */
    POINT_REQUEST_SUBMITTED,
    
    /** Có minh chứng mới cần duyệt */
    EVIDENCE_SUBMITTED,
    
    /** Sinh viên đã check-in hoạt động */
    STUDENT_CHECKED_IN,
    
    // ===== Thông báo chung =====
    
    /** Dashboard cần cập nhật (cho real-time stats) */
    DASHBOARD_UPDATED
}
