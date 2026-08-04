package com.example.uniactivity.controller.auth;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API cho hồ sơ cá nhân — dùng cho tất cả roles (Student, Manager, Admin).
 * - GET  /api/profile       → Xem thông tin cá nhân
 * - PUT  /api/profile       → Cập nhật thông tin (phone, fullName, avatarUrl)
 * - PUT  /api/profile/password → Đổi mật khẩu
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * GET /api/profile — Lấy thông tin hồ sơ cá nhân đầy đủ
     */
    @GetMapping
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userRepository.findById(userDetails.getUser().getId())
                .orElse(userDetails.getUser());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("role", user.getRole().name());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("provider", user.getProvider());
        profile.put("emailVerified", user.isEmailVerified());
        profile.put("status", user.getStatus().name());
        profile.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // Class info
        if (user.getStudentClass() != null) {
            Map<String, Object> classInfo = new LinkedHashMap<>();
            classInfo.put("id", user.getStudentClass().getId());
            classInfo.put("name", user.getStudentClass().getName());
            classInfo.put("code", user.getStudentClass().getCode());
            if (user.getStudentClass().getFaculty() != null) {
                classInfo.put("facultyName", user.getStudentClass().getFaculty().getName());
            }
            profile.put("studentClass", classInfo);
        }

        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /api/profile — Cập nhật thông tin cá nhân (fullName, phone)
     */
    @PutMapping
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userRepository.findById(userDetails.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update fullName
        String fullName = body.get("fullName");
        if (fullName != null && !fullName.trim().isEmpty()) {
            user.setFullName(fullName.trim());
        }

        // Update phone
        String phone = body.get("phone");
        if (phone != null) {
            // Validate phone format (cho phép rỗng để xóa số)
            if (!phone.isBlank() && !phone.matches("^[0-9+\\-\\s()]{7,20}$")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Số điện thoại không hợp lệ"));
            }
            user.setPhone(phone.isBlank() ? null : phone.trim());
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Cập nhật thông tin thành công",
                "fullName", user.getFullName(),
                "phone", user.getPhone() != null ? user.getPhone() : ""
        ));
    }

    /**
     * PUT /api/profile/password — Đổi mật khẩu
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userRepository.findById(userDetails.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Tài khoản Google không có mật khẩu local
        if ("GOOGLE".equalsIgnoreCase(user.getProvider())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Tài khoản đăng nhập bằng Google không hỗ trợ đổi mật khẩu tại đây"
            ));
        }

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        // Validate
        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập mật khẩu hiện tại"));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập mật khẩu mới"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới phải có ít nhất 6 ký tự"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu xác nhận không khớp"));
        }

        // Check current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu hiện tại không đúng"));
        }

        // Check same password
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới không được trùng mật khẩu cũ"));
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }
}
