package com.example.uniactivity.service;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralized authorization checks for IDOR protection
 */
@Service
@Slf4j
public class AuthorizationService {

    /**
     * Verify manager belongs to the same class as the student
     */
    public void verifyManagerClassOwnership(User manager, User student, String resourceType) {
        if (manager.getRole() != Role.MANAGER) {
            throw new ValidationException("Chỉ manager mới có quyền thực hiện thao tác này");
        }

        if (manager.getStudentClass() == null) {
            throw new ValidationException("Manager chưa được gán lớp");
        }

        if (student.getStudentClass() == null) {
            throw new ValidationException("Sinh viên chưa có lớp");
        }

        if (!manager.getStudentClass().getId().equals(student.getStudentClass().getId())) {
            log.warn("IDOR attempt: Manager {} tried to access {} of student {} from different class", 
                    manager.getId(), resourceType, student.getId());
            throw new NotFoundException(resourceType, 0L); // Return 404 to not reveal existence
        }
    }

    /**
     * Verify manager belongs to the specified class
     */
    public void verifyManagerBelongsToClass(User manager, StudentClass studentClass, String resourceType) {
        if (manager.getRole() != Role.MANAGER) {
            throw new ValidationException("Chỉ manager mới có quyền thực hiện thao tác này");
        }

        if (manager.getStudentClass() == null) {
            throw new ValidationException("Manager chưa được gán lớp");
        }

        if (!manager.getStudentClass().getId().equals(studentClass.getId())) {
            log.warn("IDOR attempt: Manager {} tried to access {} of class {}", 
                    manager.getId(), resourceType, studentClass.getId());
            throw new NotFoundException(resourceType, 0L);
        }
    }

    /**
     * Verify registration belongs to manager's class
     */
    public void verifyManagerOwnsRegistration(User manager, ActivityRegistration registration) {
        User student = registration.getStudent();
        verifyManagerClassOwnership(manager, student, "Đăng ký hoạt động");
    }

    /**
     * Verify point request belongs to manager's class
     */
    public void verifyManagerOwnsPointRequest(User manager, PointRequest pointRequest) {
        User student = pointRequest.getStudent();
        verifyManagerClassOwnership(manager, student, "Yêu cầu điểm");
    }

    /**
     * Verify join request belongs to manager's class
     */
    public void verifyManagerOwnsJoinRequest(User manager, ClassJoinRequest joinRequest) {
        if (manager.getRole() != Role.MANAGER) {
            throw new ValidationException("Chỉ manager mới có quyền duyệt yêu cầu");
        }

        if (manager.getStudentClass() == null) {
            throw new ValidationException("Manager chưa được gán lớp");
        }

        if (!manager.getStudentClass().getId().equals(joinRequest.getStudentClass().getId())) {
            log.warn("IDOR attempt: Manager {} tried to process join request {} for different class", 
                    manager.getId(), joinRequest.getId());
            throw new NotFoundException("Yêu cầu tham gia", joinRequest.getId());
        }
    }

    /**
     * Verify student can only access their own data
     */
    public void verifyStudentOwnership(User requestingUser, User dataOwner, String resourceType) {
        if (!requestingUser.getId().equals(dataOwner.getId())) {
            log.warn("IDOR attempt: User {} tried to access {} of user {}", 
                    requestingUser.getId(), resourceType, dataOwner.getId());
            throw new NotFoundException(resourceType, 0L);
        }
    }

    /**
     * Verify activity registration belongs to student
     */
    public void verifyStudentOwnsRegistration(User student, ActivityRegistration registration) {
        if (!registration.getStudent().getId().equals(student.getId())) {
            log.warn("IDOR attempt: Student {} tried to access registration {} of another student", 
                    student.getId(), registration.getId());
            throw new NotFoundException("Đăng ký hoạt động", registration.getId());
        }
    }
}
