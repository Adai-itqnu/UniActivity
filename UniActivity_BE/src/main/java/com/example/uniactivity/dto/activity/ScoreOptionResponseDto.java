package com.example.uniactivity.dto.activity;

import lombok.Data;

@Data
public class ScoreOptionResponseDto {
    private Long id;
    private String name;
    private String scoreCategory;
    private Integer scoreValue;
    private String description;
}
