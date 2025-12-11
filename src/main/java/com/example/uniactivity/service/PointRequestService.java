package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointRequestService {

    private final PointRequestRepository pointRequestRepository;
    private final SemesterRepository semesterRepository;
    private final @Lazy TrainingPointService trainingPointService;
    private final ScoringRulesService scoringRulesService;

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

        return pointRequestRepository.save(request);
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
    }
}
