package com.example.uniactivity.service;

import com.example.uniactivity.entity.OAuthExchangeCode;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.OAuthExchangeCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class OAuthExchangeCodeService {

    static final long EXPIRATION_SECONDS = 60L;

    private final OAuthExchangeCodeRepository repository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public OAuthExchangeCodeService(OAuthExchangeCodeRepository repository) {
        this(repository, Clock.systemUTC(), new SecureRandom());
    }

    OAuthExchangeCodeService(OAuthExchangeCodeRepository repository, Clock clock, SecureRandom secureRandom) {
        this.repository = repository;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public String issue(User user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        OAuthExchangeCode record = new OAuthExchangeCode();
        record.setCodeHash(hash(rawCode));
        record.setUser(user);
        record.setExpiresAt(now().plusSeconds(EXPIRATION_SECONDS));
        repository.save(record);
        return rawCode;
    }

    @Transactional
    public Optional<User> consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }

        Optional<OAuthExchangeCode> found = repository.findByCodeHashForUpdate(hash(rawCode));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        OAuthExchangeCode record = found.get();
        LocalDateTime now = now();
        if (record.getConsumedAt() != null
                || !record.getExpiresAt().isAfter(now)
                || record.getUser().getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }

        record.setConsumedAt(now);
        return Optional.of(record.getUser());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private String hash(String rawCode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
