package com.example.uniactivity.service;

import com.example.uniactivity.entity.PasswordResetToken;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.PasswordResetTokenRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private OtpService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new OtpService(tokenRepository, userRepository, passwordEncoder);
    }

    @Test
    void storesHashInsteadOfPlainOtp() {
        OtpService.IssuedOtp issued = service.issue("student@example.com", "PASSWORD_RESET");

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        assertEquals(6, issued.code().length());
        assertNotEquals(issued.code(), saved.getOtpCode());
        assertTrue(passwordEncoder.matches(issued.code(), saved.getOtpCode()));
    }

    @Test
    void rejectsExpiredUsedAndWrongTypeOtp() {
        PasswordResetToken expired = token("123456", "PASSWORD_RESET");
        expired.setExpiryTime(LocalDateTime.now().minusSeconds(1));
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(expired));
        assertFalse(service.verify("student@example.com", "PASSWORD_RESET", "123456"));

        PasswordResetToken used = token("123456", "PASSWORD_RESET");
        used.setUsed(true);
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(used));
        assertFalse(service.verify("student@example.com", "PASSWORD_RESET", "123456"));

        PasswordResetToken wrongType = token("123456", "EMAIL_VERIFY");
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(wrongType));
        assertFalse(service.verify("student@example.com", "PASSWORD_RESET", "123456"));
    }

    @Test
    void locksAfterFiveFailedAttempts() {
        PasswordResetToken token = token("123456", "PASSWORD_RESET");
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(token));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(service.verify("student@example.com", "PASSWORD_RESET", "000000"));
        }

        assertEquals(5, token.getFailedAttempts());
        assertFalse(service.verify("student@example.com", "PASSWORD_RESET", "123456"));
    }

    @Test
    void onlyOneConcurrentConsumeCanSucceed() {
        PasswordResetToken token = token("123456", "PASSWORD_RESET");
        User user = user();
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));

        assertTrue(service.consumeForPasswordReset(
                "student@example.com", "123456", "new-password"));
        assertFalse(service.consumeForPasswordReset(
                "student@example.com", "123456", "other-password"));

        verify(userRepository, times(1)).save(user);
    }

    @Test
    void passwordResetIncrementsTokenVersion() {
        PasswordResetToken token = token("123456", "PASSWORD_RESET");
        User user = user();
        user.setTokenVersion(4);
        when(tokenRepository.findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(
                "student@example.com", "PASSWORD_RESET"))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));

        assertTrue(service.consumeForPasswordReset(
                "student@example.com", "123456", "new-password"));

        assertEquals(5, user.getTokenVersion());
        assertTrue(token.isUsed());
        assertTrue(passwordEncoder.matches("new-password", user.getPasswordHash()));
    }

    private PasswordResetToken token(String otp, String type) {
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail("student@example.com");
        token.setType(type);
        token.setOtpCode(passwordEncoder.encode(otp));
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        return token;
    }

    private User user() {
        User user = new User();
        user.setEmail("student@example.com");
        user.setPasswordHash(passwordEncoder.encode("old-password"));
        return user;
    }
}
