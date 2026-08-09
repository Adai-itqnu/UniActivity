package com.example.uniactivity.service;

import com.example.uniactivity.entity.OAuthExchangeCode;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.OAuthExchangeCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthExchangeCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private OAuthExchangeCodeRepository repository;

    @Test
    void issueStoresOnlyHashAndExpiresAfterSixtySeconds() {
        User user = activeUser();
        OAuthExchangeCodeService service = service();

        String rawCode = service.issue(user);

        ArgumentCaptor<OAuthExchangeCode> saved = ArgumentCaptor.forClass(OAuthExchangeCode.class);
        verify(repository).save(saved.capture());
        assertFalse(rawCode.isBlank());
        assertNotEquals(rawCode, saved.getValue().getCodeHash());
        assertEquals(64, saved.getValue().getCodeHash().length());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC), saved.getValue().getExpiresAt());
        assertNull(saved.getValue().getConsumedAt());
    }

    @Test
    void consumeReturnsActiveUserOnceAndMarksCodeConsumed() {
        OAuthExchangeCodeService service = service();
        User user = activeUser();
        String rawCode = service.issue(user);
        ArgumentCaptor<OAuthExchangeCode> saved = ArgumentCaptor.forClass(OAuthExchangeCode.class);
        verify(repository).save(saved.capture());
        OAuthExchangeCode record = saved.getValue();
        when(repository.findByCodeHashForUpdate(record.getCodeHash())).thenReturn(Optional.of(record));

        Optional<User> first = service.consume(rawCode);
        Optional<User> second = service.consume(rawCode);

        assertEquals(Optional.of(user), first);
        assertTrue(second.isEmpty());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), record.getConsumedAt());
    }

    @Test
    void consumeRejectsExpiredOrLockedAccount() {
        OAuthExchangeCodeService service = service();
        OAuthExchangeCode expired = new OAuthExchangeCode();
        expired.setCodeHash("expired-hash");
        expired.setExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        expired.setUser(activeUser());

        OAuthExchangeCode locked = new OAuthExchangeCode();
        locked.setCodeHash("locked-hash");
        locked.setExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(10), ZoneOffset.UTC));
        User lockedUser = activeUser();
        lockedUser.setStatus(UserStatus.LOCKED);
        locked.setUser(lockedUser);

        when(repository.findByCodeHashForUpdate(anyString()))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.of(locked));

        assertTrue(service.consume("expired-code").isEmpty());
        assertTrue(service.consume("locked-code").isEmpty());
    }

    private OAuthExchangeCodeService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new OAuthExchangeCodeService(repository, clock, new SecureRandom());
    }

    private User activeUser() {
        User user = new User();
        user.setId(31L);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
