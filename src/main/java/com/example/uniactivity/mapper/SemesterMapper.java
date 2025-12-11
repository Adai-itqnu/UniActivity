package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.admin.SemesterDto;
import com.example.uniactivity.dto.admin.SemesterResponseDto;
import com.example.uniactivity.entity.Semester;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class SemesterMapper {

    public Semester toEntity(SemesterDto dto) {
        Semester entity = new Semester();
        entity.setName(dto.getName());
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            entity.setStartDate(LocalDate.parse(dto.getStartDate()));
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            entity.setEndDate(LocalDate.parse(dto.getEndDate()));
        }
        entity.setIsCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent() : false);
        return entity;
    }
    
    public void updateEntity(SemesterDto dto, Semester entity) {
        entity.setName(dto.getName());
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            entity.setStartDate(LocalDate.parse(dto.getStartDate()));
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            entity.setEndDate(LocalDate.parse(dto.getEndDate()));
        }
        entity.setIsCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent() : false);
    }

    public SemesterResponseDto toResponseDto(Semester entity) {
        SemesterResponseDto dto = new SemesterResponseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setIsCurrent(entity.getIsCurrent());
        return dto;
    }
}
