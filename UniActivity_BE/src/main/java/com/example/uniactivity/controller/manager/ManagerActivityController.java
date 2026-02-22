package com.example.uniactivity.controller.manager;

import com.example.uniactivity.dto.activity.ActivityResponseDto;
import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.QrCodeService;
import com.example.uniactivity.service.TrainingPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing activities, QR codes, and registrations
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerActivityController {

    private final ActivityService activityService;
    private final QrCodeService qrCodeService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final TrainingPointService trainingPointService;

    @GetMapping("/activities")
    public String activities(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        // Pass classId for QR code generation - only students from this class can check-in
        if (currentUser.getStudentClass() != null) {
            model.addAttribute("classId", currentUser.getStudentClass().getId());
        }
        return "manager/activities";
    }

    @GetMapping("/activities/{activityId}")
    public String activityDetail(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @PathVariable Long activityId,
                                  Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        model.addAttribute("activityId", activityId);
        
        // Get activity info
        var activity = activityService.getActivityById(activityId);
        model.addAttribute("activity", activity);
        
        return "manager/activity-detail";
    }

    // ========== Manager Activities API ==========

    @GetMapping("/api/activities")
    @ResponseBody
    public List<ActivityResponseDto> getActivities(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        if (currentUser.getStudentClass() == null) {
            return List.of();
        }
        // Return activities visible to manager's class (CLASS or FACULTY scope)
        return activityService.getVisibleActivitiesForStudent(currentUser);
    }

    @GetMapping("/api/qrcode/{activityId}")
    @ResponseBody
    public ResponseEntity<byte[]> generateQRCode(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                  @PathVariable Long activityId) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().build();
            }
            
            Long classId = currentUser.getStudentClass().getId();
            
            // Build check-in URL with classId for validation
            String checkinUrl = String.format("/student/checkin/%d?classId=%d", activityId, classId);
            
            // Generate QR code using ZXing
            byte[] qrImage = qrCodeService.generateQrCodeBlack(checkinUrl);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                    .body(qrImage);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/activities/{activityId}/registrations")
    @ResponseBody
    public List<Map<String, Object>> getActivityRegistrations(@PathVariable Long activityId) {
        Activity activity = activityService.findActivityById(activityId);
        List<ActivityRegistration> registrations = activityRegistrationRepository.findByActivityOrderByRegisteredAtAsc(activity);
        
        return registrations.stream().map(reg -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", reg.getId());
            data.put("studentId", reg.getStudent().getId());
            data.put("studentName", reg.getStudent().getFullName());
            data.put("studentCode", reg.getStudent().getUsername());
            data.put("status", reg.getStatus().name());
            data.put("registeredAt", reg.getRegisteredAt());
            data.put("evidenceUrl", reg.getEvidenceUrl());
            data.put("isApproved", reg.getIsApproved());
            return data;
        }).toList();
    }

    @PostMapping("/api/registrations/{registrationId}/approve")
    @ResponseBody
    public ResponseEntity<?> approveRegistration(@PathVariable Long registrationId) {
        try {
            ActivityRegistration reg = activityRegistrationRepository.findById(registrationId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đăng ký"));
            
            reg.setIsApproved(true);
            activityRegistrationRepository.save(reg);
            
            // Add points to student training points
            Activity activity = reg.getActivity();
            User student = reg.getStudent();
            
            String criteriaCode;
            Integer score;
            String description;
            
            // Get score from activity's slot/score option, or use default
            if (reg.getScoreOption() != null) {
                criteriaCode = reg.getScoreOption().getScoreCategory();
                score = reg.getScoreOption().getScoreValue();
                description = activity.getName() + " - " + reg.getScoreOption().getName();
            } else {
                // Default: category 3.1 (Hoạt động CT-XH), 5 points
                criteriaCode = "3.1";
                score = 5;
                description = "Tham gia hoạt động: " + activity.getName();
            }
            
            trainingPointService.addOrUpdateScore(student, criteriaCode, score, 
                    "AUTO_ACTIVITY", activity.getId(), description);
            
            return ResponseEntity.ok(Map.of("message", "Đã duyệt và cộng " + score + " điểm thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/registrations/{registrationId}/reject")
    @ResponseBody
    public ResponseEntity<?> rejectRegistration(@PathVariable Long registrationId,
                                                 @RequestBody Map<String, String> body) {
        try {
            String reason = body.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập lý do từ chối"));
            }
            
            ActivityRegistration reg = activityRegistrationRepository.findById(registrationId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đăng ký"));
            
            reg.setIsApproved(false);
            reg.setRejectionReason(reason.trim());
            activityRegistrationRepository.save(reg);
            
            return ResponseEntity.ok(Map.of("message", "Đã từ chối minh chứng"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
