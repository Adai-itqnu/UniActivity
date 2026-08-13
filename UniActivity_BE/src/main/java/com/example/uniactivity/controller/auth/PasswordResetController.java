package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.ForgotPasswordRequest;
import com.example.uniactivity.dto.auth.ResetPasswordRequest;
import com.example.uniactivity.dto.auth.VerifyOtpRequest;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.PasswordResetTokenRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.service.MailService;
import com.example.uniactivity.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private static final int MAX_OTP_PER_WINDOW = 3;
    private static final int OTP_WINDOW_MINUTES = 15;
    private static final Map<String, String> GENERIC_FORGOT_RESPONSE = Map.of(
            "message", "Nếu email hợp lệ, mã OTP sẽ được gửi đến hộp thư của bạn.");

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailService mailService;
    private final OtpService otpService;

    @PostMapping("/send-verification-email")
    public ResponseEntity<Map<String, String>> sendVerificationEmail(
            @RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email không được để trống."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email không tồn tại trong hệ thống."));
        }
        if (userOpt.get().isEmailVerified()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email đã được xác thực rồi."));
        }
        if (recentOtpCount(email, OtpService.EMAIL_VERIFY) >= MAX_OTP_PER_WINDOW) {
            return tooManyRequests();
        }

        OtpService.IssuedOtp issued = otpService.issue(email, OtpService.EMAIL_VERIFY);
        try {
            mailService.sendEmail(email, "Xác thực Email - UniActivity",
                    "Mã OTP xác thực email của bạn là: " + issued.code()
                            + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
            return ResponseEntity.ok(
                    Map.of("message", "Mã OTP xác thực đã được gửi đến email của bạn."));
        } catch (RuntimeException exception) {
            log.error("Could not send email verification OTP", exception);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Lỗi gửi email, vui lòng thử lại sau."));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email và mã OTP không được để trống."));
        }

        if (otpService.consumeForEmailVerification(email, otp)) {
            return ResponseEntity.ok(Map.of("message", "Xác thực email thành công!"));
        }
        return invalidOtp();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> processForgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()
                || !userOpt.get().isEmailVerified()
                || recentOtpCount(email, OtpService.PASSWORD_RESET) >= MAX_OTP_PER_WINDOW) {
            return ResponseEntity.ok(GENERIC_FORGOT_RESPONSE);
        }

        OtpService.IssuedOtp issued = otpService.issue(email, OtpService.PASSWORD_RESET);
        try {
            mailService.sendEmail(email, "Mã OTP khôi phục mật khẩu UniActivity",
                    "Mã OTP của bạn là: " + issued.code()
                            + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
        } catch (RuntimeException exception) {
            log.error("Could not send password reset OTP", exception);
        }
        return ResponseEntity.ok(GENERIC_FORGOT_RESPONSE);
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        if (otpService.verify(request.getEmail(), OtpService.PASSWORD_RESET, request.getOtp())) {
            return ResponseEntity.ok(Map.of("message", "OTP hợp lệ."));
        }
        return invalidOtp();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        if (otpService.consumeForPasswordReset(
                request.getEmail(), request.getOtp(), request.getNewPassword())) {
            return ResponseEntity.ok(
                    Map.of("message", "Mật khẩu đã được đặt lại thành công."));
        }
        return invalidOtp();
    }

    private long recentOtpCount(String email, String type) {
        return tokenRepository.countByEmailAndTypeAndCreatedAtAfter(
                email, type, LocalDateTime.now().minusMinutes(OTP_WINDOW_MINUTES));
    }

    private ResponseEntity<Map<String, String>> tooManyRequests() {
        return ResponseEntity.status(429).body(Map.of(
                "message", "Bạn đã gửi quá " + MAX_OTP_PER_WINDOW
                        + " lần. Vui lòng thử lại sau " + OTP_WINDOW_MINUTES + " phút."));
    }

    private ResponseEntity<Map<String, String>> invalidOtp() {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Mã OTP không hợp lệ hoặc đã hết hạn."));
    }
}
