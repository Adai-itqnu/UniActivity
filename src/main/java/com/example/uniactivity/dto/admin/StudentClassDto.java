package com.example.uniactivity.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudentClassDto {
    private Long id;
    
    @NotBlank(message = "Mã lớp không được để trống")
    private String code;
    
    @NotBlank(message = "Tên lớp không được để trống")
    private String name;
    
    private Long facultyId;
    private Long academicYearId;
}
