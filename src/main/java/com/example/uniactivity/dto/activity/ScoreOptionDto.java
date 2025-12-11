package com.example.uniactivity.dto.activity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScoreOptionDto {
    private Long id;
    
    @NotBlank(message = "Tên không được để trống")
    private String name;
    
    @NotBlank(message = "Mục điểm không được để trống")
    private String scoreCategory;
    
    @NotNull(message = "Số điểm không được để trống")
    @Min(value = 1, message = "Số điểm phải >= 1")
    private Integer scoreValue;
    
    private String description;
}
