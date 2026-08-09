package com.example.uniactivity.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Ensures JWT signing and QR HMAC keys do not share the same blast radius. */
@Component
public class SecretSeparationValidator {

    private final String jwtSecret;
    private final String qrSecret;

    public SecretSeparationValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.qr.secret}") String qrSecret) {
        this.jwtSecret = jwtSecret;
        this.qrSecret = qrSecret;
    }

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || qrSecret == null) {
            return;
        }

        if (MessageDigest.isEqual(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                qrSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("JWT secret and QR secret must be different");
        }
    }
}
