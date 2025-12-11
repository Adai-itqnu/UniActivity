package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
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

    @GetMapping("/checkin/{activityId}")
    public String checkinPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long activityId,
                               @RequestParam(required = false) String token,
                               @RequestParam(required = false) Long classId,
                               Model model) {
        User currentUser = userDetails.getUser();
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
                                             @PathVariable Long activityId) {
        try {
            User currentUser = userDetails.getUser();
            Activity activity = activityService.findActivityById(activityId);
            
            var registration = activityRegistrationRepository.findByActivityAndStudent(activity, currentUser);
            
            if (registration.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa đăng ký hoạt động này"));
            }
            
            ActivityRegistration reg = registration.get();
            
            if (reg.getStatus() == RegistrationStatus.ATTENDED) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn đã check-in rồi"));
            }
            
            // Mark as attended
            reg.setStatus(RegistrationStatus.ATTENDED);
            activityRegistrationRepository.save(reg);
            
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
            User currentUser = userDetails.getUser();
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
            
            return ResponseEntity.ok(Map.of(
                "message", "Đã nộp " + uploadedUrls.size() + " ảnh minh chứng! Vui lòng chờ xác nhận từ quản lý lớp."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi tải file: " + e.getMessage()));
        }
    }
}
