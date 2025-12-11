package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ClassJoinRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing class members and join requests
 */
@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerMemberController {

    private final UserRepository userRepository;
    private final ClassJoinRequestService classJoinRequestService;
    private final StudentClassRepository studentClassRepository;

    @GetMapping("/join-requests")
    public String joinRequests(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        
        if (currentUser.getStudentClass() != null) {
            List<ClassJoinRequest> pendingRequests = classJoinRequestService.getPendingRequestsForClass(currentUser.getStudentClass());
            model.addAttribute("pendingRequests", pendingRequests);
        }
        
        return "manager/join-requests";
    }

    @GetMapping("/members")
    public String members(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @RequestParam(required = false) String search,
                          Model model) {
        User currentUser = userDetails.getUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("studentClass", currentUser.getStudentClass());
        
        if (currentUser.getStudentClass() != null) {
            List<User> members;
            if (search != null && !search.isBlank()) {
                members = userRepository.findByStudentClassAndFullNameContainingIgnoreCaseOrStudentClassAndUsernameContainingIgnoreCase(
                    currentUser.getStudentClass(), search, currentUser.getStudentClass(), search);
                model.addAttribute("search", search);
            } else {
                members = userRepository.findByStudentClass(currentUser.getStudentClass());
            }
            model.addAttribute("members", members);
            model.addAttribute("memberCount", userRepository.countByStudentClass(currentUser.getStudentClass()));
        }
        
        return "manager/members";
    }

    // ========== API ==========

    @PostMapping("/api/join-requests/{id}/approve")
    @ResponseBody
    public ResponseEntity<?> approveJoinRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            classJoinRequestService.approveRequest(id, userDetails.getUser());
            return ResponseEntity.ok(Map.of("message", "Đã duyệt yêu cầu"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/join-requests/{id}/reject")
    @ResponseBody
    public ResponseEntity<?> rejectJoinRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            classJoinRequestService.rejectRequest(id, userDetails.getUser());
            return ResponseEntity.ok(Map.of("message", "Đã từ chối yêu cầu"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/members/{userId}")
    @ResponseBody
    public ResponseEntity<?> removeMember(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            User member = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên"));
            
            if (!member.getStudentClass().equals(userDetails.getUser().getStudentClass())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Không có quyền xóa thành viên này"));
            }
            
            member.setStudentClass(null);
            userRepository.save(member);
            
            return ResponseEntity.ok(Map.of("message", "Đã xóa khỏi lớp"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/regenerate-join-code")
    @ResponseBody
    public ResponseEntity<?> regenerateJoinCode(@AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            User currentUser = userDetails.getUser();
            if (currentUser.getStudentClass() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa được gán quản lý lớp nào"));
            }
            
            // Generate a unique 6-character code
            String newCode = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            
            // Update class with new join code
            var studentClass = currentUser.getStudentClass();
            studentClass.setJoinCode(newCode);
            studentClassRepository.save(studentClass);
            
            return ResponseEntity.ok(Map.of(
                "message", "Đã tạo mã tham gia mới",
                "joinCode", newCode
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
