package com.example.uniactivity.dto.admin;

import lombok.Data;

@Data
public class StudentClassResponseDto {
    private Long id;
    private String code;
    private String name;
    private String joinCode;
    private String qrCodeUrl;
    
    // Flattened faculty info
    private Long facultyId;
    private String facultyName;
    
    // Flattened academic year info
    private Long academicYearId;
    private String academicYearCode;
}
