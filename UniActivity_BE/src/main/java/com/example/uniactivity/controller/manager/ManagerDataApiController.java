package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.ClassJoinRequestService;
import com.example.uniactivity.service.PointRequestService;
import com.example.uniactivity.service.TrainingPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API cung cấp dữ liệu cho Manager React frontend.
 * Bổ sung cho các controller hiện có (vốn chỉ render Thymeleaf).
 */
@RestController
@RequestMapping("/manager/api")
@RequiredArgsConstructor
public class ManagerDataApiController {

    private final UserRepository userRepository;
    private final ClassJoinRequestService classJoinRequestService;
    private final PointRequestService pointRequestService;
    private final ActivityService activityService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final TrainingPointService trainingPointService;

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboardStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        Map<String, Object> data = new LinkedHashMap<>();

        if (currentUser.getStudentClass() == null) {
            data.put("hasClass", false);
            return ResponseEntity.ok(data);
        }

        data.put("hasClass", true);

        // Class info
        var sc = currentUser.getStudentClass();
        Map<String, Object> classInfo = new LinkedHashMap<>();
        classInfo.put("id", sc.getId());
        classInfo.put("name", sc.getName());
        classInfo.put("code", sc.getCode());
        classInfo.put("joinCode", sc.getJoinCode());
        classInfo.put("facultyName", sc.getFaculty() != null ? sc.getFaculty().getName() : null);
        data.put("studentClass", classInfo);

        // Members
        List<User> members = userRepository.findByStudentClass(sc);
        data.put("memberCount", members.size());

