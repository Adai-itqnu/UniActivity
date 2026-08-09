package com.example.uniactivity.service;

import com.example.uniactivity.entity.ActivitySlot;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.mapper.ActivityMapper;
import com.example.uniactivity.repository.AcademicYearRepository;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.ActivityRepository;
import com.example.uniactivity.repository.ActivitySlotRepository;
import com.example.uniactivity.repository.FacultyRepository;
import com.example.uniactivity.repository.ScoreOptionRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityRegistrationTransactionTest {

    @Mock ActivityRepository activityRepository;
    @Mock ActivitySlotRepository activitySlotRepository;
    @Mock ScoreOptionRepository scoreOptionRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock FacultyRepository facultyRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock StudentClassRepository studentClassRepository;
    @Mock ActivityMapper activityMapper;
    @Mock ActivityRegistrationRepository activityRegistrationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SseEmitterService sseEmitterService;
    @Mock TransactionTemplate transactionTemplate;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(
                activityRepository, activitySlotRepository, scoreOptionRepository,
                semesterRepository, facultyRepository, academicYearRepository,
                studentClassRepository, activityMapper, activityRegistrationRepository,
                userRepository, notificationService, sseEmitterService, transactionTemplate);
    }

    @Test
    void registrationRetriesInAFreshTransactionAfterOptimisticConflict() {
        Map<String, Object> success = Map.of("message", "ok", "registrationId", 7L);
        when(transactionTemplate.execute(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(ActivitySlot.class, 4L))
                .thenReturn(success);

        Map<String, Object> result = service.registerStudentForActivity(new User(), 3L);

        assertEquals(success, result);
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void cancellationRunsInATransaction() {
        Map<String, Object> success = Map.of("message", "ok");
        when(transactionTemplate.execute(any())).thenReturn(success);

        Map<String, Object> result = service.cancelStudentRegistration(new User(), 3L);

        assertEquals(success, result);
        verify(transactionTemplate).execute(any());
    }
}
