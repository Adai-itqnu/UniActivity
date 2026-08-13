package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.ScoreOption;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceSubmissionServiceTest {

    @Mock ActivityService activityService;
    @Mock ActivityRegistrationRepository registrationRepository;
    @Mock EvidenceReviewService evidenceReviewService;
    @Mock FileUploadService fileUploadService;
    @Mock MultipartFile image;

    private EvidenceSubmissionService service;
    private User student;
    private Activity activity;
    private ActivityRegistration registration;

    @BeforeEach
    void setUp() {
        service = new EvidenceSubmissionService(
                activityService, registrationRepository,
                evidenceReviewService, fileUploadService);
        student = new User();
        student.setId(1L);
        activity = new Activity();
        activity.setId(2L);
        registration = new ActivityRegistration();
        registration.setId(3L);
        registration.setStudent(student);
        registration.setActivity(activity);
        registration.setStatus(RegistrationStatus.ATTENDED);
    }

    @Test
    void approvedEvidenceCannotBeReopened() throws Exception {
        registration.setIsApproved(true);
        stubLockedRegistration();

        assertThrows(ConflictException.class,
                () -> service.submit(student, 2L, 4L, List.of(image)));

        verify(fileUploadService, never()).uploadEvidenceImages(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pendingEvidenceCannotBeChangedDuringReview() {
        registration.setEvidenceUrl("/uploads/evidence/pending.jpg");
        registration.setIsApproved(null);
        stubLockedRegistration();

        assertThrows(ConflictException.class,
                () -> service.submit(student, 2L, 4L, List.of(image)));
    }

    @Test
    void rejectedEvidenceMayBeResubmittedAsPending() throws Exception {
        registration.setEvidenceUrl("/uploads/evidence/rejected.jpg");
        registration.setIsApproved(false);
        registration.setRejectionReason("Mờ");
        stubLockedRegistration();
        ScoreOption option = new ScoreOption();
        option.setId(4L);
        when(evidenceReviewService.requireScoreOption(2L, 4L)).thenReturn(option);
        when(fileUploadService.isValidImage(image)).thenReturn(true);
        when(fileUploadService.getMaxFileSize()).thenReturn(5L * 1024 * 1024);
        when(image.getSize()).thenReturn(1024L);
        when(fileUploadService.uploadEvidenceImages(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("/uploads/evidence/new.jpg"));

        EvidenceSubmissionService.EvidenceSubmissionResult result =
                service.submit(student, 2L, 4L, List.of(image));

        assertEquals(option, registration.getScoreOption());
        assertEquals("/uploads/evidence/new.jpg", registration.getEvidenceUrl());
        assertNull(registration.getIsApproved());
        assertNull(registration.getRejectionReason());
        assertEquals(1, result.paths().size());
        verify(registrationRepository).save(registration);
    }

    private void stubLockedRegistration() {
        when(activityService.findActivityById(2L)).thenReturn(activity);
        when(registrationRepository.findByActivityAndStudentForUpdate(activity, student))
                .thenReturn(Optional.of(registration));
    }
}
