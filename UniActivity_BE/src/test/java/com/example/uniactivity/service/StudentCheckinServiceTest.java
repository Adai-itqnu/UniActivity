package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCheckinServiceTest {

    @Mock ActivityService activityService;
    @Mock ActivityRegistrationRepository registrationRepository;
    @Mock DynamicQrTokenService qrTokenService;

    private StudentCheckinService service;
    private User student;
    private Activity activity;
    private ActivityRegistration registration;

    @BeforeEach
    void setUp() {
        service = new StudentCheckinService(
                activityService, registrationRepository, qrTokenService);
        StudentClass studentClass = new StudentClass();
        studentClass.setId(10L);
        student = new User();
        student.setId(1L);
        student.setStudentClass(studentClass);

        activity = new Activity();
        activity.setId(20L);
        activity.setStatus(ActivityStatus.OPEN);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));

        registration = new ActivityRegistration();
        registration.setId(30L);
        registration.setActivity(activity);
        registration.setStudent(student);
        registration.setStatus(RegistrationStatus.REGISTERED);
    }

    @Test void rejectsMissingClassId() {
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, null, "token", null, null, null));
    }

    @Test void rejectsMissingToken() {
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, " ", null, null, null));
    }

    @Test void rejectsWrongStudentClass() {
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 11L, "token", null, null, null));
    }

    @Test void rejectsInvalidQrToken() {
        stubActivityAndToken(false);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "bad", null, null, null));
    }

    @Test void rejectsNonOpenActivity() {
        activity.setStatus(ActivityStatus.FINISHED);
        when(activityService.findActivityById(20L)).thenReturn(activity);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", null, null, null));
    }

    @Test void rejectsBeforeStartTime() {
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        when(activityService.findActivityById(20L)).thenReturn(activity);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", null, null, null));
    }

    @Test void rejectsAfterEndTime() {
        activity.setStartTime(LocalDateTime.now().minusHours(2));
        activity.setEndTime(LocalDateTime.now().minusHours(1));
        when(activityService.findActivityById(20L)).thenReturn(activity);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", null, null, null));
    }

    @Test void rejectsNaNLatitudeLongitudeAndAccuracy() {
        configureGps();
        stubActivityAndToken(true);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", Double.NaN, 109.2, 5.0));
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", 13.7, Double.NaN, 5.0));
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", 13.7, 109.2, Double.NaN));
    }

    @Test void rejectsOutsideRadius() {
        configureGps();
        stubActivityAndToken(true);
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", 14.7, 110.2, 5.0));
    }

    @Test void rejectsCancelledRegistration() {
        registration.setStatus(RegistrationStatus.CANCELLED);
        stubSuccessfulPreconditions();
        assertThrows(ValidationException.class,
                () -> service.checkIn(student, 20L, 10L, "token", null, null, null));
    }

    @Test void marksRegisteredStudentAttended() {
        stubSuccessfulPreconditions();
        when(registrationRepository.save(registration)).thenReturn(registration);

        ActivityRegistration result =
                service.checkIn(student, 20L, 10L, "token", null, null, null);

        assertEquals(RegistrationStatus.ATTENDED, result.getStatus());
        verify(registrationRepository).findByActivityAndStudentForUpdate(activity, student);
        verify(registrationRepository).save(registration);
    }

    private void stubActivityAndToken(boolean tokenValid) {
        when(activityService.findActivityById(20L)).thenReturn(activity);
        if (tokenValid) {
            when(qrTokenService.validateToken("token", 20L, 10L)).thenReturn(true);
        }
    }

    private void stubSuccessfulPreconditions() {
        stubActivityAndToken(true);
        when(registrationRepository.findByActivityAndStudentForUpdate(activity, student))
                .thenReturn(Optional.of(registration));
    }

    private void configureGps() {
        activity.setLatitude(13.7);
        activity.setLongitude(109.2);
        activity.setCheckinRadius(100);
    }
}
