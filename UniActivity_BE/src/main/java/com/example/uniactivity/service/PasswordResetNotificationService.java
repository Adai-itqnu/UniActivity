package com.example.uniactivity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetNotificationService {

    private final MailService mailService;

    @Async
    public void sendPasswordResetOtp(String email, String code) {
        try {
            mailService.sendEmail(email, "Mã OTP khôi phục mật khẩu UniActivity",
                    "Mã OTP của bạn là: " + code
                            + "\nMã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ cho ai.");
        } catch (RuntimeException exception) {
            log.error("Could not send password reset OTP", exception);
        }
    }
}
