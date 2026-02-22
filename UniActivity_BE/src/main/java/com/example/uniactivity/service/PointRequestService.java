package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointRequestService {

    private final PointRequestRepository pointRequestRepository;
    private final SemesterRepository semesterRepository;
    private final @Lazy TrainingPointService trainingPointService;
    private final ScoringRulesService scoringRulesService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * Student creates a new point request
     */
    @Transactional
    public PointRequest createPointRequest(User student, String criteriaCode, Integer claimedScore, 
                                           String description, String evidenceImageUrl) {
        // Validate criteria code exists in scoring rules
        if (!scoringRulesService.isValidCriteriaCode(criteriaCode)) {
            throw new ValidationException("Mã mục điểm không hợp lệ: " + criteriaCode);
        }

        // Get current semester
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester == null) {
            throw new ValidationException("Không tìm thấy học kỳ hiện tại");
        }

        PointRequest request = new PointRequest();
        request.setStudent(student);
        request.setSemester(currentSemester);
        request.setCriteriaCode(criteriaCode);
        request.setClaimedScore(claimedScore);
        request.setDescription(description);
        request.setEvidenceImageUrl(evidenceImageUrl);
        request.setStatus(EvidenceStatus.PENDING);

        PointRequest saved = pointRequestRepository.save(request);
        
        // Phase 4: Notify manager about new point request
        StudentClass studentClass = student.getStudentClass();
        if (studentClass != null) {
            String criteriaName = scoringRulesService.getCriteriaName(criteriaCode);
            userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER)
                .ifPresent(manager -> notificationService.notifyNewPointRequest(manager, student.getFullName(), criteriaName));
        }
        
        return saved;
    }

    /**
     * Get student's point requests
     */
    public List<PointRequest> getStudentPointRequests(User student) {
        return pointRequestRepository.findByStudent(student);
    }

    /**
     * Get pending point requests for a class (for Manager)
     */
    public List<PointRequest> getPendingRequestsForClass(StudentClass studentClass) {
        return pointRequestRepository.findByStudentClassAndStatus(studentClass, EvidenceStatus.PENDING);
    }

    /**
     * Get all point requests for a class (for Manager)
     */
    public List<PointRequest> getAllRequestsForClass(StudentClass studentClass) {
        return pointRequestRepository.findByStudentClass(studentClass);
    }

    /**
     * Count pending point requests for a class
     */
    public long getPendingRequestCount(StudentClass studentClass) {
        return pointRequestRepository.countByStudentClassAndStatus(studentClass, EvidenceStatus.PENDING);
    }

    /**
     * Manager approves a point request
     */
    @Transactional
    public void approveRequest(Long requestId, User manager, String comment) {
        PointRequest request = pointRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Yêu cầu điểm", requestId));

        if (request.getStatus() != EvidenceStatus.PENDING) {
            throw new ValidationException("Yêu cầu này đã được xử lý");
        }

        // Verify manager owns the class
        if (!request.getStudent().getStudentClass().equals(manager.getStudentClass())) {
            throw new ValidationException("Bạn không có quyền duyệt yêu cầu này");
        }

        request.setStatus(EvidenceStatus.APPROVED);
        request.setReviewer(manager);
        request.setReviewComment(comment);
        request.setReviewedAt(LocalDateTime.now());
        
        pointRequestRepository.save(request);
        
        // Sync score to StudentTrainingPoint
        trainingPointService.addOrUpdateScore(
                request.getStudent(),
                request.getCriteriaCode(),
                request.getClaimedScore(),
                "MANUAL",
                request.getId(),
                request.getDescription()
        );
        
        // Gửi notification real-time cho sinh viên (điểm đã được duyệt)
        String criteriaName = scoringRulesService.getCriteriaName(request.getCriteriaCode());
        notificationService.notifyPointRequestApproved(request.getStudent(), criteriaName, request.getClaimedScore());
        
        // Gửi dashboard_update SSE cho manager để cập nhật stat cards real-time
        sendDashboardUpdateToManager(manager);
    }

    /**
     * Manager rejects a point request
     */
    @Transactional
    public void rejectRequest(Long requestId, User manager, String comment) {
        PointRequest request = pointRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Yêu cầu điểm", requestId));

        if (request.getStatus() != EvidenceStatus.PENDING) {
            throw new ValidationException("Yêu cầu này đã được xử lý");
        }

        // Verify manager owns the class
        if (!request.getStudent().getStudentClass().equals(manager.getStudentClass())) {
            throw new ValidationException("Bạn không có quyền từ chối yêu cầu này");
        }

        request.setStatus(EvidenceStatus.REJECTED);
        request.setReviewer(manager);
        request.setReviewComment(comment);
        request.setReviewedAt(LocalDateTime.now());
        
        pointRequestRepository.save(request);
        
        // Gửi notification real-time cho sinh viên (điểm bị từ chối)
        String criteriaName = scoringRulesService.getCriteriaName(request.getCriteriaCode());
        notificationService.notifyPointRequestRejected(request.getStudent(), criteriaName, comment);
        
        // Gửi dashboard_update SSE cho manager
        sendDashboardUpdateToManager(manager);
    }
    
    /**
     * Gửi SSE event cập nhật dashboard cho Manager
     * Cập nhật real-time stat cards: số yêu cầu điểm đang chờ
     */
    private void sendDashboardUpdateToManager(User manager) {
        try {
            if (manager.getStudentClass() == null) return;
            
            long pendingPointRequests = getPendingRequestCount(manager.getStudentClass());
            
            Map<String, Object> dashboardData = new HashMap<>();
            dashboardData.put("pendingPointRequests", pendingPointRequests);
            
            sseEmitterService.sendToUser(manager.getId(), "dashboard_update", dashboardData);
            log.debug("Sent dashboard_update to manager {} after point request processing", manager.getId());
        } catch (Exception e) {
            log.error("Failed to send dashboard update to manager: {}", e.getMessage());
        }
    }
}
