package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.*;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ClassJoinRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for student class join requests
 */
@RestController
@RequestMapping("/student/api")
@RequiredArgsConstructor
public class StudentJoinClassController {

    private final ClassJoinRequestService classJoinRequestService;

    @PostMapping("/join-class")
    public ResponseEntity<?> joinClass(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        try {
            String joinCode = body.get("joinCode");
            if (joinCode == null || joinCode.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Vui lòng nhập mã tham gia"));
            }

            User currentUser = userDetails.getUser();
            ClassJoinRequest request = classJoinRequestService.createJoinRequest(currentUser, joinCode.trim());
            
            return ResponseEntity.ok(Map.of(
                    "message", "Đã gửi yêu cầu tham gia lớp " + request.getStudentClass().getName(),
                    "className", request.getStudentClass().getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
