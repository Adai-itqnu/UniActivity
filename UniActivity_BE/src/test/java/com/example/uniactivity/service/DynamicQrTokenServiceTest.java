package com.example.uniactivity.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicQrTokenServiceTest {

    private static final String QR_SECRET_A = "qr-secret-a-0123456789abcdef0123456789";
    private static final String QR_SECRET_B = "qr-secret-b-0123456789abcdef0123456789";

    @Test
    void initRejectsMissingBlankOrShortQrSecret() {
        assertThrows(IllegalStateException.class, () -> configured(null).init());
        assertThrows(IllegalStateException.class, () -> configured("   ").init());
        assertThrows(IllegalStateException.class, () -> configured("short-qr-secret").init());
    }

    @Test
    void qrTokensValidateOnlyWithTheIndependentQrSecret() {
        DynamicQrTokenService issuer = configured(QR_SECRET_A);
        DynamicQrTokenService otherSecret = configured(QR_SECRET_B);
        issuer.init();
        otherSecret.init();

        String token = issuer.generateToken(101L, 202L);

        assertTrue(issuer.validateToken(token, 101L, 202L));
        assertFalse(otherSecret.validateToken(token, 101L, 202L));
        assertFalse(issuer.validateToken(token, 999L, 202L));
        assertFalse(issuer.validateToken(token, 101L, 999L));
    }

    @Test
    void checkinCodesUseTheSharedAlphanumericPolicyAndRemainScoped() {
        DynamicQrTokenService issuer = configured(QR_SECRET_A);
        UnifiedCodePolicy policy = new UnifiedCodePolicy();
        issuer.init();

        String code = issuer.generateCheckinCode(101L, 202L);

        assertTrue(policy.isValid(code));
        assertTrue(issuer.validateCheckinCode(code.toLowerCase(Locale.ROOT), 101L, 202L));
        assertFalse(issuer.validateCheckinCode(code, 999L, 202L));
        assertFalse(issuer.validateCheckinCode(code, 101L, 999L));
        assertFalse(issuer.validateCheckinCode("ABCDEF", 101L, 202L));
    }

    @Test
    void longQrTokensKeepTheirSignedFormatAndAcceptThePreviousWindow() {
        Clock issuanceClock = Clock.fixed(Instant.ofEpochSecond(960), ZoneOffset.UTC);
        Clock nextWindowClock = Clock.fixed(Instant.ofEpochSecond(1_020), ZoneOffset.UTC);
        DynamicQrTokenService issuer = configured(QR_SECRET_A, issuanceClock);
        DynamicQrTokenService validator = configured(QR_SECRET_A, nextWindowClock);
        issuer.init();
        validator.init();

        String token = issuer.generateToken(101L, 202L);
        String code = issuer.generateCheckinCode(101L, 202L);

        assertEquals("YntQu9lQ0jiUJCYL-0RUnbz_F_XulMSpw8IWsRagnS4", token);
        assertEquals(60, issuer.getIntervalSeconds());
        assertTrue(validator.validateToken(token, 101L, 202L));
        assertTrue(validator.validateCheckinCode(code, 101L, 202L));
    }

    private DynamicQrTokenService configured(String secret) {
        DynamicQrTokenService service = new DynamicQrTokenService(new UnifiedCodePolicy());
        ReflectionTestUtils.setField(service, "secretKey", secret);
        return service;
    }

    private DynamicQrTokenService configured(String secret, Clock clock) {
        DynamicQrTokenService service = new DynamicQrTokenService(new UnifiedCodePolicy(), clock);
        ReflectionTestUtils.setField(service, "secretKey", secret);
        return service;
    }
}
