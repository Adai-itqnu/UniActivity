package com.example.uniactivity.dto.activity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityResponseDto {
    private Long id;
    private String name;
    private String description;
    private String bannerUrl;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private String scope;
    private String status;
    private LocalDateTime createdAt;
    
    // Nested info
    private Long semesterId;
    private String semesterName;
    
    // Stats for list view
    private Integer maxSlots;
    private Integer registeredCount;
    private Integer checkedInCount;
    private Boolean isDeadlinePassed;
    private Boolean isEnded; // true nếu endTime < now
}
