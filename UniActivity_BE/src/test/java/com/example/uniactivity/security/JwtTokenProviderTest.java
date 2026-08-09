package com.example.uniactivity.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void initRejectsMissingSecret() {
        JwtTokenProvider provider = configuredProvider(null);

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    void initRejectsBlankSecret() {
        JwtTokenProvider provider = configuredProvider("   ");

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    void initRejectsSecretShorterThan32Utf8Bytes() {
        JwtTokenProvider provider = configuredProvider("short-secret");

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    void accessTokenContainsTypeAndTokenVersion() {
        JwtTokenProvider provider = initializedProvider();

        String token = provider.generateAccessToken(7L, "student01", "STUDENT", 4L);

        assertTrue(provider.validateToken(token));
        assertTrue(provider.isAccessToken(token));
        assertFalse(provider.isRefreshToken(token));
        assertEquals("access", provider.getTokenType(token));
        assertEquals(4L, provider.getTokenVersion(token));
        assertEquals(7L, provider.getUserIdFromToken(token));
    }

    @Test
    void refreshTokenContainsTypeAndTokenVersion() {
        JwtTokenProvider provider = initializedProvider();

        String token = provider.generateRefreshToken(7L, 9L);

        assertTrue(provider.validateToken(token));
        assertTrue(provider.isRefreshToken(token));
        assertFalse(provider.isAccessToken(token));
        assertEquals("refresh", provider.getTokenType(token));
        assertEquals(9L, provider.getTokenVersion(token));
    }

    @Test
    void sseTicketIsPurposeBoundAndExpiresWithinSixtySeconds() {
        JwtTokenProvider provider = initializedProvider();

        String ticket = provider.generateSseTicket(7L, 12L);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(ticket)
                .getPayload();

        assertTrue(provider.validateToken(ticket));
        assertTrue(provider.isSseTicket(ticket));
        assertFalse(provider.isAccessToken(ticket));
        assertFalse(provider.isRefreshToken(ticket));
        assertEquals("sse", provider.getTokenType(ticket));
        assertEquals(12L, provider.getTokenVersion(ticket));
        assertTrue(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()).toSeconds() <= 60);
    }

    private JwtTokenProvider initializedProvider() {
        JwtTokenProvider provider = configuredProvider(VALID_SECRET);
        provider.init();
        return provider;
    }

    private JwtTokenProvider configuredProvider(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "accessTokenExpirationMs", 60_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpirationMs", 120_000L);
        return provider;
    }
}
