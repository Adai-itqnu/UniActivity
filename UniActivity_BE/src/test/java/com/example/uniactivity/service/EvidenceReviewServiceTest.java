package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.ScoreOption;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.ScoreOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceReviewServiceTest {

    @Mock ActivityRegistrationRepository registrationRepository;
    @Mock ScoreOptionRepository scoreOptionRepository;
    @Mock ManagerScopeAuthorizationService scopeAuthorizationService;
    @Mock TrainingPointService trainingPointService;

    private EvidenceReviewService service;
    private User manager;
    private Activity activity;
    private ActivityRegistration registration;

    @BeforeEach
    void setUp() {
        service = new EvidenceReviewService(
                registrationRepository, scoreOptionRepository,
                scopeAuthorizationService, trainingPointService);
        manager = new User();
        manager.setId(1L);
        activity = new Activity();
        activity.setId(20L);
        activity.setName("Ngày hội");
        registration = new ActivityRegistration();
        registration.setId(30L);
        registration.setStudent(new User());
        registration.setActivity(activity);
        registration.setEvidenceUrl("/uploads/evidence/a.jpg");
        registration.setIsApproved(null);
    }

    @Test
    void rejectsScoreOptionFromAnotherActivity() {
        when(scoreOptionRepository.findByIdAndActivity_Id(5L, 20L))
                .thenReturn(Optional.empty());

        assertThrows(ValidationException.class,
                () -> service.requireScoreOption(20L, 5L));
    }

    @Test
    void rejectsApprovalWithoutEvidence() {
        registration.setEvidenceUrl(" ");
        when(registrationRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(registration));

        assertThrows(ValidationException.class,
                () -> service.approve(manager, 30L));
    }

    @Test
    void rejectsApprovalAfterRejection() {
        registration.setIsApproved(false);
        when(registrationRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(registration));

        assertThrows(ConflictException.class,
                () -> service.approve(manager, 30L));
    }

    @Test
    void rejectsRejectionAfterApproval() {
        registration.setIsApproved(true);
        when(registrationRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(registration));

        assertThrows(ConflictException.class,
                () -> service.reject(manager, 30L, "Không hợp lệ"));
    }

    @Test
    void repeatedApprovalAddsScoreOnlyOnce() {
        ScoreOption option = new ScoreOption();
        option.setActivity(activity);
        option.setScoreCategory("3.1");
        option.setScoreValue(5);
        option.setName("Tham gia");
        registration.setScoreOption(option);
        when(registrationRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(registration));
        when(trainingPointService.addScoreOnce(
                registration.getStudent(), "3.1", 5,
                "AUTO_ACTIVITY", 30L, "Ngày hội - Tham gia"))
                .thenReturn(true);

        EvidenceReviewService.EvidenceReviewResult result = service.approve(manager, 30L);

        assertTrue(result.scoreAdded());
        assertThrows(ConflictException.class,
                () -> service.approve(manager, 30L));
        verify(trainingPointService, times(1)).addScoreOnce(
                registration.getStudent(), "3.1", 5,
                "AUTO_ACTIVITY", 30L, "Ngày hội - Tham gia");
    }

    @Test
    void rejectsLegacyRegistrationWithOptionFromAnotherActivity() {
        Activity anotherActivity = new Activity();
        anotherActivity.setId(21L);
        ScoreOption option = new ScoreOption();
        option.setActivity(anotherActivity);
        option.setScoreCategory("3.1");
        option.setScoreValue(5);
        registration.setScoreOption(option);
        when(registrationRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(registration));

        assertThrows(ValidationException.class,
                () -> service.approve(manager, 30L));
    }
}
