package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API endpoint cho React frontend đăng ký tài khoản.
 * Trả về JSON thay vì redirect HTML.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationApiController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationDto dto, BindingResult result) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (result.hasErrors()) {
            String firstError = result.getFieldErrors().stream()
                    .findFirst()
                    .map(e -> e.getDefaultMessage())
                    .orElse("Dữ liệu không hợp lệ.");
            response.put("error", firstError);
            return ResponseEntity.badRequest().body(response);
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            response.put("error", "Mật khẩu nhập lại không khớp.");
            return ResponseEntity.badRequest().body(response);
        }

        if (!dto.isTermsAccepted()) {
            response.put("error", "Bạn phải chấp nhận các điều khoản và điều kiện.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            var user = userService.registerUser(dto);
            response.put("message", "Đăng ký thành công!");
            response.put("username", user.getUsername());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
