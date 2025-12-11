package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.manager.PointRequestResponseDto;
import com.example.uniactivity.entity.PointRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for PointRequest entity to DTO conversion
 */
@Component
public class PointRequestMapper {

    public PointRequestResponseDto toResponseDto(PointRequest entity) {
        if (entity == null) {
            return null;
        }
        
        return PointRequestResponseDto.builder()
                .id(entity.getId())
                .studentId(entity.getStudent() != null ? entity.getStudent().getId() : null)
                .studentName(entity.getStudent() != null ? entity.getStudent().getFullName() : null)
                .studentCode(entity.getStudent() != null ? entity.getStudent().getUsername() : null)
                .semesterName(entity.getSemester() != null ? entity.getSemester().getName() : null)
                .criteriaCode(entity.getCriteriaCode())
                .claimedScore(entity.getClaimedScore())
                .description(entity.getDescription())
                .evidenceImageUrl(entity.getEvidenceImageUrl())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .reviewerId(entity.getReviewer() != null ? entity.getReviewer().getId() : null)
                .reviewerName(entity.getReviewer() != null ? entity.getReviewer().getFullName() : null)
                .reviewComment(entity.getReviewComment())
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<PointRequestResponseDto> toResponseDtoList(List<PointRequest> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }
}
