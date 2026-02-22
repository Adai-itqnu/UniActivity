package com.example.uniactivity.dto.admin;

import lombok.Data;

@Data
public class AcademicYearResponseDto {
    private Long id;
    private String code;
    private Integer startYear;
    private Integer endYear;
    private String status;
}
