package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.manager.ClassJoinRequestResponseDto;
import com.example.uniactivity.entity.ClassJoinRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for ClassJoinRequest entity to DTO conversion
 */
@Component
public class ClassJoinRequestMapper {

    public ClassJoinRequestResponseDto toResponseDto(ClassJoinRequest entity) {
        if (entity == null) {
            return null;
        }
        
        return ClassJoinRequestResponseDto.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .userStudentCode(entity.getUser() != null ? entity.getUser().getUsername() : null)
                .classId(entity.getStudentClass() != null ? entity.getStudentClass().getId() : null)
                .className(entity.getStudentClass() != null ? entity.getStudentClass().getName() : null)
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .createdAt(entity.getCreatedAt())
                .processedAt(entity.getProcessedAt())
                .processedById(entity.getProcessedBy() != null ? entity.getProcessedBy().getId() : null)
                .processedByName(entity.getProcessedBy() != null ? entity.getProcessedBy().getFullName() : null)
                .build();
    }

    public List<ClassJoinRequestResponseDto> toResponseDtoList(List<ClassJoinRequest> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }
}
