package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for student home page, scores, and class info
 */
@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentHomeController {

    private final ClassJoinRequestService classJoinRequestService;
    private final PointRequestService pointRequestService;
    private final SemesterRepository semesterRepository;
    private final ScoringRulesService scoringRulesService;
    private final TrainingPointService trainingPointService;
    private final UserRepository userRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityService activityService;

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        model.addAttribute("hasClass", currentUser.getStudentClass() != null);
        
        // Check for pending request
        ClassJoinRequest pendingRequest = classJoinRequestService.getPendingRequestForUser(currentUser);
        model.addAttribute("hasPendingRequest", pendingRequest != null);
        if (pendingRequest != null) {
            model.addAttribute("pendingClassName", pendingRequest.getStudentClass().getName());
        }
        
        // If user has class, load additional data for dashboard
        if (currentUser.getStudentClass() != null) {
            // Training points data for chart
            int totalScore = trainingPointService.getTotalScore(currentUser);
            String classification = trainingPointService.getClassification(currentUser);
            java.util.Map<Integer, Integer> categoryTotals = trainingPointService.getCategoryTotals(currentUser);
            
            model.addAttribute("totalScore", totalScore);
            model.addAttribute("classification", classification);
            model.addAttribute("categoryTotals", categoryTotals);
            
            // Get upcoming/hot activities (limit 6)
            var upcomingActivities = activityService.getVisibleActivitiesForStudent(currentUser).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeadlinePassed()) && !Boolean.TRUE.equals(a.getIsEnded()))
                .limit(6)
                .toList();
            model.addAttribute("upcomingActivities", upcomingActivities);
            
            // Check registered activities
            java.util.Set<Long> registeredActivityIds = new java.util.HashSet<>();
            for (var reg : activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser)) {
                registeredActivityIds.add(reg.getActivity().getId());
            }
            model.addAttribute("registeredActivityIds", registeredActivityIds);
        }
        
        return "student/home";
    }

    @GetMapping("/my-scores")
    public String myScores(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        model.addAttribute("hasClass", currentUser.getStudentClass() != null);
        
        // Current semester
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        model.addAttribute("currentSemester", currentSemester);
        
        // Get user's point requests for current semester
        List<PointRequest> myRequests = pointRequestService.getStudentPointRequests(currentUser);
        model.addAttribute("myRequests", myRequests);
        
        // Get approved activity registrations (điểm từ hoạt động)
        List<ActivityRegistration> approvedActivities = activityRegistrationRepository
                .findByStudentOrderByRegisteredAtDesc(currentUser).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsApproved()))
                .collect(Collectors.toList());
        model.addAttribute("approvedActivities", approvedActivities);
        
        // Load real scores from TrainingPointService
        java.util.Map<String, Integer> scores = trainingPointService.getScoresByCriteria(currentUser);
        model.addAttribute("scores", scores);
        
        java.util.Map<Integer, Integer> categoryTotals = trainingPointService.getCategoryTotals(currentUser);
        model.addAttribute("categoryTotals", categoryTotals);
        
        int totalScore = trainingPointService.getTotalScore(currentUser);
        model.addAttribute("totalScore", totalScore);
        
        String classification = trainingPointService.getClassification(currentUser);
        model.addAttribute("classification", classification);
        
        // Pass scoring rules JSON to frontend
        JsonNode scoringRules = scoringRulesService.getScoringRules();
        model.addAttribute("scoringRulesJson", scoringRules.toString());
        
        return "student/my-scores";
    }

    @GetMapping("/my-class")
    public String myClass(@AuthenticationPrincipal CustomUserDetails userDetails, 
                          @RequestParam(required = false) String search,
                          Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("hasClass", currentUser.getStudentClass() != null);
        
        if (currentUser.getStudentClass() == null) {
            return "student/my-class";
        }
        
        StudentClass studentClass = currentUser.getStudentClass();
        model.addAttribute("studentClass", studentClass);
        
        // Get members
        List<User> members;
        if (search != null && !search.isBlank()) {
            members = userRepository.findByStudentClassAndFullNameContainingIgnoreCaseOrStudentClassAndUsernameContainingIgnoreCase(
                studentClass, search, studentClass, search);
            model.addAttribute("search", search);
        } else {
            members = userRepository.findByStudentClass(studentClass);
        }
        model.addAttribute("members", members);
        model.addAttribute("memberCount", userRepository.countByStudentClass(studentClass));
        
        return "student/my-class";
    }
}
