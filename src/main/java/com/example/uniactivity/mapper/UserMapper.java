package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.admin.UserDto;
import com.example.uniactivity.dto.admin.UserResponseDto;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(UserDto dto) {
        User entity = new User();
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setFullName(dto.getFullName());
        entity.setPhone(dto.getPhone());
        if (dto.getRole() != null && !dto.getRole().isEmpty()) {
            entity.setRole(Role.valueOf(dto.getRole()));
        }
        return entity;
    }
    
    public void updateEntity(UserDto dto, User entity) {
        entity.setEmail(dto.getEmail());
        entity.setFullName(dto.getFullName());
        entity.setPhone(dto.getPhone());
        if (dto.getRole() != null && !dto.getRole().isEmpty()) {
            entity.setRole(Role.valueOf(dto.getRole()));
        }
    }

    public UserResponseDto toResponseDto(User entity) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setFullName(entity.getFullName());
        dto.setPhone(entity.getPhone());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setRole(entity.getRole() != null ? entity.getRole().name() : null);
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setCreatedAt(entity.getCreatedAt());
        
        if (entity.getStudentClass() != null) {
            dto.setClassId(entity.getStudentClass().getId());
            dto.setClassName(entity.getStudentClass().getName());
        }
        return dto;
    }
}
