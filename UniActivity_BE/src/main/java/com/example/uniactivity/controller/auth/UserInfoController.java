package com.example.uniactivity.controller.auth;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserInfoController {

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        User user = userDetails.getUser();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("fullName", user.getFullName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("role", user.getRole().name());
        info.put("avatarUrl", user.getAvatarUrl());
        info.put("provider", user.getProvider());
        info.put("status", user.getStatus().name());

        return ResponseEntity.ok(info);
    }
}
