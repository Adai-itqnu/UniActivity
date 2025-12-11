package com.example.uniactivity.service;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.JoinRequestStatus;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ClassJoinRequestRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassJoinRequestService {

    private final ClassJoinRequestRepository joinRequestRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;

    /**
     * Student creates a join request using class join code
     */
    @Transactional
    public ClassJoinRequest createJoinRequest(User student, String joinCode) {
        // Find class by join code
        StudentClass studentClass = studentClassRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new NotFoundException("Mã tham gia không hợp lệ"));

        // Check if student already has a class
        if (student.getStudentClass() != null) {
            throw new ValidationException("Bạn đã thuộc lớp " + student.getStudentClass().getName());
        }

        // Check if student already has a pending request for this class
        if (joinRequestRepository.existsByUserAndStudentClassAndStatus(student, studentClass, JoinRequestStatus.PENDING)) {
            throw new ValidationException("Bạn đã gửi yêu cầu tham gia lớp này. Vui lòng chờ duyệt.");
        }

        // Create new join request
        ClassJoinRequest request = new ClassJoinRequest();
        request.setUser(student);
        request.setStudentClass(studentClass);
        request.setStatus(JoinRequestStatus.PENDING);

        return joinRequestRepository.save(request);
    }

    /**
     * Get pending requests for a class (for manager)
     */
    public List<ClassJoinRequest> getPendingRequestsForClass(StudentClass studentClass) {
        return joinRequestRepository.findByStudentClassAndStatus(studentClass, JoinRequestStatus.PENDING);
    }

    /**
     * Approve a join request - assigns class_id to user
     */
    @Transactional
    public void approveRequest(Long requestId, User manager) {
        ClassJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Yêu cầu", requestId));

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new ValidationException("Yêu cầu này đã được xử lý");
        }

        // Verify manager owns the class
        if (!request.getStudentClass().equals(manager.getStudentClass())) {
            throw new ValidationException("Bạn không có quyền duyệt yêu cầu này");
        }

        // Update request status
        request.setStatus(JoinRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(manager);
        joinRequestRepository.save(request);

        // Assign class to user
        User student = request.getUser();
        student.setStudentClass(request.getStudentClass());
        userRepository.save(student);
    }

    /**
     * Reject a join request
     */
    @Transactional
    public void rejectRequest(Long requestId, User manager) {
        ClassJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Yêu cầu", requestId));

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new ValidationException("Yêu cầu này đã được xử lý");
        }

        // Verify manager owns the class
        if (!request.getStudentClass().equals(manager.getStudentClass())) {
            throw new ValidationException("Bạn không có quyền từ chối yêu cầu này");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(manager);
        joinRequestRepository.save(request);
    }

    /**
     * Get pending request count for a class
     */
    public long getPendingRequestCount(StudentClass studentClass) {
        return joinRequestRepository.countByStudentClassAndStatus(studentClass, JoinRequestStatus.PENDING);
    }

    /**
     * Get pending request for current user
     */
    public ClassJoinRequest getPendingRequestForUser(User user) {
        return joinRequestRepository.findByUserAndStatus(user, JoinRequestStatus.PENDING).orElse(null);
    }
}
