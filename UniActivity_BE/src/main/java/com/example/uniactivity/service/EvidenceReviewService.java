package com.example.uniactivity.service;

import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.ScoreOption;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.ScoreOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

    private final ActivityRegistrationRepository registrationRepository;
    private final ScoreOptionRepository scoreOptionRepository;
    private final ManagerScopeAuthorizationService scopeAuthorizationService;
    private final TrainingPointService trainingPointService;

    public ScoreOption requireScoreOption(Long activityId, Long scoreOptionId) {
        return scoreOptionRepository.findByIdAndActivity_Id(scoreOptionId, activityId)
                .orElseThrow(() -> new ValidationException(
                        "Mục điểm không thuộc hoạt động này"));
    }

    @Transactional
    public EvidenceReviewResult approve(User manager, Long registrationId) {
        ActivityRegistration registration = loadPending(manager, registrationId);
        ScoreOption option = registration.getScoreOption();
        if (option != null && (option.getActivity() == null
                || option.getActivity().getId() == null
                || !option.getActivity().getId().equals(registration.getActivity().getId()))) {
            throw new ValidationException("Mục điểm không thuộc hoạt động này");
        }
        String criteriaCode = option == null ? "3.1" : option.getScoreCategory();
        int score = option == null ? 5 : option.getScoreValue();
        String description = option == null
                ? "Tham gia hoạt động: " + registration.getActivity().getName()
                : registration.getActivity().getName() + " - " + option.getName();

        boolean scoreAdded = trainingPointService.addScoreOnce(
                registration.getStudent(), criteriaCode, score,
                "AUTO_ACTIVITY", registration.getId(), description);
        registration.setIsApproved(true);
        registration.setRejectionReason(null);
        registrationRepository.save(registration);
        return new EvidenceReviewResult(registration, score, scoreAdded);
    }

    @Transactional
    public void reject(User manager, Long registrationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Vui lòng nhập lý do từ chối");
        }
        ActivityRegistration registration = loadPending(manager, registrationId);
        registration.setIsApproved(false);
        registration.setRejectionReason(reason.trim());
        registrationRepository.save(registration);
    }

    private ActivityRegistration loadPending(User manager, Long registrationId) {
        ActivityRegistration registration = registrationRepository
                .findByIdForUpdate(registrationId)
                .orElseThrow(() -> new NotFoundException("Đăng ký", registrationId));
        scopeAuthorizationService.assertRegistrationInScope(manager, registration);
        if (registration.getIsApproved() != null) {
            throw new ConflictException("Minh chứng này đã được xử lý");
        }
        if (registration.getEvidenceUrl() == null
                || registration.getEvidenceUrl().isBlank()) {
            throw new ValidationException("Đăng ký chưa có minh chứng");
        }
        return registration;
    }

    public record EvidenceReviewResult(
            ActivityRegistration registration, int score, boolean scoreAdded) {
    }
}
