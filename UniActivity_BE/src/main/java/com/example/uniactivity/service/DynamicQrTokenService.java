package com.example.uniactivity.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

/**
 * Service tạo và validate Dynamic QR Token cho check-in hoạt động.
 * 
 * Cơ chế chống gửi QR từ xa:
 * - Token = HMAC-SHA256(activityId:classId:timeWindow, secret)
 * - timeWindow = timestamp / INTERVAL_SECONDS (mặc định 60s)
 * - QR đổi liên tục mỗi 60 giây → screenshot sẽ hết hạn nhanh
 * - Server chấp nhận cả window hiện tại VÀ window trước đó (tolerance 1)
 *   để tránh edge case sinh viên quét đúng lúc QR đổi.
 */
@Service
@Slf4j
public class DynamicQrTokenService {

    private final UnifiedCodePolicy codePolicy;
    private final Clock clock;

    @Value("${app.qr.secret}")
    private String secretKey;

    @Autowired
    public DynamicQrTokenService(UnifiedCodePolicy codePolicy) {
        this(codePolicy, Clock.systemUTC());
    }

    DynamicQrTokenService(UnifiedCodePolicy codePolicy, Clock clock) {
        this.codePolicy = Objects.requireNonNull(codePolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("QR secret must be configured");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("QR secret must contain at least 32 UTF-8 bytes");
        }
    }

    /** QR đổi mỗi 60 giây (1 phút) */
    private static final int INTERVAL_SECONDS = 60;

    /** Chấp nhận token từ window trước đó (tolerance = 1 → ±60s) */
    private static final int TOLERANCE_WINDOWS = 1;

    /**
     * Tạo dynamic token cho hoạt động + lớp tại thời điểm hiện tại.
     * Token này sẽ hết hạn sau INTERVAL_SECONDS giây.
     * 
     * @return token string (Base64 URL-safe)
     */
    public String generateToken(Long activityId, Long classId) {
        long currentWindow = getCurrentWindow();
        return computeHmac(activityId, classId, currentWindow);
    }

    /**
     * Lấy thời gian hết hạn (epoch millis) của token hiện tại.
     */
    public long getTokenExpiresAt() {
        long currentWindow = getCurrentWindow();
        return (currentWindow + 1) * INTERVAL_SECONDS * 1000L;
    }

    /**
     * Lấy số giây còn lại trước khi token hết hạn.
     */
    public int getSecondsRemaining() {
        long now = clock.millis() / 1000;
        long windowEnd = (getCurrentWindow() + 1) * INTERVAL_SECONDS;
        return (int) Math.max(0, windowEnd - now);
    }

    /**
     * Validate token check-in. Chấp nhận window hiện tại ± tolerance.
     * 
     * @return true nếu token hợp lệ (chưa hết hạn hoặc mới hết hạn trong tolerance)
     */
    public boolean validateToken(String token, Long activityId, Long classId) {
        if (token == null || token.isBlank()) return false;

        long currentWindow = getCurrentWindow();

        // Kiểm tra window hiện tại VÀ các window gần đó (tolerance)
        for (int offset = -TOLERANCE_WINDOWS; offset <= 0; offset++) {
            String expected = computeHmac(activityId, classId, currentWindow + offset);
            if (MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.US_ASCII),
                    expected.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }

        log.warn("Invalid QR token for activity {} class {}", activityId, classId);
        return false;
    }

    /**
     * Lấy interval (giây) — frontend cần biết để set timer refresh QR.
     */
    public int getIntervalSeconds() {
        return INTERVAL_SECONDS;
    }

    /**
     * Tạo mã check-in 6 ký tự chữ-số, đồng bộ với QR token (cùng timeWindow).
     * Sinh viên có thể nhập mã này thay vì quét QR.
     */
    public String generateCheckinCode(Long activityId, Long classId) {
        return codePolicy.deriveCode(computeHmacBytes(
                "CODE:" + activityId + ":" + classId + ":" + getCurrentWindow()));
    }

    /**
     * Validate mã check-in. Chấp nhận window hiện tại ± tolerance.
     */
    public boolean validateCheckinCode(String code, Long activityId, Long classId) {
        String normalized = codePolicy.normalize(code);
        if (!codePolicy.isValid(normalized)) return false;
        long currentWindow = getCurrentWindow();
        for (int offset = -TOLERANCE_WINDOWS; offset <= 0; offset++) {
            String expected = codePolicy.deriveCode(computeHmacBytes(
                    "CODE:" + activityId + ":" + classId + ":" + (currentWindow + offset)));
            if (MessageDigest.isEqual(
                    normalized.getBytes(StandardCharsets.US_ASCII),
                    expected.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        log.warn("Invalid checkin code for activity {} class {}", activityId, classId);
        return false;
    }

    // ===================== PRIVATE =====================

    private long getCurrentWindow() {
        return clock.millis() / 1000 / INTERVAL_SECONDS;
    }

    /**
     * HMAC-SHA256 để tạo token không thể giả mạo.
     */
    private String computeHmac(Long activityId, Long classId, long timeWindow) {
        byte[] hmacBytes = computeHmacBytes(activityId + ":" + classId + ":" + timeWindow);
        // URL-safe Base64, no padding → compact cho QR
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }

    private byte[] computeHmacBytes(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Failed to compute HMAC for QR token", exception);
        }
    }
}