        // Recent 5 members
        data.put("recentMembers", members.stream().limit(5).map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("fullName", m.getFullName());
            mm.put("username", m.getUsername());
            mm.put("email", m.getEmail());
            mm.put("avatarUrl", m.getAvatarUrl());
            mm.put("role", m.getRole().name());
            return mm;
        }).collect(Collectors.toList()));

        // Pending counts
        long pendingJoin = classJoinRequestService.getPendingRequestCount(sc);
        long pendingPoints = pointRequestService.getPendingRequestCount(sc);
        data.put("pendingJoinRequests", pendingJoin);
        data.put("pendingPointRequests", pendingPoints);

        // Active activities
        var activeActivities = activityService.getVisibleActivitiesForStudent(currentUser).stream()
                .filter(a -> "OPEN".equals(a.getStatus()))
                .collect(Collectors.toList());
        data.put("activeActivitiesCount", activeActivities.size());
        data.put("activeActivities", activeActivities.stream().limit(5).collect(Collectors.toList()));

        // Pending evidence — use optimized query instead of findAll()
        long pendingEvidences = activityRegistrationRepository.countPendingEvidence();
        data.put("pendingEvidences", pendingEvidences);

        // Average training points
        int totalPoints = 0;
        int studentCount = 0;
        for (User member : members) {
            if ("STUDENT".equals(member.getRole().name())) {
                totalPoints += trainingPointService.getTotalScore(member);
                studentCount++;
            }
        }
        data.put("avgTrainingPoints", studentCount > 0 ? totalPoints / studentCount : 0);

        return ResponseEntity.ok(data);
    }

    // ==================== MEMBERS ====================

    @GetMapping("/members")
    public ResponseEntity<?> getMembers(@AuthenticationPrincipal CustomUserDetails userDetails,
                                        @RequestParam(required = false) String search) {
        User currentUser = userDetails.getUser();
        if (currentUser.getStudentClass() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<User> members;
        if (search != null && !search.isBlank()) {
            members = userRepository.findByStudentClassAndFullNameContainingIgnoreCaseOrStudentClassAndUsernameContainingIgnoreCase(
                    currentUser.getStudentClass(), search, currentUser.getStudentClass(), search);
        } else {
            members = userRepository.findByStudentClass(currentUser.getStudentClass());
        }

        List<Map<String, Object>> result = members.stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("fullName", m.getFullName());
            mm.put("username", m.getUsername());
            mm.put("email", m.getEmail());
            mm.put("phone", m.getPhone());
            mm.put("role", m.getRole().name());
            mm.put("avatarUrl", m.getAvatarUrl());
            mm.put("createdAt", m.getCreatedAt());
            return mm;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ==================== JOIN REQUESTS ====================

    @GetMapping("/join-requests")
    public ResponseEntity<?> getJoinRequests(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        if (currentUser.getStudentClass() == null) {
            return ResponseEntity.ok(Map.of("requests", List.of(), "joinCode", ""));
        }

        List<ClassJoinRequest> pending = classJoinRequestService.getPendingRequestsForClass(currentUser.getStudentClass());
        List<Map<String, Object>> requests = pending.stream().map(r -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("userId", r.getUser().getId());
            rm.put("fullName", r.getUser().getFullName());
            rm.put("username", r.getUser().getUsername());
            rm.put("email", r.getUser().getEmail());
            rm.put("createdAt", r.getCreatedAt());
            return rm;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requests", requests);
        data.put("joinCode", currentUser.getStudentClass().getJoinCode());
        return ResponseEntity.ok(data);
    }

    // ==================== POINT REQUESTS ====================

    @GetMapping("/point-requests")
    public ResponseEntity<?> getPointRequests(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        if (currentUser.getStudentClass() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<PointRequest> pending = pointRequestService.getPendingRequestsForClass(currentUser.getStudentClass());
        List<Map<String, Object>> result = pending.stream().map(r -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("studentId", r.getStudent().getId());
            rm.put("studentName", r.getStudent().getFullName());
            rm.put("studentCode", r.getStudent().getUsername());
            rm.put("criteriaCode", r.getCriteriaCode());
            rm.put("claimedScore", r.getClaimedScore());
            rm.put("description", r.getDescription());
            rm.put("evidenceImageUrl", r.getEvidenceImageUrl());
            rm.put("status", r.getStatus().name());
            rm.put("createdAt", r.getCreatedAt());
            return rm;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ==================== SEARCH ====================

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("q") String query) {

        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        Map<String, Object> result = new LinkedHashMap<>();
        String q = query.toLowerCase().trim();

        // Search class members
        if (currentUser.getStudentClass() != null) {
            var members = userRepository.findByStudentClass(currentUser.getStudentClass()).stream()
                    .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(q))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                    .limit(8)
                    .map(u -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", u.getId());
                        item.put("fullName", u.getFullName());
                        item.put("username", u.getUsername());
                        item.put("email", u.getEmail());
                        item.put("role", u.getRole().name());
                        item.put("avatarUrl", u.getAvatarUrl());
                        item.put("status", u.getStatus().name());
                        item.put("className", u.getStudentClass() != null ? u.getStudentClass().getName() : null);
                        return item;
                    })
                    .collect(Collectors.toList());
            result.put("users", members);
        } else {
            result.put("users", List.of());
        }

        return ResponseEntity.ok(result);
    }

    // ==================== USER SCORES ====================

    @GetMapping("/users/{id}/scores")
    public ResponseEntity<?> getUserScores(@PathVariable Long id) {
        java.util.Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryTotals", trainingPointService.getCategoryTotals(user));
        data.put("totalScore", trainingPointService.getTotalScore(user));
        data.put("classification", trainingPointService.getClassification(user));

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("fullName", user.getFullName());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("role", user.getRole().name());
        userInfo.put("avatarUrl", user.getAvatarUrl());
        userInfo.put("status", user.getStatus().name());
        userInfo.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        if (user.getStudentClass() != null) {
            userInfo.put("className", user.getStudentClass().getName());
            userInfo.put("classCode", user.getStudentClass().getCode());
            userInfo.put("facultyName", user.getStudentClass().getFaculty() != null
                    ? user.getStudentClass().getFaculty().getName() : null);
        }
        data.put("user", userInfo);

        return ResponseEntity.ok(data);
    }
}
