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
import java.util.Map;
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

    /** Giới hạn gửi OTP: tối đa 3 lần / 15 phút cho mỗi loại */
    private static final int MAX_OTP_PER_WINDOW = 3;
    private static final int OTP_WINDOW_MINUTES = 15;

    @PostMapping("/send-verification-email")
    public ResponseEntity<Map<String, String>> sendVerificationEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không được để trống."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không tồn tại trong hệ thống."));
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email đã được xác thực rồi."));
        }

        // Chống spam: đếm số OTP gửi gần đây
        long recentCount = tokenRepository.countByEmailAndTypeAndCreatedAtAfter(
                email, "EMAIL_VERIFY", LocalDateTime.now().minusMinutes(OTP_WINDOW_MINUTES));
        if (recentCount >= MAX_OTP_PER_WINDOW) {
            return ResponseEntity.status(429).body(Map.of(
                    "message", "Bạn đã gửi quá " + MAX_OTP_PER_WINDOW + " lần. Vui lòng thử lại sau " + OTP_WINDOW_MINUTES + " phút."));
        }

        String otp = String.format("%06d", new Random().nextInt(999999));

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtpCode(otp);
        token.setType("EMAIL_VERIFY");
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        tokenRepository.save(token);

        try {
            mailService.sendEmail(email, "Xác thực Email - UniActivity",
                "Mã OTP xác thực email của bạn là: " + otp + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
            return ResponseEntity.ok(Map.of("message", "Mã OTP xác thực đã được gửi đến email của bạn."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi gửi email, vui lòng thử lại sau."));
        }
    }

    /**
     * POST /api/auth/verify-email
     * Xác thực OTP email → đánh dấu emailVerified = true.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email và mã OTP không được để trống."));
        }

        Optional<PasswordResetToken> tokenOpt = tokenRepository
                .findFirstByEmailAndTypeAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
                        email, "EMAIL_VERIFY", LocalDateTime.now());

        if (tokenOpt.isEmpty() || !tokenOpt.get().getOtpCode().equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mã OTP không hợp lệ hoặc đã hết hạn."));
        }

        // Đánh dấu token đã dùng
        PasswordResetToken token = tokenOpt.get();
        token.setUsed(true);
        tokenRepository.save(token);

        // Đánh dấu email đã verified
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setEmailVerified(true);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Xác thực email thành công!"));
        }

        return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy tài khoản với email này."));
    }

    // ===================== QUÊN MẬT KHẨU =====================

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> processForgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail();

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không tồn tại trong hệ thống."));
        }

        User user = userOpt.get();

        // ★ KIỂM TRA EMAIL ĐÃ XÁC THỰC CHƯA
        if (!user.isEmailVerified()) {
            return ResponseEntity.status(403).body(Map.of(
                    "message", "Email chưa được xác thực. Vui lòng xác thực email trước khi sử dụng tính năng quên mật khẩu."));
        }

        // Chống spam: đếm số OTP gửi gần đây
        long recentCount = tokenRepository.countByEmailAndTypeAndCreatedAtAfter(
                email, "PASSWORD_RESET", LocalDateTime.now().minusMinutes(OTP_WINDOW_MINUTES));
        if (recentCount >= MAX_OTP_PER_WINDOW) {
            return ResponseEntity.status(429).body(Map.of(
                    "message", "Bạn đã gửi quá " + MAX_OTP_PER_WINDOW + " lần. Vui lòng thử lại sau " + OTP_WINDOW_MINUTES + " phút."));
        }

        String otp = String.format("%06d", new Random().nextInt(999999));

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtpCode(otp);
        token.setType("PASSWORD_RESET");
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        tokenRepository.save(token);

        try {
            mailService.sendEmail(email, "Mã OTP khôi phục mật khẩu UniActivity", 
                "Mã OTP của bạn là: " + otp + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
            return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi đến email của bạn."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi gửi email, vui lòng thử lại sau."));
        }
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();

        Optional<PasswordResetToken> tokenOpt = tokenRepository
            .findFirstByEmailAndTypeAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
                    email, "PASSWORD_RESET", LocalDateTime.now());

        if (tokenOpt.isPresent() && tokenOpt.get().getOtpCode().equals(otp)) {
            return ResponseEntity.ok(Map.of("message", "OTP hợp lệ."));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Mã OTP không hợp lệ hoặc đã hết hạn."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();
        String newPassword = request.getNewPassword();

        Optional<PasswordResetToken> tokenOpt = tokenRepository
            .findFirstByEmailAndTypeAndUsedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
                    email, "PASSWORD_RESET", LocalDateTime.now());

        if (tokenOpt.isPresent() && tokenOpt.get().getOtpCode().equals(otp)) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setPasswordHash(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                PasswordResetToken token = tokenOpt.get();
                token.setUsed(true);
                tokenRepository.save(token);

                return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công."));
            }
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Mã OTP không hợp lệ hoặc đã hết hạn."));
    }
}
