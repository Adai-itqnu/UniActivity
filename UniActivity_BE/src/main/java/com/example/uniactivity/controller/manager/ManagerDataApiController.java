package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.*;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final SemesterRepository semesterRepository;
    private final ScoringRulesService scoringRulesService;
    private final FileUploadService fileUploadService;
    private final StudentCheckinService studentCheckinService;
    private final ManagerScopeAuthorizationService managerScopeAuthorizationService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
    public ResponseEntity<?> getUserScores(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        User user = managerScopeAuthorizationService.requireStudent(userDetails.getUser(), id);

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

    // ===================== MANAGER AS STUDENT SCORES =====================

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

    // ===================== MANAGER AS STUDENT REGISTRATIONS =====================

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

    // ===================== MANAGER MANAGEMENT ACTIVITIES =====================

    @GetMapping("/activities")
    public ResponseEntity<?> getActivities(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());
        if (currentUser.getStudentClass() == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(activityService.getVisibleActivitiesForStudent(currentUser));
    }

    // ===================== MANAGER AS STUDENT ACTIVITIES =====================

    @GetMapping("/my-activities")
    public ResponseEntity<?> getMyActivities(
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

        // Registered activity IDs - chỉ tính những đăng ký còn hiệu lực (không phải CANCELLED)
        Set<Long> registeredIds = new HashSet<>();
        for (var reg : activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser)) {
            if (reg.getStatus() != RegistrationStatus.CANCELLED) {
                registeredIds.add(reg.getActivity().getId());
            }
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

    // ===================== MANAGER AS STUDENT ACTIVITY REGISTRATION =====================

    @PostMapping("/activities/{activityId}/register")
    public ResponseEntity<?> registerActivity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        try {
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            
            Map<String, Object> result = activityService.registerStudentForActivity(currentUser, activityId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/activities/{activityId}/register")
    public ResponseEntity<?> cancelRegistration(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long activityId) {
        try {
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            
            Map<String, Object> result = activityService.cancelStudentRegistration(currentUser, activityId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ===================== MANAGER AS STUDENT CHECKIN & EVIDENCE =====================

    @PostMapping("/checkin/{activityId}")
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
            return ResponseEntity.ok(Map.of(
                    "message", "Check-in thành công! Cảm ơn bạn đã tham gia.",
                    "activityName", registration.getActivity().getName()
            ));
        } catch (com.example.uniactivity.exception.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Không thể check-in, vui lòng thử lại"));
        }
    }
    @GetMapping("/activities/{activityId}/score-options")
    public ResponseEntity<?> getScoreOptionsForActivity(@PathVariable Long activityId) {
        try {
            return ResponseEntity.ok(activityService.getScoreOptionsByActivity(activityId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Collections.emptyList());
        }
    }

    @PostMapping("/activities/{activityId}/evidence")
    public ResponseEntity<?> uploadEvidence(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @PathVariable Long activityId,
                                             @RequestParam("scoreOptionId") Long scoreOptionId,
                                             @RequestParam("files") List<MultipartFile> files) {
        try {
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

            var scoreOption = activityService.findScoreOptionById(scoreOptionId);
            if (scoreOption == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mục điểm không hợp lệ"));
            }
            reg.setScoreOption(scoreOption);
            
            List<String> uploadedUrls = new ArrayList<>();
            String basePath = System.getProperty("user.dir");
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(basePath, "src", "main", "resources", "uploads", "evidence");
            java.nio.file.Files.createDirectories(uploadPath);
            
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String originalName = file.getOriginalFilename();
                if (originalName == null) originalName = "file.jpg";
                String extension = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = originalName.substring(dotIndex);
                }
                String fileName = UUID.randomUUID().toString().substring(0, 8) + extension;
                java.nio.file.Path filePath = uploadPath.resolve(fileName);
                java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                uploadedUrls.add("/uploads/evidence/" + fileName);
            }
            
            reg.setEvidenceUrl(String.join(",", uploadedUrls));
            reg.setIsApproved(null); // Pending approval
            reg.setRejectionReason(null);
            activityRegistrationRepository.save(reg);
            
            return ResponseEntity.ok(Map.of(
                "message", "Đã nộp " + uploadedUrls.size() + " ảnh minh chứng! Vui lòng chờ xác nhận từ quản lý lớp."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ===================== MANAGER AS STUDENT MANUAL POINT REQUESTS =====================

    @PostMapping("/calculate-gpa-score")
    public ResponseEntity<?> calculateGpaScore(@RequestBody Map<String, Double> body) {
        try {
            Double currentGpa = body.get("currentGpa");
            Double previousGpa = body.get("previousGpa");
            if (currentGpa == null || previousGpa == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập cả ĐTB hiện tại và kỳ trước"));
            }
            int score = scoringRulesService.calculateAcademicScore(currentGpa, previousGpa);
            return ResponseEntity.ok(Map.of(
                    "score", score,
                    "currentGpa", currentGpa,
                    "previousGpa", previousGpa
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/save-gpa-score")
    public ResponseEntity<?> saveGpaScore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Double> body) {
        try {
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            Double currentGpa = body.get("currentGpa");
            Double previousGpa = body.get("previousGpa");
            if (currentGpa == null || previousGpa == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập cả ĐTB hiện tại và kỳ trước"));
            }
            int score = scoringRulesService.calculateAcademicScore(currentGpa, previousGpa);
            trainingPointService.addOrUpdateScore(
                    currentUser, "1.1", score, "AUTO_GPA", null,
                    String.format("ĐTB kỳ này: %.2f, ĐTB kỳ trước: %.2f", currentGpa, previousGpa)
            );
            return ResponseEntity.ok(Map.of(
                    "message", "Đã lưu điểm mục 1.1 (Kết quả học tập)",
                    "score", score
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/upload-evidence")
    public ResponseEntity<?> uploadEvidence(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("files") MultipartFile[] files) {
        try {
            for (MultipartFile file : files) {
                if (!fileUploadService.isValidImage(file)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file ảnh (jpg, png, gif, webp)"));
                }
                if (file.getSize() > fileUploadService.getMaxFileSize()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "File không được vượt quá 5MB"));
                }
            }
            List<String> uploadedPaths = fileUploadService.uploadEvidenceImages(files);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã tải lên " + uploadedPaths.size() + " ảnh",
                    "paths", uploadedPaths
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi khi tải ảnh: " + e.getMessage()));
        }
    }

    @PostMapping("/point-requests")
    public ResponseEntity<?> submitPointRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body) {
        try {
            User currentUser = userRepository.findById(userDetails.getUser().getId())
                    .orElse(userDetails.getUser());
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn cần tham gia lớp trước khi nhập điểm"));
            }
            String criteriaCode = (String) body.get("criteriaCode");
            Integer claimedScore = body.get("claimedScore") != null ? 
                    ((Number) body.get("claimedScore")).intValue() : null;
            String description = (String) body.get("description");
            
            @SuppressWarnings("unchecked")
            List<String> evidenceImages = (List<String>) body.get("evidenceImages");
            String evidenceImageUrl = evidenceImages != null && !evidenceImages.isEmpty() 
                    ? String.join(",", evidenceImages) 
                    : (String) body.get("evidenceImageUrl");

            if (criteriaCode == null || description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng điền đầy đủ thông tin"));
            }
            if (scoringRulesService.requiresEvidence(criteriaCode) && 
                (evidenceImageUrl == null || evidenceImageUrl.trim().isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mục này yêu cầu minh chứng (ảnh)"));
            }
            PointRequest request = pointRequestService.createPointRequest(
                    currentUser, criteriaCode, claimedScore, description.trim(), evidenceImageUrl);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã gửi yêu cầu điểm mục " + request.getCriteriaCode(),
                    "id", request.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/scoring-rules/{categoryCode}")
    public ResponseEntity<?> getScoringRulesHtml(@PathVariable String categoryCode) {
        String rulesHtml = scoringRulesService.getRulesHtml(categoryCode);
        return ResponseEntity.ok(Map.of(
                "rulesHtml", rulesHtml,
                "requiresEvidence", scoringRulesService.requiresEvidence(categoryCode),
                "defaultScore", scoringRulesService.getDefaultScore(categoryCode)
        ));
    }
}
