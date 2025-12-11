package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Controller for student training points APIs (GPA, point requests, evidence)
 */
@RestController
@RequestMapping("/student/api")
@RequiredArgsConstructor
@Slf4j
public class StudentPointController {

    private final PointRequestService pointRequestService;
    private final ScoringRulesService scoringRulesService;
    private final FileUploadService fileUploadService;
    private final TrainingPointService trainingPointService;

    @PostMapping("/calculate-gpa-score")
    public ResponseEntity<?> calculateGpaScore(@RequestBody Map<String, Double> body) {
        try {
            Double currentGpa = body.get("currentGpa");
            Double previousGpa = body.get("previousGpa");
            
            if (currentGpa == null || previousGpa == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Vui lòng nhập cả ĐTB hiện tại và kỳ trước"));
            }
            
            int score = scoringRulesService.calculateAcademicScore(currentGpa, previousGpa);
            
            return ResponseEntity.ok(Map.of(
                    "score", score,
                    "currentGpa", currentGpa,
                    "previousGpa", previousGpa
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/save-gpa-score")
    public ResponseEntity<?> saveGpaScore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Double> body) {
        try {
            User currentUser = userDetails.getUser();
            Double currentGpa = body.get("currentGpa");
            Double previousGpa = body.get("previousGpa");
            
            if (currentGpa == null || previousGpa == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Vui lòng nhập cả ĐTB hiện tại và kỳ trước"));
            }
            
            int score = scoringRulesService.calculateAcademicScore(currentGpa, previousGpa);
            
            // Save GPA score directly (auto-approved for item 1.1)
            trainingPointService.addOrUpdateScore(
                    currentUser,
                    "1.1",
                    score,
                    "AUTO_GPA",
                    null,
                    String.format("ĐTB kỳ này: %.2f, ĐTB kỳ trước: %.2f", currentGpa, previousGpa)
            );
            
            return ResponseEntity.ok(Map.of(
                    "message", "Đã lưu điểm mục 1.1 (Kết quả học tập)",
                    "score", score
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/upload-evidence")
    public ResponseEntity<?> uploadEvidence(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("files") MultipartFile[] files) {
        try {
            // Validate files
            for (MultipartFile file : files) {
                if (!fileUploadService.isValidImage(file)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Chỉ chấp nhận file ảnh (jpg, png, gif, webp)"));
                }
                if (file.getSize() > fileUploadService.getMaxFileSize()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "File không được vượt quá 5MB"));
                }
            }
            
            List<String> uploadedPaths = fileUploadService.uploadEvidenceImages(files);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Đã tải lên " + uploadedPaths.size() + " ảnh",
                    "paths", uploadedPaths
            ));
        } catch (IOException e) {
            log.error("Failed to upload evidence images", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Lỗi khi tải ảnh: " + e.getMessage()));
        }
    }

    @PostMapping("/point-requests")
    public ResponseEntity<?> submitPointRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        try {
            User currentUser = userDetails.getUser();
            
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Bạn cần tham gia lớp trước khi nhập điểm"));
            }

            String criteriaCode = (String) body.get("criteriaCode");
            Integer claimedScore = body.get("claimedScore") != null ? 
                    ((Number) body.get("claimedScore")).intValue() : null;
            String description = (String) body.get("description");
            
            // Handle multiple evidence images
            @SuppressWarnings("unchecked")
            List<String> evidenceImages = (List<String>) body.get("evidenceImages");
            String evidenceImageUrl = evidenceImages != null && !evidenceImages.isEmpty() 
                    ? String.join(",", evidenceImages) 
                    : (String) body.get("evidenceImageUrl");

            if (criteriaCode == null || description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Vui lòng điền đầy đủ thông tin"));
            }
            
            // Check if evidence is required
            if (scoringRulesService.requiresEvidence(criteriaCode) && 
                (evidenceImageUrl == null || evidenceImageUrl.trim().isEmpty())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Mục này yêu cầu minh chứng (ảnh)"));
            }

            PointRequest request = pointRequestService.createPointRequest(
                    currentUser, criteriaCode, claimedScore, description.trim(), evidenceImageUrl);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Đã gửi yêu cầu điểm mục " + request.getCriteriaCode(),
                    "id", request.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/scoring-rules/{categoryCode}")
    public ResponseEntity<?> getScoringRulesHtml(@PathVariable String categoryCode) {
        String rulesHtml = scoringRulesService.getRulesHtml(categoryCode);
        return ResponseEntity.ok(Map.of(
                "rulesHtml", rulesHtml,
                "requiresEvidence", scoringRulesService.requiresEvidence(categoryCode),
                "defaultScore", scoringRulesService.getDefaultScore(categoryCode)
        ));
    }
}
