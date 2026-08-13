package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.ActivitySlot;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.enums.RegistrationStatus;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void reactivationRechecksCurrentVisibility() {
        User student = student(11L);
        Activity activity = openActivity(3L);
        ActivityRegistration cancelled = cancelledRegistration(student, activity, null);
        executeRealTransactionCallback();
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activity));
        when(activityRegistrationRepository.findByActivityAndStudent(activity, student))
                .thenReturn(Optional.of(cancelled));
        when(activitySlotRepository.findByActivityId(3L)).thenReturn(List.of());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.registerStudentForActivity(student, 3L));

        assertEquals("Bạn không thể đăng ký hoạt động này", error.getMessage());
    }

    @Test
    void reactivationRejectsAFullCurrentSlot() {
        User student = student(11L);
        Activity activity = openActivity(3L);
        ActivitySlot slot = new ActivitySlot();
        slot.setId(9L);
        slot.setActivity(activity);
        slot.setStudentClass(student.getStudentClass());
        slot.setCurrentQuantity(10);
        slot.setMaxQuantity(10);
        ActivityRegistration cancelled = cancelledRegistration(student, activity, slot);
        executeRealTransactionCallback();
        when(activityRepository.findById(3L)).thenReturn(Optional.of(activity));
        when(activityRegistrationRepository.findByActivityAndStudent(activity, student))
                .thenReturn(Optional.of(cancelled));
        when(activitySlotRepository.findByActivityId(3L)).thenReturn(List.of(slot));
        when(activitySlotRepository.findById(9L)).thenReturn(Optional.of(slot));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.registerStudentForActivity(student, 3L));

        assertEquals("Slot đã đầy, không thể đăng ký", error.getMessage());
    }

    @SuppressWarnings("unchecked")
    private void executeRealTransactionCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((org.springframework.transaction.support.TransactionCallback<Map<String, Object>>)
                        invocation.getArgument(0)).doInTransaction(null));
    }

    private static User student(Long classId) {
        StudentClass studentClass = new StudentClass();
        studentClass.setId(classId);
        User student = new User();
        student.setId(1L);
        student.setStudentClass(studentClass);
        return student;
    }

    private static Activity openActivity(Long id) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setStatus(ActivityStatus.OPEN);
        return activity;
    }

    private static ActivityRegistration cancelledRegistration(
            User student, Activity activity, ActivitySlot slot) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setId(7L);
        registration.setStudent(student);
        registration.setActivity(activity);
        registration.setActivitySlot(slot);
        registration.setStatus(RegistrationStatus.CANCELLED);
        return registration;
    }
}
