package com.example.uniactivity.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private DynamicQrTokenService configured(String secret) {
        DynamicQrTokenService service = new DynamicQrTokenService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        return service;
    }
}
