package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.student.StudentTrainingPointResponseDto;
import com.example.uniactivity.dto.student.StudentTrainingPointResponseDto.TrainingPointDetailDto;
import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.TrainingPointDetail;
import com.example.uniactivity.repository.TrainingPointDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for StudentTrainingPoint entity to DTO conversion
 */
@Component
@RequiredArgsConstructor
public class StudentTrainingPointMapper {

    private final TrainingPointDetailRepository trainingPointDetailRepository;

    public StudentTrainingPointResponseDto toResponseDto(StudentTrainingPoint entity) {
        if (entity == null) {
            return null;
        }
        
        // Fetch details
        List<TrainingPointDetail> details = trainingPointDetailRepository.findByStudentTrainingPoint(entity);
        
        return StudentTrainingPointResponseDto.builder()
                .id(entity.getId())
                .studentId(entity.getStudent() != null ? entity.getStudent().getId() : null)
                .studentName(entity.getStudent() != null ? entity.getStudent().getFullName() : null)
                .studentCode(entity.getStudent() != null ? entity.getStudent().getUsername() : null)
                .semesterId(entity.getSemester() != null ? entity.getSemester().getId() : null)
                .semesterName(entity.getSemester() != null ? entity.getSemester().getName() : null)
                .totalScore(entity.getTotalScore())
                .classification(entity.getClassification())
                .status(entity.getStatus())
                .details(toDetailDtoList(details))
                .build();
    }

    public List<StudentTrainingPointResponseDto> toResponseDtoList(List<StudentTrainingPoint> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public TrainingPointDetailDto toDetailDto(TrainingPointDetail detail) {
        if (detail == null) {
            return null;
        }
        return TrainingPointDetailDto.builder()
                .id(detail.getId())
                .criteriaCode(detail.getCriteriaCode())
                .score(detail.getScore())
                .sourceType(detail.getSourceType())
                .referenceId(detail.getReferenceId())
                .description(detail.getDescription())
                .createdAt(detail.getCreatedAt())
                .build();
    }

    public List<TrainingPointDetailDto> toDetailDtoList(List<TrainingPointDetail> details) {
        if (details == null) {
            return List.of();
        }
        return details.stream()
                .map(this::toDetailDto)
                .collect(Collectors.toList());
    }
}
