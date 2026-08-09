package com.example.uniactivity.service;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseTicketServiceTest {

    private static final String TICKET = "sse-ticket";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Test
    void resolvesActiveVersionMatchingSseTicket() {
        User user = activeUser(4L);
        stubTicket(user, 4L);

        OptionalLong result = service().resolveUserId(TICKET);

        assertTrue(result.isPresent());
        assertEquals(user.getId(), result.getAsLong());
    }

    @Test
    void rejectsOrdinaryAccessToken() {
        when(jwtTokenProvider.validateToken(TICKET)).thenReturn(true);
        when(jwtTokenProvider.isSseTicket(TICKET)).thenReturn(false);

        assertTrue(service().resolveUserId(TICKET).isEmpty());
    }

    @Test
    void rejectsLockedUserOrRevokedVersion() {
        User locked = activeUser(2L);
        locked.setStatus(UserStatus.LOCKED);
        stubTicket(locked, 2L);
        assertTrue(service().resolveUserId(TICKET).isEmpty());

        User revoked = activeUser(5L);
        stubTicket(revoked, 4L);
        assertTrue(service().resolveUserId(TICKET).isEmpty());
    }

    private void stubTicket(User user, long ticketVersion) {
        when(jwtTokenProvider.validateToken(TICKET)).thenReturn(true);
        when(jwtTokenProvider.isSseTicket(TICKET)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(TICKET)).thenReturn(user.getId());
        when(jwtTokenProvider.getTokenVersion(TICKET)).thenReturn(ticketVersion);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User activeUser(long version) {
        User user = new User();
        user.setId(61L);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(version);
        return user;
    }

    private SseTicketService service() {
        return new SseTicketService(jwtTokenProvider, userRepository);
    }
}
