package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.ActivitySlotRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller for student activities list and registration management
 */
@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentActivityController {

    private final ActivityService activityService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivitySlotRepository activitySlotRepository;

    @GetMapping("/activities")
    public String activities(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("hasClass", currentUser.getStudentClass() != null);
        
        if (currentUser.getStudentClass() == null) {
            model.addAttribute("activities", List.of());
            return "student/activities";
        }
        
        var activities = activityService.getVisibleActivitiesForStudent(currentUser);
        
        // Check registration status for each activity
        Set<Long> registeredActivityIds = new HashSet<>();
        for (var reg : activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser)) {
            registeredActivityIds.add(reg.getActivity().getId());
        }
        model.addAttribute("activities", activities);
        model.addAttribute("registeredActivityIds", registeredActivityIds);
        
        return "student/activities";
    }

    @GetMapping("/my-registrations")
    public String myRegistrations(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("hasClass", currentUser.getStudentClass() != null);
        
        List<ActivityRegistration> registrations = activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser);
        model.addAttribute("registrations", registrations);
        
        return "student/my-registrations";
    }

    @PostMapping("/api/activities/{activityId}/register")
    @ResponseBody
    public ResponseEntity<?> registerActivity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        try {
            User currentUser = userDetails.getUser();
            
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn phải tham gia lớp trước khi đăng ký hoạt động"));
            }
            
            Activity activity = activityService.findActivityById(activityId);
            
            // Check if already registered
            if (activityRegistrationRepository.existsByActivityAndStudent(activity, currentUser)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn đã đăng ký hoạt động này rồi"));
            }
            
            // Check visibility
            if (!activityService.isActivityVisibleToStudent(activity, currentUser)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn không thể đăng ký hoạt động này"));
            }
            
            // Find matching slot
            ActivitySlot slot = activityService.findMatchingSlotForStudent(activity, currentUser);
            if (slot == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy slot phù hợp"));
            }
            
            // Check slot capacity
            if (slot.getCurrentQuantity() >= slot.getMaxQuantity()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Slot đã đầy, không thể đăng ký"));
            }
            
            // Create registration
            ActivityRegistration reg = new ActivityRegistration();
            reg.setStudent(currentUser);
            reg.setActivity(activity);
            reg.setActivitySlot(slot);
            reg.setStatus(RegistrationStatus.REGISTERED);
            activityRegistrationRepository.save(reg);
            
            // Update slot count and save
            slot.setCurrentQuantity(slot.getCurrentQuantity() + 1);
            activitySlotRepository.save(slot);
            
            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công!", "registrationId", reg.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/activities/{activityId}/register")
    @ResponseBody
    public ResponseEntity<?> cancelRegistration(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        try {
            User currentUser = userDetails.getUser();
            Activity activity = activityService.findActivityById(activityId);
            
            var registration = activityRegistrationRepository.findByActivityAndStudent(activity, currentUser);
            if (registration.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa đăng ký hoạt động này"));
            }
            
            ActivityRegistration reg = registration.get();
            
            // Decrease slot count and save
            if (reg.getActivitySlot() != null) {
                ActivitySlot slot = reg.getActivitySlot();
                slot.setCurrentQuantity(Math.max(0, slot.getCurrentQuantity() - 1));
                activitySlotRepository.save(slot);
            }
            
            activityRegistrationRepository.delete(reg);
            
            return ResponseEntity.ok(Map.of("message", "Đã hủy đăng ký thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
