package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.admin.AcademicYearDto;
import com.example.uniactivity.dto.admin.AcademicYearResponseDto;
import com.example.uniactivity.entity.AcademicYear;
import com.example.uniactivity.enums.CommonStatus;
import org.springframework.stereotype.Component;

@Component
public class AcademicYearMapper {

    public AcademicYear toEntity(AcademicYearDto dto) {
        AcademicYear entity = new AcademicYear();
        entity.setCode(dto.getCode());
        entity.setStartYear(dto.getStartYear());
        entity.setEndYear(dto.getEndYear());
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(CommonStatus.valueOf(dto.getStatus()));
        }
        return entity;
    }
    
    public void updateEntity(AcademicYearDto dto, AcademicYear entity) {
        entity.setCode(dto.getCode());
        entity.setStartYear(dto.getStartYear());
        entity.setEndYear(dto.getEndYear());
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(CommonStatus.valueOf(dto.getStatus()));
        }
    }

    public AcademicYearResponseDto toResponseDto(AcademicYear entity) {
        AcademicYearResponseDto dto = new AcademicYearResponseDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setStartYear(entity.getStartYear());
        dto.setEndYear(entity.getEndYear());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        return dto;
    }
}
