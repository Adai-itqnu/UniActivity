package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.admin.StudentClassDto;
import com.example.uniactivity.dto.admin.StudentClassResponseDto;
import com.example.uniactivity.entity.StudentClass;
import org.springframework.stereotype.Component;

@Component
public class StudentClassMapper {

    public StudentClass toEntity(StudentClassDto dto) {
        StudentClass entity = new StudentClass();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        return entity;
    }
    
    public void updateEntity(StudentClassDto dto, StudentClass entity) {
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
    }

    public StudentClassResponseDto toResponseDto(StudentClass entity) {
        StudentClassResponseDto dto = new StudentClassResponseDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setJoinCode(entity.getJoinCode());
        dto.setQrCodeUrl(entity.getQrCodeUrl());
        
        if (entity.getFaculty() != null) {
            dto.setFacultyId(entity.getFaculty().getId());
            dto.setFacultyName(entity.getFaculty().getName());
        }
        if (entity.getAcademicYear() != null) {
            dto.setAcademicYearId(entity.getAcademicYear().getId());
            dto.setAcademicYearCode(entity.getAcademicYear().getCode());
        }
        return dto;
    }
}
