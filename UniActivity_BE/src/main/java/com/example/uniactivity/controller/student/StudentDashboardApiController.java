package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API cho React Student Dashboard
 * Trả về JSON thay vì Thymeleaf template
 */
@RestController
@RequestMapping("/student/api")
@RequiredArgsConstructor
public class StudentDashboardApiController {

    private final UserRepository userRepository;
    private final ClassJoinRequestService classJoinRequestService;
    private final TrainingPointService trainingPointService;
    private final ActivityService activityService;
    private final ActivityRegistrationRepository activityRegistrationRepository;

    /**
     * API tổng hợp dữ liệu dashboard cho Student
     * GET /student/api/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // Fetch fresh user data
        User currentUser = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        Map<String, Object> response = new LinkedHashMap<>();

        // ===== User Info =====
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", currentUser.getId());
        userInfo.put("fullName", currentUser.getFullName());
        userInfo.put("email", currentUser.getEmail());
        userInfo.put("avatarUrl", currentUser.getAvatarUrl());
        response.put("user", userInfo);

        // ===== Class Info =====
        boolean hasClass = currentUser.getStudentClass() != null;
        response.put("hasClass", hasClass);

        if (hasClass) {
            StudentClass sc = currentUser.getStudentClass();
            Map<String, Object> classInfo = new LinkedHashMap<>();
            classInfo.put("id", sc.getId());
            classInfo.put("name", sc.getName());
            classInfo.put("code", sc.getCode());
            classInfo.put("facultyName", sc.getFaculty() != null ? sc.getFaculty().getName() : null);
            classInfo.put("memberCount", userRepository.countByStudentClass(sc));
            response.put("studentClass", classInfo);
        } else {
            response.put("studentClass", null);

            // Check pending join request
            ClassJoinRequest pendingRequest = classJoinRequestService.getPendingRequestForUser(currentUser);
            response.put("hasPendingRequest", pendingRequest != null);
            if (pendingRequest != null) {
                response.put("pendingClassName", pendingRequest.getStudentClass().getName());
            }
        }

        // ===== Training Points =====
        if (hasClass) {
            Map<String, Object> trainingPoints = new LinkedHashMap<>();
            int totalScore = trainingPointService.getTotalScore(currentUser);
            String classification = trainingPointService.getClassification(currentUser);
            Map<Integer, Integer> categoryTotals = trainingPointService.getCategoryTotals(currentUser);

            trainingPoints.put("totalScore", totalScore);
            trainingPoints.put("classification", classification);

            // Chuyển categoryTotals thành danh sách dễ sử dụng cho frontend
            List<Map<String, Object>> categories = new ArrayList<>();
            String[] categoryNames = {"", "Ý thức học tập", "Ý thức chấp hành", "Hoạt động CT-XH", "Quan hệ cộng đồng", "Phẩm chất công dân", "Thành tích đặc biệt"};
            String[] categoryColors = {"", "#3b82f6", "#10b981", "#06b6d4", "#f59e0b", "#8b5cf6", "#ef4444"};
            for (int i = 1; i <= 6; i++) {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("id", i);
                cat.put("name", categoryNames[i]);
                cat.put("value", categoryTotals.getOrDefault(i, 0));
                cat.put("color", categoryColors[i]);
                categories.add(cat);
            }
            trainingPoints.put("categories", categories);
            response.put("trainingPoints", trainingPoints);
        }

        // ===== Stats =====
        if (hasClass) {
            Map<String, Object> stats = new LinkedHashMap<>();

            // Tổng hoạt động đã tham gia (ATTENDED)
            List<ActivityRegistration> registrations = activityRegistrationRepository
                    .findByStudentOrderByRegisteredAtDesc(currentUser);
            long attendedCount = registrations.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equals("ATTENDED"))
                    .count();
            stats.put("eventsAttended", attendedCount);
            stats.put("totalRegistrations", registrations.size());

            // Đang chờ (REGISTERED nhưng chưa ATTENDED)
            long pendingCount = registrations.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().name().equals("REGISTERED"))
                    .count();
            stats.put("pendingRegistrations", pendingCount);

            stats.put("totalScore", trainingPointService.getTotalScore(currentUser));
            stats.put("classification", trainingPointService.getClassification(currentUser));

            response.put("stats", stats);
        }

        // ===== Upcoming / HOT Activities =====
        if (hasClass) {
            var visibleActivities = activityService.getVisibleActivitiesForStudent(currentUser);

            // Upcoming: chưa hết hạn, chưa kết thúc
            var upcoming = visibleActivities.stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsDeadlinePassed()) && !Boolean.TRUE.equals(a.getIsEnded()))
                    .limit(6)
                    .toList();

            // Đã đăng ký IDs
            Set<Long> registeredIds = new HashSet<>();
            for (var reg : activityRegistrationRepository.findByStudentOrderByRegisteredAtDesc(currentUser)) {
                registeredIds.add(reg.getActivity().getId());
            }
            response.put("registeredActivityIds", registeredIds);

            // Build activity list
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            List<Map<String, Object>> activityList = new ArrayList<>();
            for (var a : upcoming) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId());
                item.put("name", a.getName());
                item.put("description", a.getDescription());
                item.put("location", a.getLocation());
                item.put("startTime", a.getStartTime() != null ? a.getStartTime().format(dtf) : null);
                item.put("endTime", a.getEndTime() != null ? a.getEndTime().format(dtf) : null);
                item.put("registrationDeadline", a.getRegistrationDeadline() != null ? a.getRegistrationDeadline().format(dtf) : null);
                item.put("bannerUrl", a.getBannerUrl());
                item.put("status", a.getStatus());
                item.put("isRegistered", registeredIds.contains(a.getId()));

                // Slot capacity & registered (already computed in DTO)
                item.put("maxSlots", a.getMaxSlots() != null ? a.getMaxSlots() : 0);
                item.put("registeredCount", a.getRegisteredCount() != null ? a.getRegisteredCount() : 0);

                activityList.add(item);
            }
            response.put("upcomingActivities", activityList);
        }

        return ResponseEntity.ok(response);
    }
}
