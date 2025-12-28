package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ClassJoinRequestService;
import com.example.uniactivity.service.PointRequestService;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.TrainingPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for manager dashboard
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerHomeController {

    private final UserRepository userRepository;
    private final ClassJoinRequestService classJoinRequestService;
    private final PointRequestService pointRequestService;
    private final ActivityService activityService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final TrainingPointService trainingPointService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        
        if (currentUser.getStudentClass() != null) {
            // Get class members
            List<User> members = userRepository.findByStudentClass(currentUser.getStudentClass());
            model.addAttribute("memberCount", members.size());
            model.addAttribute("members", members.stream().limit(5).collect(Collectors.toList()));
            
            // Count pending join requests
            long pendingJoinRequests = classJoinRequestService.getPendingRequestCount(currentUser.getStudentClass());
            model.addAttribute("pendingJoinRequests", pendingJoinRequests);
            
            // Count pending point requests
            long pendingPointRequests = pointRequestService.getPendingRequestCount(currentUser.getStudentClass());
            model.addAttribute("pendingPointRequests", pendingPointRequests);
            
            // Get active activities (status = OPEN)
            var activeActivities = activityService.getVisibleActivitiesForStudent(currentUser).stream()
                .filter(a -> "OPEN".equals(a.getStatus()))
                .limit(5)
                .collect(Collectors.toList());
            model.addAttribute("activeActivities", activeActivities);
            model.addAttribute("activeActivitiesCount", activeActivities.size());
            
            // Count pending evidence approvals (minh chứng)
            long pendingEvidences = activityRegistrationRepository.findAll().stream()
                .filter(r -> r.getActivity() != null && 
                            r.getEvidenceUrl() != null && 
                            !r.getEvidenceUrl().isEmpty() &&
                            r.getIsApproved() == null)
                .count();
            model.addAttribute("pendingEvidences", pendingEvidences);
            
            // Calculate average training points for class
            int totalPoints = 0;
            int studentCount = 0;
            for (User member : members) {
                if ("STUDENT".equals(member.getRole().name())) {
                    totalPoints += trainingPointService.getTotalScore(member);
                    studentCount++;
                }
            }
            model.addAttribute("avgTrainingPoints", studentCount > 0 ? totalPoints / studentCount : 0);
        } else {
            model.addAttribute("memberCount", 0);
            model.addAttribute("pendingJoinRequests", 0);
            model.addAttribute("pendingPointRequests", 0);
            model.addAttribute("pendingEvidences", 0);
            model.addAttribute("activeActivitiesCount", 0);
            model.addAttribute("avgTrainingPoints", 0);
        }
        
        return "manager/dashboard";
    }
}
