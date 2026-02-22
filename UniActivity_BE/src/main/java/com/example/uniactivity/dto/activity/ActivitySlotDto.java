package com.example.uniactivity.dto.activity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivitySlotDto {
    private Long id;
    
    private Long facultyId;
    private Long academicYearId;
    private Long classId;
    
    @NotNull(message = "Số lượng tối đa không được để trống")
    @Min(value = 1, message = "Số lượng tối đa phải >= 1")
    private Integer maxQuantity;
}
