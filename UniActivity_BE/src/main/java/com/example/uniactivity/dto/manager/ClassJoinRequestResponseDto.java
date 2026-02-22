package com.example.uniactivity.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for ClassJoinRequest response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassJoinRequestResponseDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userStudentCode;
    private Long classId;
    private String className;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private Long processedById;
    private String processedByName;
}
