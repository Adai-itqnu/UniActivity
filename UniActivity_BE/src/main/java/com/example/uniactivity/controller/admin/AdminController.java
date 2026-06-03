package com.example.uniactivity.controller.admin;

import com.example.uniactivity.entity.Notification;
import com.example.uniactivity.service.AcademicYearService;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.FacultyService;
import com.example.uniactivity.service.NotificationService;
import com.example.uniactivity.service.SemesterService;
import com.example.uniactivity.service.StudentClassService;
import com.example.uniactivity.service.TrainingPointService;
import com.example.uniactivity.service.UserManagementService;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.dto.activity.ActivityResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FacultyService facultyService;
    private final StudentClassService studentClassService;
    private final UserManagementService userManagementService;
    private final AcademicYearService academicYearService;
    private final ActivityService activityService;
    private final SemesterService semesterService;
    private final NotificationService notificationService;
    private final TrainingPointService trainingPointService;
    private final UserRepository userRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Main statistics cards
        model.addAttribute("totalFaculties", facultyService.countFaculties());
        model.addAttribute("totalClasses", studentClassService.countClasses());
        model.addAttribute("totalStudents", userManagementService.countStudents());
        model.addAttribute("totalUsers", userManagementService.countAllUsers());
        model.addAttribute("totalAcademicYears", academicYearService.countAcademicYears());
        model.addAttribute("totalActivities", activityService.countActivities());
        model.addAttribute("activeActivities", activityService.countActiveActivities());
        model.addAttribute("totalSemesters", semesterService.countSemesters());
        
        // Detail lists for breakdown view
        model.addAttribute("faculties", facultyService.getActiveFaculties());
        model.addAttribute("recentActivities", activityService.getRecentActivities(5));
        model.addAttribute("classes", studentClassService.getAllClasses());
        model.addAttribute("currentSemester", semesterService.getCurrentSemester());
        
        return "admin/dashboard";
    }

    // ===== REST API cho React Frontend =====
    @GetMapping("/api/dashboard-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalFaculties", facultyService.countFaculties());
        stats.put("totalClasses", studentClassService.countClasses());
        stats.put("totalStudents", userManagementService.countStudents());
        stats.put("totalUsers", userManagementService.countAllUsers());
        stats.put("totalAcademicYears", academicYearService.countAcademicYears());
        stats.put("totalActivities", activityService.countActivities());
        stats.put("activeActivities", activityService.countActiveActivities());
        stats.put("totalSemesters", semesterService.countSemesters());
        stats.put("recentActivities", activityService.getRecentActivities(5));
        
        // Sự kiện sắp tới: lấy hoạt động OPEN có startTime trong tương lai, sắp xếp gần nhất
        List<ActivityResponseDto> allActivities = activityService.getAllActivities();
        ActivityResponseDto upcomingEvent = allActivities.stream()
                .filter(a -> "OPEN".equals(a.getStatus()) && a.getStartTime() != null && a.getStartTime().isAfter(LocalDateTime.now()))
                .min(Comparator.comparing(ActivityResponseDto::getStartTime))
                .orElse(null);
        if (upcomingEvent != null) {
            Map<String, Object> upcoming = new LinkedHashMap<>();
            upcoming.put("id", upcomingEvent.getId());
            upcoming.put("name", upcomingEvent.getName());
            upcoming.put("description", upcomingEvent.getDescription());
            upcoming.put("startTime", upcomingEvent.getStartTime());
            upcoming.put("location", upcomingEvent.getLocation());
            stats.put("upcomingEvent", upcoming);
        }
        
        return ResponseEntity.ok(stats);
    }

    // API tìm kiếm hoạt động + người dùng
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchActivities(@RequestParam("q") String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        String q = query.toLowerCase().trim();

        // Search activities
        List<ActivityResponseDto> allActivities = activityService.getAllActivities();
        List<ActivityResponseDto> matchedActivities = allActivities.stream()
                .filter(a -> (a.getName() != null && a.getName().toLowerCase().contains(q))
                        || (a.getDescription() != null && a.getDescription().toLowerCase().contains(q))
                        || (a.getLocation() != null && a.getLocation().toLowerCase().contains(q)))
                .limit(5)
                .toList();
        result.put("activities", matchedActivities);

        // Search users
        var allUsers = userManagementService.getAllUsers();
        var matchedUsers = allUsers.stream()
                .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(q))
                        || (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                .limit(5)
                .toList();
        result.put("users", matchedUsers);

        return ResponseEntity.ok(result);
    }

    // API thông báo cho admin (chỉ unread — dùng cho dropdown header)
    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userDetails == null) {
            result.put("notifications", List.of());
            result.put("unreadCount", 0);
            return ResponseEntity.ok(result);
        }
        Long userId = userDetails.getUser().getId();
        List<Notification> notifications = notificationService.getUnreadNotifications(userId);
        long unreadCount = notificationService.getUnreadCount(userId);
        result.put("notifications", notifications);
        result.put("unreadCount", unreadCount);
        return ResponseEntity.ok(result);
    }

    // API lấy tất cả thông báo (có phân trang) — dùng cho trang thông báo
    @GetMapping("/api/notifications/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userDetails == null) {
            result.put("notifications", List.of());
            result.put("hasMore", false);
            result.put("unreadCount", 0);
            return ResponseEntity.ok(result);
        }
        Long userId = userDetails.getUser().getId();
        var notifPage = notificationService.getNotifications(userId, page, size);
        long unreadCount = notificationService.getUnreadCount(userId);
        result.put("notifications", notifPage.getContent());
        result.put("hasMore", notifPage.hasNext());
        result.put("unreadCount", unreadCount);
        return ResponseEntity.ok(result);
    }

    // API đánh dấu 1 thông báo đã đọc
    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userDetails != null) {
            notificationService.markAsRead(id, userDetails.getUser().getId());
        }
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    // API đánh dấu đã đọc tất cả thông báo
    @PostMapping("/api/notifications/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userDetails != null) {
            notificationService.markAllAsRead(userDetails.getUser().getId());
        }
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    // API lấy điểm rèn luyện theo user ID
    @GetMapping("/api/users/{id}/scores")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserScores(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        Map<String, Object> result = new LinkedHashMap<>();
        Map<Integer, Integer> categoryTotals = trainingPointService.getCategoryTotals(user);
        int totalScore = trainingPointService.getTotalScore(user);
        String classification = trainingPointService.getClassification(user);

        result.put("categoryTotals", categoryTotals);
        result.put("totalScore", totalScore);
        result.put("classification", classification);

        // User info
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
        result.put("user", userInfo);

        return ResponseEntity.ok(result);
    }

    /**
     * API cho Admin gửi thông báo broadcast
     * Body: { "title": "...", "message": "...", "target": "ALL" | "MANAGER" | "STUDENT" }
     */
    @PostMapping("/api/notifications/broadcast")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> broadcastNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (userDetails == null) {
            result.put("success", false);
            result.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(result);
        }
        
        String title = body.get("title");
        String message = body.get("message");
        String target = body.getOrDefault("target", "ALL");
        
        if (title == null || title.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "Tiêu đề không được để trống");
            return ResponseEntity.badRequest().body(result);
        }
        
        if (message == null || message.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "Nội dung không được để trống");
            return ResponseEntity.badRequest().body(result);
        }
        
        int count = notificationService.broadcastNotification(title.trim(), message.trim(), target);
        
        String targetLabel = "ALL".equals(target) ? "toàn hệ thống" : 
                             "MANAGER".equals(target) ? "quản lý lớp" : "sinh viên";
        result.put("success", true);
        result.put("message", "Đã gửi thông báo đến " + count + " người dùng (" + targetLabel + ")");
        result.put("recipientCount", count);
        return ResponseEntity.ok(result);
    }

}
