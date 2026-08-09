package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointRequestServiceValidationTest {

    @Mock PointRequestRepository pointRequestRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TrainingPointService trainingPointService;
    @Mock ScoringRulesService scoringRulesService;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock SseEmitterService sseEmitterService;

    private PointRequestService service;
    private User student;

    @BeforeEach
    void setUp() {
        service = new PointRequestService(
                pointRequestRepository, semesterRepository, trainingPointService,
                scoringRulesService, notificationService, userRepository, sseEmitterService);
        student = new User();
        student.setId(1L);
        when(scoringRulesService.isValidCriteriaCode("1.3")).thenReturn(true);
        when(scoringRulesService.getMaximumClaimedScore("1.3")).thenReturn(5);
    }

    @Test void rejectsNullClaimedScore() {
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", null, "Chứng chỉ", "a.jpg"));
    }

    @Test void rejectsNegativeClaimedScore() {
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", -1, "Chứng chỉ", "a.jpg"));
    }

    @Test void rejectsScoreAboveCriteriaMaximum() {
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", 6, "Chứng chỉ", "a.jpg"));
    }

    @Test void acceptsScoreAtCriteriaMaximum() {
        Semester semester = new Semester();
        semester.setId(2L);
        when(semesterRepository.findByIsCurrentTrue()).thenReturn(semester);
        when(pointRequestRepository.save(any(PointRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PointRequest result =
                service.createPointRequest(student, "1.3", 5, "Chứng chỉ", "a.jpg");

        assertEquals(5, result.getClaimedScore());
    }
}
