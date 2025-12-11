package com.example.uniactivity.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcademicYearDto {
    private Long id;
    
    @NotBlank(message = "Mã khóa không được để trống")
    private String code;
    
    private Integer startYear;
    private Integer endYear;
    private String status;
}
