package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.DynamicQrTokenService;
import com.example.uniactivity.service.NotificationService;
import com.example.uniactivity.service.SseEmitterService;
import com.example.uniactivity.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Controller for student QR check-in and evidence upload
 */
@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentCheckinController {

    private final ActivityService activityService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DynamicQrTokenService dynamicQrTokenService;
    private final SseEmitterService sseEmitterService;

    @GetMapping("/checkin/{activityId}")
    public String checkinPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long activityId,
                               @RequestParam(required = false) String token,
                               @RequestParam(required = false) Long classId,
                               Model model) {
        // Fetch fresh user data from database
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
        model.addAttribute("user", currentUser);
        model.addAttribute("activityId", activityId);
        model.addAttribute("classId", classId);
        
        try {
            Activity activity = activityService.findActivityById(activityId);
            model.addAttribute("activity", activity);
            
            // Check if student belongs to the same class as the QR creator
            if (classId != null && currentUser.getStudentClass() != null) {
                if (!currentUser.getStudentClass().getId().equals(classId)) {
                    model.addAttribute("error", "Mã QR này chỉ dành cho lớp khác. Vui lòng sử dụng mã QR của lớp bạn.");
                    model.addAttribute("canCheckin", false);
                    return "student/checkin";
                }
            }
            
            // Check if already registered
            var registration = activityRegistrationRepository.findByActivityAndStudent(activity, currentUser);
            
            if (registration.isEmpty()) {
                model.addAttribute("error", "Bạn chưa đăng ký hoạt động này. Vui lòng đăng ký trước khi check-in.");
                model.addAttribute("canCheckin", false);
            } else if (registration.get().getStatus() == RegistrationStatus.ATTENDED) {
                model.addAttribute("success", "Bạn đã check-in thành công trước đó!");
                model.addAttribute("canCheckin", false);
            } else {
                model.addAttribute("canCheckin", true);
                model.addAttribute("registration", registration.get());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Không tìm thấy hoạt động: " + e.getMessage());
            model.addAttribute("canCheckin", false);
        }
        
        return "student/checkin";
    }

    @PostMapping("/api/checkin/{activityId}")
    @ResponseBody
    public ResponseEntity<?> performCheckin(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @PathVariable Long activityId,
                                             @RequestParam(required = false) Long classId,
                                             @RequestParam(required = false) String token,
                                             @RequestParam(required = false) Double lat,
                                             @RequestParam(required = false) Double lng,
                                             @RequestParam(required = false) Double accuracy) {
        try {
            // Fetch fresh user data from database
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            Activity activity = activityService.findActivityById(activityId);
            
            // Validate classId if provided (from QR code)
            if (classId != null && currentUser.getStudentClass() != null) {
                if (!currentUser.getStudentClass().getId().equals(classId)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Mã QR này chỉ dành cho lớp khác. Vui lòng sử dụng mã QR của lớp bạn."));
                }
            }

            // ✅ Validate Dynamic QR Token — chặn gửi QR từ xa
            if (classId != null && token != null && !token.isBlank()) {
                if (!dynamicQrTokenService.validateToken(token, activityId, classId)) {
                    return ResponseEntity.status(403).body(Map.of(
                        "message", "Mã QR đã hết hạn. Vui lòng quét lại mã QR mới từ Manager.",
                        "expired", true
                    ));
                }
            } else if (classId != null) {
                // Khi có classId nhưng không có token → QR cũ hoặc link thủ công
                // Cho phép trong giai đoạn chuyển đổi
            }

            // ✅ Validate GPS Location — kiểm tra vị trí sinh viên
            if (activity.getLatitude() != null && activity.getLongitude() != null
                    && activity.getCheckinRadius() != null && activity.getCheckinRadius() > 0) {
                // Hoạt động có GPS → bắt buộc sinh viên gửi vị trí
                if (lat == null || lng == null) {
                    return ResponseEntity.status(403).body(Map.of(
                        "message", "Hoạt động này yêu cầu xác minh vị trí. Vui lòng cấp quyền GPS để check-in.",
                        "gpsRequired", true
                    ));
                }

                // Kiểm tra độ chính xác GPS
                if (accuracy != null && accuracy > 150) {
                    return ResponseEntity.status(403).body(Map.of(
                        "message", String.format("Tín hiệu GPS không chính xác (sai số: %.0fm). Vui lòng ra khu vực thoáng hoặc bật Wi-Fi để tăng độ chính xác.", accuracy),
                        "gpsInaccurate", true
                    ));
                }

                // Tính khoảng cách Haversine
                double distance = GeoUtils.haversineMeters(
                        activity.getLatitude(), activity.getLongitude(), lat, lng);
                int radius = activity.getCheckinRadius();

                if (distance > radius) {
                    return ResponseEntity.status(403).body(Map.of(
                        "message", String.format("Bạn cách địa điểm hoạt động khoảng %.0fm. Chỉ được check-in trong phạm vi %dm.", distance, radius),
                        "tooFar", true,
                        "distance", Math.round(distance),
                        "radius", radius
                    ));
                }
            }
            
            var registration = activityRegistrationRepository.findByActivityAndStudent(activity, currentUser);
            
            if (registration.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa đăng ký hoạt động này"));
            }
            
            ActivityRegistration reg = registration.get();
            
            if (reg.getStatus() == RegistrationStatus.ATTENDED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn đã check-in rồi"));
            }
            
            if (reg.getStatus() == RegistrationStatus.CANCELLED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Đăng ký đã bị hủy, không thể check-in"));
            }
            
            // Mark as attended
            reg.setStatus(RegistrationStatus.ATTENDED);
            activityRegistrationRepository.save(reg);

            // Notify manager about check-in
            try {
                StudentClass studentClass = currentUser.getStudentClass();
                if (studentClass != null) {
                    userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER)
                        .forEach(manager -> notificationService.notifyStudentCheckedIn(
                            manager, currentUser.getFullName(), activity.getName()));
                }
            } catch (Exception ignored) {}
            
            return ResponseEntity.ok(Map.of(
                "message", "Check-in thành công! Cảm ơn bạn đã tham gia.",
                "activityName", activity.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Get score options for an activity (for student evidence upload)
    @GetMapping("/api/activities/{activityId}/score-options")
    @ResponseBody
    public ResponseEntity<?> getScoreOptionsForActivity(@PathVariable Long activityId) {
        try {
            return ResponseEntity.ok(activityService.getScoreOptionsByActivity(activityId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Collections.emptyList());
        }
    }

    @PostMapping("/api/activities/{activityId}/evidence")
    @ResponseBody
    public ResponseEntity<?> uploadEvidence(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @PathVariable Long activityId,
                                             @RequestParam("scoreOptionId") Long scoreOptionId,
                                             @RequestParam("files") List<MultipartFile> files) {
        try {
            // Fetch fresh user data from database
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            Activity activity = activityService.findActivityById(activityId);
            
            var registration = activityRegistrationRepository.findByActivityAndStudent(activity, currentUser);
            
            if (registration.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa đăng ký hoạt động này"));
            }
            
            ActivityRegistration reg = registration.get();
            
            if (reg.getStatus() != RegistrationStatus.ATTENDED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn cần check-in trước khi nộp minh chứng"));
            }

            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn ít nhất 1 ảnh"));
            }

            if (files.size() > 3) {
                return ResponseEntity.badRequest().body(Map.of("message", "Tối đa 3 ảnh"));
            }

            // Find and set the selected score option
            var scoreOption = activityService.findScoreOptionById(scoreOptionId);
            if (scoreOption == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mục điểm không hợp lệ"));
            }
            reg.setScoreOption(scoreOption);
            
            // Save files and collect URLs - save to resources/uploads/evidence
            List<String> uploadedUrls = new ArrayList<>();
            String basePath = System.getProperty("user.dir");
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(basePath, "src", "main", "resources", "uploads", "evidence");
            java.nio.file.Files.createDirectories(uploadPath);
            
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                
                // Sanitize filename
                String originalName = file.getOriginalFilename();
                if (originalName == null) originalName = "file.jpg";
                // Keep only extension
                String extension = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = originalName.substring(dotIndex);
                }
                
                String fileName = UUID.randomUUID().toString().substring(0, 8) + extension;
                java.nio.file.Path filePath = uploadPath.resolve(fileName);
                
                // Use Files.copy instead of transferTo
                java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                uploadedUrls.add("/uploads/evidence/" + fileName);
            }
            
            // Store URLs as comma-separated string
            reg.setEvidenceUrl(String.join(",", uploadedUrls));
            reg.setIsApproved(null); // Pending approval
            reg.setRejectionReason(null); // Clear any previous rejection reason
            activityRegistrationRepository.save(reg);

            // Notify manager about evidence submission + SSE real-time update
            try {
                StudentClass studentClass = currentUser.getStudentClass();
                if (studentClass != null) {
                    List<User> managers = userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER);
                    managers.forEach(manager -> notificationService.notifyEvidenceSubmitted(
                            manager, currentUser.getFullName(), activity.getName()));
                    
                    // Gửi SSE event để Manager ActivityDetail tự re-fetch
                    Set<Long> managerIds = new HashSet<>();
                    managers.forEach(m -> managerIds.add(m.getId()));
                    if (!managerIds.isEmpty()) {
                        Map<String, Object> ssePayload = new HashMap<>();
                        ssePayload.put("activityId", activityId);
                        ssePayload.put("activityName", activity.getName());
                        ssePayload.put("studentName", currentUser.getFullName());
                        ssePayload.put("action", "evidence_submitted");
                        sseEmitterService.sendToUsers(managerIds, "activity_registration_update", ssePayload);
                    }
                }
            } catch (Exception ignored) {}
            
            return ResponseEntity.ok(Map.of(
                "message", "Đã nộp " + uploadedUrls.size() + " ảnh minh chứng! Vui lòng chờ xác nhận từ quản lý lớp."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi tải file: " + e.getMessage()));
        }
    }
}
