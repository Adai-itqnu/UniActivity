package com.example.uniactivity.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for ActivityRegistration response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRegistrationResponseDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentCode;
    private Long activityId;
    private String activityName;
    private Long activitySlotId;
    private String activitySlotInfo;
    private Long scoreOptionId;
    private String scoreOptionName;
    private String scoreCategory;
    private Integer scoreValue;
    private LocalDateTime registeredAt;
    private String status;
    private Boolean attendanceConfirmed;
    private LocalDateTime confirmedAt;
    private String notes;
    private String evidenceUrl;
    private Boolean isApproved;
    private String rejectionReason;
}
