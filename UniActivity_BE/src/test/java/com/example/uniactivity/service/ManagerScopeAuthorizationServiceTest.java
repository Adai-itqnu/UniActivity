package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerScopeAuthorizationServiceTest {

    @Mock UserRepository userRepository;
    @Mock ActivityRegistrationRepository registrationRepository;
    @Mock ActivityService activityService;

    private ManagerScopeAuthorizationService service;
    private User manager;
    private StudentClass managedClass;

    @BeforeEach
    void setUp() {
        service = new ManagerScopeAuthorizationService(
                userRepository, registrationRepository, activityService);
        managedClass = studentClass(10L);
        manager = user(1L, managedClass);
    }

    @Test
    void rejectsRegistrationFromAnotherClass() {
        ActivityRegistration registration = registration(100L, user(2L, studentClass(20L)));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(registration));

        assertThrows(AuthorizationException.class,
                () -> service.requireRegistration(manager, 100L));
    }

    @Test
    void returnsRegistrationFromManagersClass() {
        ActivityRegistration registration = registration(100L, user(2L, managedClass));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(registration));

        assertSame(registration, service.requireRegistration(manager, 100L));
    }

    @Test
    void filtersActivityRegistrationsByManagersClass() {
        Activity activity = activity(50L);
        ActivityRegistration registration = registration(100L, user(2L, managedClass));
        when(activityService.findActivityById(50L)).thenReturn(activity);
        when(activityService.isActivityVisibleToStudent(activity, manager)).thenReturn(true);
        when(registrationRepository
                .findByActivityAndStudent_StudentClassOrderByRegisteredAtAsc(activity, managedClass))
                .thenReturn(List.of(registration));

        List<ActivityRegistration> result = service.registrationsForActivity(manager, 50L);

        assertEquals(List.of(registration), result);
    }

    @Test
    void rejectsActivityNotVisibleToManagersClass() {
        Activity activity = activity(50L);
        when(activityService.findActivityById(50L)).thenReturn(activity);
        when(activityService.isActivityVisibleToStudent(activity, manager)).thenReturn(false);

        assertThrows(AuthorizationException.class,
                () -> service.requireActivity(manager, 50L));
    }

    @Test
    void rejectsManagerWithoutClass() {
        manager.setStudentClass(null);

        assertThrows(AuthorizationException.class,
                () -> service.requireManagedClass(manager));
    }

    private static StudentClass studentClass(Long id) {
        StudentClass value = new StudentClass();
        value.setId(id);
        return value;
    }

    private static User user(Long id, StudentClass studentClass) {
        User value = new User();
        value.setId(id);
        value.setStudentClass(studentClass);
        return value;
    }

    private static Activity activity(Long id) {
        Activity value = new Activity();
        value.setId(id);
        return value;
    }

    private static ActivityRegistration registration(Long id, User student) {
        ActivityRegistration value = new ActivityRegistration();
        value.setId(id);
        value.setStudent(student);
        return value;
    }
}
