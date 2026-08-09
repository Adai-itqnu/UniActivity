package com.example.uniactivity.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretSeparationValidatorTest {

    private static final String JWT_SECRET = "jwt-secret-that-is-at-least-thirty-two-bytes";
    private static final String QR_SECRET = "qr-secret-that-is-different-and-thirty-two-bytes";

    @Test
    void rejectsIdenticalJwtAndQrSecrets() {
        SecretSeparationValidator validator = new SecretSeparationValidator(JWT_SECRET, JWT_SECRET);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void acceptsDistinctJwtAndQrSecrets() {
        SecretSeparationValidator validator = new SecretSeparationValidator(JWT_SECRET, QR_SECRET);

        assertDoesNotThrow(validator::validate);
    }
}
