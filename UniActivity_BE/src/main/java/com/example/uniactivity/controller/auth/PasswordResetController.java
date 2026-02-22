package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.ForgotPasswordRequest;
import com.example.uniactivity.dto.auth.ResetPasswordRequest;
import com.example.uniactivity.dto.auth.VerifyOtpRequest;
import com.example.uniactivity.entity.PasswordResetToken;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.PasswordResetTokenRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.service.MailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> processForgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"message\": \"Email không tồn tại trong hệ thống.\"}");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtpCode(otp);
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        tokenRepository.save(token);

        try {
            mailService.sendEmail(email, "Mã OTP khôi phục mật khẩu UniActivity", 
                "Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
            return ResponseEntity.ok("{\"message\": \"Mã OTP đã được gửi đến email của bạn.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Lỗi gửi email, vui lòng thử lại sau.\"}");
        }
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();

        Optional<PasswordResetToken> tokenOpt = tokenRepository
            .findFirstByEmailAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(email, LocalDateTime.now());

        if (tokenOpt.isPresent() && tokenOpt.get().getOtpCode().equals(otp)) {
            return ResponseEntity.ok("{\"message\": \"OTP hợp lệ.\"}");
        }
        return ResponseEntity.badRequest().body("{\"message\": \"Mã OTP không hợp lệ hoặc đã hết hạn.\"}");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();
        String newPassword = request.getNewPassword();

        Optional<PasswordResetToken> tokenOpt = tokenRepository
            .findFirstByEmailAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(email, LocalDateTime.now());

        if (tokenOpt.isPresent() && tokenOpt.get().getOtpCode().equals(otp)) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setPasswordHash(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                PasswordResetToken token = tokenOpt.get();
                token.setUsed(true);
                tokenRepository.save(token);

                return ResponseEntity.ok("{\"message\": \"Mật khẩu đã được đặt lại thành công.\"}");
            }
        }
        return ResponseEntity.badRequest().body("{\"message\": \"Mã OTP không hợp lệ hoặc đã hết hạn.\"}");
    }
}
