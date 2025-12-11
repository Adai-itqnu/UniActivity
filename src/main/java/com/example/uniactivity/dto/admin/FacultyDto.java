package com.example.uniactivity.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FacultyDto {
    private Long id;
    
    @NotBlank(message = "Mã khoa không được để trống")
    private String code;
    
    @NotBlank(message = "Tên khoa không được để trống")
    private String name;
    
    private String description;
    private String status;
}
