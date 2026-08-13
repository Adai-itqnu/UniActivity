package com.example.uniactivity.service;

import com.example.uniactivity.entity.PasswordResetToken;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.PasswordResetTokenRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OtpService {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFY = "EMAIL_VERIFY";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final Set<String> ALLOWED_TYPES = Set.of(PASSWORD_RESET, EMAIL_VERIFY);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record IssuedOtp(String code) {}

    @Transactional
    public IssuedOtp issue(String email, String type) {
        requireAllowedType(type);
        tokenRepository.invalidateUnusedTokens(email, type);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtpCode(passwordEncoder.encode(code));
        token.setType(type);
        token.setExpiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        token.setFailedAttempts(0);
        tokenRepository.save(token);
        return new IssuedOtp(code);
    }

    @Transactional
    public boolean verify(String email, String type, String otp) {
        requireAllowedType(type);
        return findAndValidateLocked(email, type, otp).isPresent();
    }

    @Transactional
    public boolean consumeForEmailVerification(String email, String otp) {
        Optional<PasswordResetToken> token =
                findAndValidateLocked(email, EMAIL_VERIFY, otp);
        if (token.isEmpty()) {
            return false;
        }

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return false;
        }

        user.get().setEmailVerified(true);
        token.get().setUsed(true);
        userRepository.save(user.get());
        tokenRepository.save(token.get());
        return true;
    }

    @Transactional
    public boolean consumeForPasswordReset(String email, String otp, String newPassword) {
        Optional<PasswordResetToken> token =
                findAndValidateLocked(email, PASSWORD_RESET, otp);
        if (token.isEmpty()) {
            return false;
        }

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return false;
        }

        User account = user.get();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setTokenVersion(account.getTokenVersion() + 1);
        token.get().setUsed(true);
        userRepository.save(account);
        tokenRepository.save(token.get());
        return true;
    }

    private Optional<PasswordResetToken> findAndValidateLocked(
            String email, String type, String otp) {
        Optional<PasswordResetToken> tokenOptional = tokenRepository
                .findFirstByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(email, type);
        if (tokenOptional.isEmpty()) {
            return Optional.empty();
        }

        PasswordResetToken token = tokenOptional.get();
        if (token.isUsed()
                || !type.equals(token.getType())
                || !token.getExpiryTime().isAfter(LocalDateTime.now())
                || token.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return Optional.empty();
        }

        boolean matches;
        try {
            matches = otp != null && passwordEncoder.matches(otp, token.getOtpCode());
        } catch (RuntimeException ignored) {
            matches = false;
        }
        if (!matches) {
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            tokenRepository.save(token);
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private void requireAllowedType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported OTP type");
        }
    }
}
