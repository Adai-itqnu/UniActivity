package com.example.uniactivity.controller.manager;

import com.example.uniactivity.dto.activity.ActivityResponseDto;
import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.NotificationType;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.DynamicQrTokenService;
import com.example.uniactivity.service.NotificationService;
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

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final DynamicQrTokenService dynamicQrTokenService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final TrainingPointService trainingPointService;
    private final NotificationService notificationService;

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

    /**
     * API trả QR image tĩnh (giữ backward-compatible, nhưng giờ embed dynamic token).
     */
    @GetMapping("/api/qrcode/{activityId}")
    @ResponseBody
    public ResponseEntity<byte[]> generateQRCode(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                  @PathVariable Long activityId,
                                                  HttpServletRequest request) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().build();
            }
            
            Long classId = currentUser.getStudentClass().getId();
            String token = dynamicQrTokenService.generateToken(activityId, classId);
            
            // Build full check-in URL với dynamic token
            String baseUrl = request.getScheme() + "://" + request.getServerName();
            int port = request.getServerPort();
            if ((request.getScheme().equals("http") && port != 80) ||
                (request.getScheme().equals("https") && port != 443)) {
                baseUrl += ":" + port;
            }
            String checkinUrl = String.format("%s/student/checkin/%d?classId=%d&token=%s",
                    baseUrl, activityId, classId, token);
            
            // Generate QR code using ZXing
            byte[] qrImage = qrCodeService.generateQrCodeBlack(checkinUrl);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(qrImage);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Dynamic QR API — trả JSON để frontend tự render QR + auto-refresh.
     * Response: { token, activityId, classId, expiresAt, secondsRemaining, interval, checkinUrl }
     */
    @GetMapping("/api/qrcode/dynamic/{activityId}")
    @ResponseBody
    public ResponseEntity<?> getDynamicQrToken(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                @PathVariable Long activityId,
                                                HttpServletRequest request) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Bạn chưa có lớp"));
            }

            Long classId = currentUser.getStudentClass().getId();
            String token = dynamicQrTokenService.generateToken(activityId, classId);

            // Build check-in URL
            String baseUrl = request.getScheme() + "://" + request.getServerName();
            int port = request.getServerPort();
            if ((request.getScheme().equals("http") && port != 80) ||
                (request.getScheme().equals("https") && port != 443)) {
                baseUrl += ":" + port;
            }
            String checkinUrl = String.format("%s/student/checkin/%d?classId=%d&token=%s",
                    baseUrl, activityId, classId, token);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("token", token);
            response.put("activityId", activityId);
            response.put("classId", classId);
            response.put("checkinUrl", checkinUrl);
            response.put("expiresAt", dynamicQrTokenService.getTokenExpiresAt());
            response.put("secondsRemaining", dynamicQrTokenService.getSecondsRemaining());
            response.put("interval", dynamicQrTokenService.getIntervalSeconds());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/activities/{activityId}/registrations")
    @ResponseBody
    public List<Map<String, Object>> getActivityRegistrations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        // Authorization: only manager of the same class can view
        User currentUser = userDetails.getUser();
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
            data.put("rejectionReason", reg.getRejectionReason());
            // Score option info
            if (reg.getScoreOption() != null) {
                Map<String, Object> so = new HashMap<>();
                so.put("id", reg.getScoreOption().getId());
                so.put("name", reg.getScoreOption().getName());
                so.put("scoreCategory", reg.getScoreOption().getScoreCategory());
                so.put("scoreValue", reg.getScoreOption().getScoreValue());
                data.put("scoreOption", so);
            }
            return data;
        }).toList();
    }

    @PostMapping("/api/registrations/{registrationId}/checkin")
    @ResponseBody
    public ResponseEntity<?> manualCheckin(@AuthenticationPrincipal CustomUserDetails userDetails,
                                           @PathVariable Long registrationId) {
        try {
            ActivityRegistration reg = activityRegistrationRepository.findById(registrationId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đăng ký"));
            
            if (reg.getStatus() == RegistrationStatus.ATTENDED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Sinh viên đã được điểm danh rồi"));
            }
            if (reg.getStatus() == RegistrationStatus.CANCELLED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Đăng ký đã bị hủy, không thể điểm danh"));
            }
            
            reg.setStatus(RegistrationStatus.ATTENDED);
            activityRegistrationRepository.save(reg);
            
            return ResponseEntity.ok(Map.of("message", "Đã điểm danh thành công cho " + reg.getStudent().getFullName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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
            
            // Send notification to student
            notificationService.create(
                student.getId(),
                NotificationType.EVIDENCE_APPROVED,
                "Minh chứng được duyệt",
                "Minh chứng hoạt động '" + activity.getName() + "' đã được duyệt. +" + score + " điểm",
                "/student/my-registrations"
            );
            
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
            
            // Send notification to student
            Activity activity = reg.getActivity();
            User student = reg.getStudent();
            String message = "Minh chứng hoạt động '" + activity.getName() + "' bị từ chối";
            if (reason != null && !reason.trim().isEmpty()) {
                message += ". Lý do: " + reason.trim();
            }
            notificationService.create(
                student.getId(),
                NotificationType.EVIDENCE_REJECTED,
                "Minh chứng bị từ chối",
                message,
                "/student/my-registrations"
            );
            
            return ResponseEntity.ok(Map.of("message", "Đã từ chối minh chứng"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
