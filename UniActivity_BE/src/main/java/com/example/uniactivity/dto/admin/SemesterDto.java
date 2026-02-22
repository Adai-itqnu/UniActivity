package com.example.uniactivity.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SemesterDto {
    private Long id;
    
    @NotBlank(message = "Tên học kỳ không được để trống")
    private String name;
    
    private String startDate;
    private String endDate;
    private Boolean isCurrent;
}
