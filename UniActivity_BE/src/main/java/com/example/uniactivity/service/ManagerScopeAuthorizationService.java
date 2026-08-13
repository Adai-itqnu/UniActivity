package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerScopeAuthorizationService {

    private final UserRepository userRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final ActivityService activityService;

    public StudentClass requireManagedClass(User manager) {
        if (manager == null || manager.getStudentClass() == null) {
            throw new AuthorizationException("Tài khoản quản lý chưa được phân công lớp");
        }
        return manager.getStudentClass();
    }

    public User requireStudent(User manager, Long studentId) {
        StudentClass managedClass = requireManagedClass(manager);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Sinh viên", studentId));
        requireSameClass(managedClass, student);
        if (student.getRole() != Role.STUDENT) {
            throw new AuthorizationException("Tài khoản được chọn không phải sinh viên");
        }
        return student;
    }

    public ActivityRegistration requireRegistration(User manager, Long registrationId) {
        ActivityRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new NotFoundException("Đăng ký", registrationId));
        assertRegistrationInScope(manager, registration);
        return registration;
    }

    public void assertRegistrationInScope(
            User manager, ActivityRegistration registration) {
        StudentClass managedClass = requireManagedClass(manager);
        requireSameClass(managedClass, registration.getStudent());
    }

    public Activity requireActivity(User manager, Long activityId) {
        requireManagedClass(manager);
        Activity activity = activityService.findActivityById(activityId);
        if (!activityService.isActivityVisibleToStudent(activity, manager)) {
            throw new AuthorizationException("Bạn không có quyền quản lý hoạt động này");
        }
        return activity;
    }

    public List<ActivityRegistration> registrationsForActivity(User manager, Long activityId) {
        StudentClass managedClass = requireManagedClass(manager);
        Activity activity = requireActivity(manager, activityId);
        return registrationRepository
                .findByActivityAndStudent_StudentClassOrderByRegisteredAtAsc(activity, managedClass);
    }

    private void requireSameClass(StudentClass managedClass, User student) {
        if (student == null || student.getStudentClass() == null
                || managedClass.getId() == null
                || !managedClass.getId().equals(student.getStudentClass().getId())) {
            throw new AuthorizationException("Bạn không có quyền truy cập sinh viên ngoài lớp được phân công");
        }
    }
}
