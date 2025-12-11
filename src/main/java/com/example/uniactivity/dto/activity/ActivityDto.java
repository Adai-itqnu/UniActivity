package com.example.uniactivity.dto.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityDto {
    private Long id;
    
    @NotBlank(message = "Tên hoạt động không được để trống")
    private String name;
    
    private String description;
    private String bannerUrl;
    private String location;
    
    private String startTime;
    private String endTime;
    private String registrationDeadline;
    
    @NotNull(message = "Phạm vi không được để trống")
    private String scope; // SCHOOL or FACULTY
    
    private String status; // DRAFT, OPEN, FINISHED, CANCELLED
    
    private Long semesterId;
}
