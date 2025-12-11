package com.example.uniactivity.dto.admin;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SemesterResponseDto {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrent;
}
