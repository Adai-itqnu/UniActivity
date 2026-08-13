package com.example.uniactivity.service;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.JoinRequestStatus;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ClassJoinRequestRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassJoinRequestService {

    private final ClassJoinRequestRepository joinRequestRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    /**
     * Student creates a join request using class join code
     */
    @Transactional
    public ClassJoinRequest createJoinRequest(User student, String joinCode) {
        // Fetch fresh user data from database to ensure current class status
        User freshStudent = userRepository.findById(student.getId())
                .orElseThrow(() -> new NotFoundException("Người dùng", student.getId()));
        
        // Find class by join code
        StudentClass studentClass = studentClassRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new NotFoundException("Mã tham gia không hợp lệ"));

        // Check if student already has a class
        if (freshStudent.getStudentClass() != null) {
            throw new ValidationException("Bạn đã thuộc lớp " + freshStudent.getStudentClass().getName());
        }

        // Check if student already has a pending request for this class
        if (joinRequestRepository.existsByUserAndStudentClassAndStatus(freshStudent, studentClass, JoinRequestStatus.PENDING)) {
            throw new ValidationException("Bạn đã gửi yêu cầu tham gia lớp này. Vui lòng chờ duyệt.");
        }

        // Create new join request
        ClassJoinRequest request = new ClassJoinRequest();
        request.setUser(freshStudent);
        request.setStudentClass(studentClass);
        request.setStatus(JoinRequestStatus.PENDING);

        ClassJoinRequest saved = joinRequestRepository.save(request);
        
        // Phase 4: Notify manager about new join request
        userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER)
            .forEach(manager -> notificationService.notifyNewJoinRequest(manager, freshStudent.getFullName()));
        
        // Gửi dashboard_update SSE cho tất cả các manager để cập nhật số lượng yêu cầu chờ duyệt real-time
        sendDashboardUpdateToClassManagers(studentClass);
        
        return saved;
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
            throw new AuthorizationException("Bạn không có quyền duyệt yêu cầu này");
        }

        // Update request status
        request.setStatus(JoinRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(manager);
        joinRequestRepository.save(request);

        // Gán lớp cho sinh viên
        User student = request.getUser();
        student.setStudentClass(request.getStudentClass());
        userRepository.save(student);
        
        // Gửi notification real-time cho sinh viên
        notificationService.notifyJoinRequestApproved(student, request.getStudentClass().getName());
        
        // Gửi dashboard_update SSE cho tất cả manager để cập nhật số lượng thành viên và yêu cầu chờ duyệt real-time
        sendDashboardUpdateToClassManagers(request.getStudentClass());
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
            throw new AuthorizationException("Bạn không có quyền từ chối yêu cầu này");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(manager);
        joinRequestRepository.save(request);
        
        // Gửi notification real-time cho sinh viên
        notificationService.notifyJoinRequestRejected(request.getUser(), request.getStudentClass().getName());
        
        // Gửi dashboard_update SSE cho tất cả manager
        sendDashboardUpdateToClassManagers(request.getStudentClass());
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
        // Fetch fresh user data from database
        User freshUser = userRepository.findById(user.getId())
                .orElse(user);
        return joinRequestRepository.findByUserAndStatus(freshUser, JoinRequestStatus.PENDING).orElse(null);
    }
    
    /**
     * Gửi SSE event cập nhật dashboard cho tất cả các Manager thuộc lớp học
     */
    public void sendDashboardUpdateToClassManagers(StudentClass studentClass) {
        try {
            if (studentClass == null) return;
            
            long memberCount = userRepository.countByStudentClass(studentClass);
            long pendingJoinRequests = getPendingRequestCount(studentClass);
            
            Map<String, Object> dashboardData = new HashMap<>();
            dashboardData.put("memberCount", memberCount);
            dashboardData.put("pendingJoinRequests", pendingJoinRequests);
            
            userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER)
                .forEach(manager -> {
                    sseEmitterService.sendToUser(manager.getId(), "dashboard_update", dashboardData);
                });
            log.debug("Sent dashboard_update to all managers of class {} after join request update", studentClass.getId());
        } catch (Exception e) {
            log.error("Failed to send dashboard update to class managers: {}", e.getMessage());
        }
    }
}
