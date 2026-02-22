package com.example.uniactivity.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for PointRequest response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointRequestResponseDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentCode;
    private String semesterName;
    private String criteriaCode;
    private Integer claimedScore;
    private String description;
    private String evidenceImageUrl;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
