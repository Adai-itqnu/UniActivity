package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.DynamicQrTokenService;
import com.example.uniactivity.service.NotificationService;
import com.example.uniactivity.service.SseEmitterService;
import com.example.uniactivity.service.StudentCheckinService;
import com.example.uniactivity.service.EvidenceSubmissionService;
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
    private final StudentCheckinService studentCheckinService;
    private final EvidenceSubmissionService evidenceSubmissionService;

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
            model.addAttribute("error", "Không thể tải thông tin hoạt động");
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
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            ActivityRegistration registration = studentCheckinService.checkIn(
                    currentUser, activityId, classId, token, lat, lng, accuracy);
            Activity activity = registration.getActivity();

            StudentClass studentClass = currentUser.getStudentClass();
            if (studentClass != null) {
                userRepository.findByStudentClassAndRole(studentClass, Role.MANAGER)
                        .forEach(manager -> notificationService.notifyStudentCheckedIn(
                                manager, currentUser.getFullName(), activity.getName()));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Check-in thành công! Cảm ơn bạn đã tham gia.",
                    "activityName", activity.getName()
            ));
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Không thể check-in, vui lòng thử lại"));
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
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
        EvidenceSubmissionService.EvidenceSubmissionResult result =
                evidenceSubmissionService.submit(
                        currentUser, activityId, scoreOptionId, files);
        Activity activity = result.registration().getActivity();

        try {
            StudentClass studentClass = currentUser.getStudentClass();
            if (studentClass != null) {
                List<User> managers = userRepository.findByStudentClassAndRole(
                        studentClass, Role.MANAGER);
                managers.forEach(manager -> notificationService.notifyEvidenceSubmitted(
                        manager, currentUser.getFullName(), activity.getName()));
                Set<Long> managerIds = new HashSet<>();
                managers.forEach(manager -> managerIds.add(manager.getId()));
                if (!managerIds.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("activityId", activityId);
                    payload.put("activityName", activity.getName());
                    payload.put("studentName", currentUser.getFullName());
                    payload.put("action", "evidence_submitted");
                    sseEmitterService.sendToUsers(
                            managerIds, "activity_registration_update", payload);
                }
            }
        } catch (Exception ignored) {
            // Evidence is already stored; notification is best-effort.
        }

        return ResponseEntity.ok(Map.of(
                "message", "Đã nộp " + result.paths().size()
                        + " ảnh minh chứng! Vui lòng chờ xác nhận từ quản lý lớp."));
    }
}
