package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.admin.FacultyDto;
import com.example.uniactivity.dto.admin.FacultyResponseDto;
import com.example.uniactivity.entity.Faculty;
import com.example.uniactivity.enums.CommonStatus;
import org.springframework.stereotype.Component;

@Component
public class FacultyMapper {

    public Faculty toEntity(FacultyDto dto) {
        Faculty entity = new Faculty();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(CommonStatus.valueOf(dto.getStatus()));
        }
        return entity;
    }
    
    public void updateEntity(FacultyDto dto, Faculty entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(CommonStatus.valueOf(dto.getStatus()));
        }
    }

    public FacultyResponseDto toResponseDto(Faculty entity) {
        FacultyResponseDto dto = new FacultyResponseDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        return dto;
    }
}
