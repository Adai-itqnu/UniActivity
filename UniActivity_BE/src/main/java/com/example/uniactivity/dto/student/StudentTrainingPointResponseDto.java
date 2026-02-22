package com.example.uniactivity.dto.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for StudentTrainingPoint response with details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentTrainingPointResponseDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentCode;
    private Long semesterId;
    private String semesterName;
    private Integer totalScore;
    private String classification;
    private String status;
    private List<TrainingPointDetailDto> details;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingPointDetailDto {
        private Long id;
        private String criteriaCode;
        private Integer score;
        private String sourceType;
        private Long referenceId;
        private String description;
        private LocalDateTime createdAt;
    }
}
