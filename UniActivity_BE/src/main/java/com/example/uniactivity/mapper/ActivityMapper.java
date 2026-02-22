package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.activity.*;
import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.ActivityScope;
import com.example.uniactivity.enums.ActivityStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ActivityMapper {

    public Activity toEntity(ActivityDto dto) {
        Activity entity = new Activity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setBannerUrl(dto.getBannerUrl());
        entity.setLocation(dto.getLocation());
        
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            entity.setStartTime(LocalDateTime.parse(dto.getStartTime()));
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            entity.setEndTime(LocalDateTime.parse(dto.getEndTime()));
        }
        if (dto.getRegistrationDeadline() != null && !dto.getRegistrationDeadline().isEmpty()) {
            entity.setRegistrationDeadline(LocalDateTime.parse(dto.getRegistrationDeadline()));
        }
        if (dto.getScope() != null && !dto.getScope().isEmpty()) {
            entity.setScope(ActivityScope.valueOf(dto.getScope()));
        }
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(ActivityStatus.valueOf(dto.getStatus()));
        }
        return entity;
    }
    
    public void updateEntity(ActivityDto dto, Activity entity) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setBannerUrl(dto.getBannerUrl());
        entity.setLocation(dto.getLocation());
        
        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            entity.setStartTime(LocalDateTime.parse(dto.getStartTime()));
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            entity.setEndTime(LocalDateTime.parse(dto.getEndTime()));
        }
        if (dto.getRegistrationDeadline() != null && !dto.getRegistrationDeadline().isEmpty()) {
            entity.setRegistrationDeadline(LocalDateTime.parse(dto.getRegistrationDeadline()));
        }
        if (dto.getScope() != null && !dto.getScope().isEmpty()) {
            entity.setScope(ActivityScope.valueOf(dto.getScope()));
        }
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            entity.setStatus(ActivityStatus.valueOf(dto.getStatus()));
        }
    }

    public ActivityResponseDto toResponseDto(Activity entity) {
        ActivityResponseDto dto = new ActivityResponseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setBannerUrl(entity.getBannerUrl());
        dto.setLocation(entity.getLocation());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setRegistrationDeadline(entity.getRegistrationDeadline());
        dto.setScope(entity.getScope() != null ? entity.getScope().name() : null);
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setCreatedAt(entity.getCreatedAt());
        
        if (entity.getSemester() != null) {
            dto.setSemesterId(entity.getSemester().getId());
            dto.setSemesterName(entity.getSemester().getName());
        }
        return dto;
    }
    
    // ActivitySlot
    public ActivitySlot toSlotEntity(ActivitySlotDto dto) {
        ActivitySlot entity = new ActivitySlot();
        entity.setMaxQuantity(dto.getMaxQuantity());
        entity.setCurrentQuantity(0);
        return entity;
    }
    
    public ActivitySlotResponseDto toSlotResponseDto(ActivitySlot entity) {
        ActivitySlotResponseDto dto = new ActivitySlotResponseDto();
        dto.setId(entity.getId());
        dto.setMaxQuantity(entity.getMaxQuantity());
        dto.setCurrentQuantity(entity.getCurrentQuantity());
        
        if (entity.getFaculty() != null) {
            dto.setFacultyId(entity.getFaculty().getId());
            dto.setFacultyName(entity.getFaculty().getName());
        }
        if (entity.getAcademicYear() != null) {
            dto.setAcademicYearId(entity.getAcademicYear().getId());
            dto.setAcademicYearCode(entity.getAcademicYear().getCode());
        }
        if (entity.getStudentClass() != null) {
            dto.setClassId(entity.getStudentClass().getId());
            dto.setClassName(entity.getStudentClass().getName());
        }
        return dto;
    }
    
    // ScoreOption
    public ScoreOption toScoreOptionEntity(ScoreOptionDto dto) {
        ScoreOption entity = new ScoreOption();
        entity.setName(dto.getName());
        entity.setScoreCategory(dto.getScoreCategory());
        entity.setScoreValue(dto.getScoreValue());
        entity.setDescription(dto.getDescription());
        return entity;
    }
    
    public ScoreOptionResponseDto toScoreOptionResponseDto(ScoreOption entity) {
        ScoreOptionResponseDto dto = new ScoreOptionResponseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setScoreCategory(entity.getScoreCategory());
        dto.setScoreValue(entity.getScoreValue());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
