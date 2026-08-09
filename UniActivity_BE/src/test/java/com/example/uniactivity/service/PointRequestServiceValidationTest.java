package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
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
import static org.mockito.Mockito.verify;
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
    }

    @Test void rejectsNullClaimedScore() {
        stubCriteria();
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", null, "Chứng chỉ", "a.jpg"));
    }

    @Test void rejectsNegativeClaimedScore() {
        stubCriteria();
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", -1, "Chứng chỉ", "a.jpg"));
    }

    @Test void rejectsScoreAboveCriteriaMaximum() {
        stubCriteria();
        assertThrows(ValidationException.class,
                () -> service.createPointRequest(student, "1.3", 6, "Chứng chỉ", "a.jpg"));
    }

    @Test void acceptsScoreAtCriteriaMaximum() {
        stubCriteria();
        Semester semester = new Semester();
        semester.setId(2L);
        when(semesterRepository.findByIsCurrentTrue()).thenReturn(semester);
        when(pointRequestRepository.save(any(PointRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PointRequest result =
                service.createPointRequest(student, "1.3", 5, "Chứng chỉ", "a.jpg");

        assertEquals(5, result.getClaimedScore());
    }

    @Test void approvalAddsAUniquePointRequestScoreInsteadOfOverwritingCriteria() {
        StudentClass studentClass = new StudentClass();
        studentClass.setId(10L);
        student.setStudentClass(studentClass);

        User manager = new User();
        manager.setId(2L);
        manager.setStudentClass(studentClass);

        PointRequest request = new PointRequest();
        request.setId(99L);
        request.setStudent(student);
        request.setCriteriaCode("1.3");
        request.setClaimedScore(5);
        request.setDescription("Chứng chỉ ngoại ngữ");
        request.setStatus(EvidenceStatus.PENDING);
        when(pointRequestRepository.findByIdForUpdate(99L)).thenReturn(java.util.Optional.of(request));

        service.approveRequest(99L, manager, "Hợp lệ");

        verify(trainingPointService).addScoreOnce(
                student, "1.3", 5, "POINT_REQUEST", 99L, "Chứng chỉ ngoại ngữ");
        assertEquals(EvidenceStatus.APPROVED, request.getStatus());
    }

    private void stubCriteria() {
        when(scoringRulesService.isValidCriteriaCode("1.3")).thenReturn(true);
        when(scoringRulesService.getMaximumClaimedScore("1.3")).thenReturn(5);
    }
}
