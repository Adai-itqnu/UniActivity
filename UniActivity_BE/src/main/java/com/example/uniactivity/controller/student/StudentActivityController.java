package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
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
    private final UserRepository userRepository;

    @GetMapping("/activities")
    public String activities(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        // Fetch fresh user data from database
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
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
        // Fetch fresh user data from database
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
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
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
        Map<String, Object> result =
                activityService.registerStudentForActivity(currentUser, activityId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/activities/{activityId}/register")
    @ResponseBody
    public ResponseEntity<?> cancelRegistration(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
        Map<String, Object> result =
                activityService.cancelStudentRegistration(currentUser, activityId);
        return ResponseEntity.ok(result);
    }

}
