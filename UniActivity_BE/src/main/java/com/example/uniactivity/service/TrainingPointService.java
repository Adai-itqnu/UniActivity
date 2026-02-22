package com.example.uniactivity.service;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.StudentTrainingPointRepository;
import com.example.uniactivity.repository.TrainingPointDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingPointService {

    private final StudentTrainingPointRepository studentTrainingPointRepository;
    private final TrainingPointDetailRepository trainingPointDetailRepository;
    private final SemesterRepository semesterRepository;
    private final ScoringRulesService scoringRulesService;

    /**
     * Get or create StudentTrainingPoint for current semester
     */
    @Transactional
    public StudentTrainingPoint getOrCreateForCurrentSemester(User student) {
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester == null) {
            throw new RuntimeException("Không tìm thấy học kỳ hiện tại");
        }
        
        return studentTrainingPointRepository.findByStudentAndSemester(student, currentSemester)
                .orElseGet(() -> {
                    StudentTrainingPoint stp = new StudentTrainingPoint();
                    stp.setStudent(student);
                    stp.setSemester(currentSemester);
                    stp.setTotalScore(0);
                    stp.setStatus("DRAFT");
                    return studentTrainingPointRepository.save(stp);
                });
    }

    /**
     * Add or update score for a specific criteria
     * Score behavior depends on source type:
     * - POINT_REQUEST, MANUAL, AUTO_GPA: REPLACE (self-declared, only latest counts)
     * - AUTO_ACTIVITY: ACCUMULATE (can have multiple activities)
     */
    @Transactional
    public void addOrUpdateScore(User student, String criteriaCode, Integer score, 
                                  String sourceType, Long referenceId, String description) {
        StudentTrainingPoint stp = getOrCreateForCurrentSemester(student);
        
        // Check if detail already exists
        Optional<TrainingPointDetail> existingDetail = 
                trainingPointDetailRepository.findByStudentTrainingPointAndCriteriaCode(stp, criteriaCode);
        
        // Determine if this source type should REPLACE or ACCUMULATE
        boolean shouldReplace = sourceType != null && 
                (sourceType.equals("POINT_REQUEST") || sourceType.equals("MANUAL") || sourceType.equals("AUTO_GPA"));
        
        TrainingPointDetail detail;
        if (existingDetail.isPresent()) {
            detail = existingDetail.get();
            if (shouldReplace) {
                // REPLACE score (self-declared, only latest counts)
                detail.setScore(score);
                detail.setDescription(description);
            } else {
                // ACCUMULATE score (activities can stack)
                detail.setScore(detail.getScore() + score);
                // Append description
                String newDesc = detail.getDescription() != null ? detail.getDescription() + "; " + description : description;
                detail.setDescription(newDesc);
            }
            detail.setSourceType(sourceType);
            detail.setReferenceId(referenceId);
        } else {
            detail = new TrainingPointDetail();
            detail.setStudentTrainingPoint(stp);
            detail.setCriteriaCode(criteriaCode);
            detail.setScore(score);
            detail.setSourceType(sourceType);
            detail.setReferenceId(referenceId);
            detail.setDescription(description);
        }
        
        trainingPointDetailRepository.save(detail);
        
        // Recalculate total
        recalculateTotal(stp);
    }

    /**
     * Recalculate total score and classification
     */
    @Transactional
    public void recalculateTotal(StudentTrainingPoint stp) {
        List<TrainingPointDetail> details = trainingPointDetailRepository.findByStudentTrainingPoint(stp);
        
        int total = details.stream()
                .mapToInt(TrainingPointDetail::getScore)
                .sum();
        
        // Normalize to 0-100
        total = scoringRulesService.normalizeScore(total);
        
        stp.setTotalScore(total);
        stp.setClassification(scoringRulesService.getClassification(total));
        
        studentTrainingPointRepository.save(stp);
    }

    /**
     * Get score details by category
     * Returns Map<categoryId, Map<subcategoryCode, score>>
     */
    public Map<String, Integer> getScoresByCriteria(User student) {
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester == null) {
            return new HashMap<>();
        }
        
        Optional<StudentTrainingPoint> stpOpt = 
                studentTrainingPointRepository.findByStudentAndSemester(student, currentSemester);
        
        if (stpOpt.isEmpty()) {
            return new HashMap<>();
        }
        
        List<TrainingPointDetail> details = 
                trainingPointDetailRepository.findByStudentTrainingPoint(stpOpt.get());
        
        Map<String, Integer> scores = new HashMap<>();
        for (TrainingPointDetail detail : details) {
            scores.put(detail.getCriteriaCode(), detail.getScore());
        }
        
        return scores;
    }

    /**
     * Get total score for student in current semester
     */
    public int getTotalScore(User student) {
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester == null) {
            return 0;
        }
        
        return studentTrainingPointRepository.findByStudentAndSemester(student, currentSemester)
                .map(StudentTrainingPoint::getTotalScore)
                .orElse(0);
    }

    /**
     * Get classification for student in current semester
     */
    public String getClassification(User student) {
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester == null) {
            return "Chưa xếp loại";
        }
        
        return studentTrainingPointRepository.findByStudentAndSemester(student, currentSemester)
                .map(stp -> stp.getClassification() != null ? stp.getClassification() : "Chưa xếp loại")
                .orElse("Chưa xếp loại");
    }

    /**
     * Get category totals (sum of subcategories in each main category)
     */
    public Map<Integer, Integer> getCategoryTotals(User student) {
        Map<String, Integer> scores = getScoresByCriteria(student);
        Map<Integer, Integer> categoryTotals = new HashMap<>();
        
        // Initialize
        for (int i = 1; i <= 6; i++) {
            categoryTotals.put(i, 0);
        }
        
        // Sum by category
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String code = entry.getKey();
            int categoryId = getCategoryFromCode(code);
            if (categoryId > 0 && categoryId <= 6) {
                categoryTotals.merge(categoryId, entry.getValue(), Integer::sum);
            }
        }
        
        return categoryTotals;
    }

    private int getCategoryFromCode(String code) {
        if (code == null || code.isEmpty()) return 0;
        try {
            return Integer.parseInt(code.substring(0, 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
