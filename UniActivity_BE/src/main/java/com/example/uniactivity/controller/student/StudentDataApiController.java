package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API endpoints cho React Student SPA:
 * - /student/api/my-class
 * - /student/api/my-registrations
 * - /student/api/my-scores
 * - /student/api/activities
 */
@RestController
@RequestMapping("/student/api")
@RequiredArgsConstructor
public class StudentDataApiController {

    private final UserRepository userRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityService activityService;
    private final TrainingPointService trainingPointService;
    private final PointRequestService pointRequestService;
    private final SemesterRepository semesterRepository;
    private final ScoringRulesService scoringRulesService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ===================== MY CLASS =====================

    @GetMapping("/my-class")
    public ResponseEntity<?> getMyClass(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String search) {

        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        Map<String, Object> response = new LinkedHashMap<>();
        boolean hasClass = currentUser.getStudentClass() != null;
        response.put("hasClass", hasClass);

        if (!hasClass) {
            return ResponseEntity.ok(response);
        }

        StudentClass sc = currentUser.getStudentClass();

        // Class info
        Map<String, Object> classInfo = new LinkedHashMap<>();
        classInfo.put("id", sc.getId());
        classInfo.put("name", sc.getName());
        classInfo.put("code", sc.getCode());
        classInfo.put("facultyName", sc.getFaculty() != null ? sc.getFaculty().getName() : null);
        classInfo.put("academicYearCode", sc.getAcademicYear() != null ? sc.getAcademicYear().getCode() : null);
        response.put("studentClass", classInfo);

        // Members
        List<User> members;
        if (search != null && !search.isBlank()) {
            members = userRepository.findByStudentClassAndFullNameContainingIgnoreCaseOrStudentClassAndUsernameContainingIgnoreCase(
                    sc, search, sc, search);
        } else {
            members = userRepository.findByStudentClass(sc);
        }

        List<Map<String, Object>> memberList = new ArrayList<>();
        for (User m : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("username", m.getUsername());
            item.put("fullName", m.getFullName());
            item.put("email", m.getEmail());
            item.put("role", m.getRole().name());
            item.put("avatarUrl", m.getAvatarUrl());
            memberList.add(item);
        }
        response.put("members", memberList);
        response.put("memberCount", userRepository.countByStudentClass(sc));

        return ResponseEntity.ok(response);
    }

    // ===================== MY REGISTRATIONS =====================

    @GetMapping("/my-registrations")
    public ResponseEntity<?> getMyRegistrations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        boolean hasClass = currentUser.getStudentClass() != null;
        List<ActivityRegistration> registrations = activityRegistrationRepository
                .findByStudentOrderByRegisteredAtDesc(currentUser);

