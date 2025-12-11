package com.example.uniactivity.dto.activity;

import lombok.Data;

@Data
public class ActivitySlotResponseDto {
    private Long id;
    private Integer maxQuantity;
    private Integer currentQuantity;
    
    // Flattened related entities
    private Long facultyId;
    private String facultyName;
    
    private Long academicYearId;
    private String academicYearCode;
    
    private Long classId;
    private String className;
}
