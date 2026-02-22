package com.example.uniactivity.mapper;

import com.example.uniactivity.dto.manager.ActivityRegistrationResponseDto;
import com.example.uniactivity.entity.ActivityRegistration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for ActivityRegistration entity to DTO conversion
 */
@Component
public class ActivityRegistrationMapper {

    public ActivityRegistrationResponseDto toResponseDto(ActivityRegistration entity) {
        if (entity == null) {
            return null;
        }
        
        return ActivityRegistrationResponseDto.builder()
                .id(entity.getId())
                .studentId(entity.getStudent() != null ? entity.getStudent().getId() : null)
                .studentName(entity.getStudent() != null ? entity.getStudent().getFullName() : null)
                .studentCode(entity.getStudent() != null ? entity.getStudent().getUsername() : null)
                .activityId(entity.getActivity() != null ? entity.getActivity().getId() : null)
                .activityName(entity.getActivity() != null ? entity.getActivity().getName() : null)
                .activitySlotId(entity.getActivitySlot() != null ? entity.getActivitySlot().getId() : null)
                .activitySlotInfo(buildSlotInfo(entity))
                .scoreOptionId(entity.getScoreOption() != null ? entity.getScoreOption().getId() : null)
                .scoreOptionName(entity.getScoreOption() != null ? entity.getScoreOption().getName() : null)
                .scoreCategory(entity.getScoreOption() != null ? entity.getScoreOption().getScoreCategory() : null)
                .scoreValue(entity.getScoreOption() != null ? entity.getScoreOption().getScoreValue() : null)
                .registeredAt(entity.getRegisteredAt())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .attendanceConfirmed(entity.getAttendanceConfirmed())
                .confirmedAt(entity.getConfirmedAt())
                .notes(entity.getNotes())
                .evidenceUrl(entity.getEvidenceUrl())
                .isApproved(entity.getIsApproved())
                .rejectionReason(entity.getRejectionReason())
                .build();
    }

    public List<ActivityRegistrationResponseDto> toResponseDtoList(List<ActivityRegistration> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    private String buildSlotInfo(ActivityRegistration entity) {
        if (entity.getActivitySlot() == null) {
            return null;
        }
        var slot = entity.getActivitySlot();
        return String.format("%d/%d", slot.getCurrentQuantity(), slot.getMaxQuantity());
    }
}