        List<Map<String, Object>> regList = new ArrayList<>();
        for (ActivityRegistration reg : registrations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", reg.getId());

            // Activity info
            Activity a = reg.getActivity();
            Map<String, Object> actInfo = new LinkedHashMap<>();
            actInfo.put("id", a.getId());
            actInfo.put("name", a.getName());
            actInfo.put("location", a.getLocation());
            actInfo.put("startTime", a.getStartTime() != null ? a.getStartTime().format(DTF) : null);
            item.put("activity", actInfo);

            item.put("registeredAt", reg.getRegisteredAt() != null ? reg.getRegisteredAt().format(DTF) : null);
            item.put("status", reg.getStatus() != null ? reg.getStatus().name() : null);
            item.put("evidenceUrl", reg.getEvidenceUrl());
            item.put("isApproved", reg.getIsApproved());
            item.put("rejectionReason", reg.getRejectionReason());

            // Score option info
            if (reg.getScoreOption() != null) {
                Map<String, Object> soInfo = new LinkedHashMap<>();
                soInfo.put("id", reg.getScoreOption().getId());
                soInfo.put("name", reg.getScoreOption().getName());
                soInfo.put("scoreCategory", reg.getScoreOption().getScoreCategory());
                soInfo.put("scoreValue", reg.getScoreOption().getScoreValue());
                item.put("scoreOption", soInfo);
            }

            regList.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "hasClass", hasClass,
                "registrations", regList
        ));
    }

    // ===================== MY SCORES =====================

    @GetMapping("/my-scores")
    public ResponseEntity<?> getMyScores(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        Map<String, Object> response = new LinkedHashMap<>();
        boolean hasClass = currentUser.getStudentClass() != null;
        response.put("hasClass", hasClass);

        // User info
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("fullName", currentUser.getFullName());
        userInfo.put("username", currentUser.getUsername());
        if (currentUser.getStudentClass() != null) {
            userInfo.put("className", currentUser.getStudentClass().getName());
            userInfo.put("facultyName", currentUser.getStudentClass().getFaculty() != null
                    ? currentUser.getStudentClass().getFaculty().getName() : null);
        }
        response.put("user", userInfo);

        // Current semester
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        if (currentSemester != null) {
            response.put("currentSemester", Map.of(
                    "id", currentSemester.getId(),
                    "name", currentSemester.getName()
            ));
        }

        // Scores
        Map<String, Integer> scores = trainingPointService.getScoresByCriteria(currentUser);
        Map<Integer, Integer> categoryTotals = trainingPointService.getCategoryTotals(currentUser);
        int totalScore = trainingPointService.getTotalScore(currentUser);
        String classification = trainingPointService.getClassification(currentUser);

        response.put("scores", scores);
        response.put("categoryTotals", categoryTotals);
        response.put("totalScore", totalScore);
        response.put("classification", classification);

        // Approved activities (điểm từ hoạt động)
        List<ActivityRegistration> approvedActivities = activityRegistrationRepository
                .findByStudentOrderByRegisteredAtDesc(currentUser).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsApproved()))
                .collect(Collectors.toList());

        List<Map<String, Object>> approvedList = new ArrayList<>();
        for (ActivityRegistration reg : approvedActivities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityName", reg.getActivity().getName());
            if (reg.getScoreOption() != null) {
                item.put("scoreCategory", reg.getScoreOption().getScoreCategory());
                item.put("scoreValue", reg.getScoreOption().getScoreValue());
            }
            approvedList.add(item);
        }
        response.put("approvedActivities", approvedList);

        // Point requests
        List<PointRequest> myRequests = pointRequestService.getStudentPointRequests(currentUser);
        List<Map<String, Object>> requestList = new ArrayList<>();
        for (PointRequest req : myRequests) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", req.getId());
            item.put("criteriaCode", req.getCriteriaCode());
            item.put("claimedScore", req.getClaimedScore());
            item.put("description", req.getDescription());
            item.put("status", req.getStatus() != null ? req.getStatus().name() : null);
            item.put("reviewComment", req.getReviewComment());
            item.put("createdAt", req.getCreatedAt() != null ? req.getCreatedAt().format(DTF) : null);
            requestList.add(item);
        }
        response.put("myRequests", requestList);

        // Scoring rules (for the form)
        JsonNode scoringRules = scoringRulesService.getScoringRules();
        response.put("scoringRules", scoringRules);

        return ResponseEntity.ok(response);
    }

    // ===================== ACTIVITIES =====================

    @GetMapping("/activities")
    public ResponseEntity<?> getActivities(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        boolean hasClass = currentUser.getStudentClass() != null;
        if (!hasClass) {
            return ResponseEntity.ok(Map.of(
                    "hasClass", false,
                    "activities", List.of(),
                    "registeredActivityIds", List.of()
            ));
        }

        var activities = activityService.getVisibleActivitiesForStudent(currentUser).stream()
                .filter(a -> !"DRAFT".equals(a.getStatus()))
                .toList();

        // Registered activity IDs
        Set<Long> registeredIds = new HashSet<>();
        for (var reg : activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser)) {
            registeredIds.add(reg.getActivity().getId());
        }

        // Build activity list
        List<Map<String, Object>> activityList = new ArrayList<>();
        for (var a : activities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("name", a.getName());
            item.put("description", a.getDescription());
            item.put("bannerUrl", a.getBannerUrl());
            item.put("location", a.getLocation());
            item.put("startTime", a.getStartTime() != null ? a.getStartTime().format(DTF) : null);
            item.put("endTime", a.getEndTime() != null ? a.getEndTime().format(DTF) : null);
            item.put("registrationDeadline", a.getRegistrationDeadline() != null ? a.getRegistrationDeadline().format(DTF) : null);
            item.put("status", a.getStatus());
            item.put("maxSlots", a.getMaxSlots() != null ? a.getMaxSlots() : 0);
            item.put("registeredCount", a.getRegisteredCount() != null ? a.getRegisteredCount() : 0);
            item.put("isDeadlinePassed", Boolean.TRUE.equals(a.getIsDeadlinePassed()));
            item.put("isEnded", Boolean.TRUE.equals(a.getIsEnded()));
            item.put("isRegistered", registeredIds.contains(a.getId()));
            activityList.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "hasClass", true,
                "activities", activityList,
                "registeredActivityIds", registeredIds
        ));
    }
}
