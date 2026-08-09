package com.example.uniactivity.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Tạo và xác thực JWT tokens.
 * - Access Token: ngắn hạn (mặc định 1 giờ)
 * - Refresh Token: dài hạn (mặc định 7 ngày)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final long SSE_TICKET_EXPIRATION_SECONDS = 60L;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms:3600000}") // 1 hour
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}") // 7 days
    private long refreshTokenExpirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 UTF-8 bytes");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Tạo Access Token
     */
    public String generateAccessToken(Long userId, String username, String role, long tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Tạo Refresh Token (chỉ chứa userId, ít claims hơn)
     */
    public String generateRefreshToken(Long userId, long tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateSseTicket(Long userId, long tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + SSE_TICKET_EXPIRATION_SECONDS * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "sse")
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Lấy userId từ token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Lấy username từ token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("username", String.class);
    }

    /**
     * Lấy role từ token
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    public long getTokenVersion(String token) {
        Object value = parseClaims(token).get("tokenVersion");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("JWT tokenVersion claim is missing or invalid");
        }
        return number.longValue();
    }

    public boolean isAccessToken(String token) {
        return hasTokenType(token, "access");
    }

    public boolean isSseTicket(String token) {
        return hasTokenType(token, "sse");
    }

    /**
     * Kiểm tra token có phải refresh token không
     */
    public boolean isRefreshToken(String token) {
        return hasTokenType(token, "refresh");
    }

    private boolean hasTokenType(String token, String expectedType) {
        try {
            return expectedType.equals(getTokenType(token));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token — kiểm tra chữ ký + hết hạn
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT malformed: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.warn("JWT signature invalid: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}
