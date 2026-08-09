package com.example.uniactivity.controller;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.SseEmitterService;
import com.example.uniactivity.service.SseTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock
    private SseEmitterService emitterService;

    @Mock
    private SseTicketService ticketService;

    @Test
    void authenticatedUserCanIssuePurposeTicket() {
        User user = new User();
        user.setId(71L);
        user.setTokenVersion(3L);
        when(ticketService.issue(user)).thenReturn("purpose-ticket");

        Map<String, Object> result = controller().issueTicket(new CustomUserDetails(user));

        assertEquals("purpose-ticket", result.get("ticket"));
        assertEquals(60L, result.get("expiresIn"));
    }

    @Test
    void subscribeUsesOnlyResolvedTicketIdentity() {
        SseEmitter emitter = new SseEmitter();
        when(ticketService.resolveUserId("purpose-ticket")).thenReturn(OptionalLong.of(71L));
        when(emitterService.subscribe(71L)).thenReturn(emitter);

        assertEquals(emitter, controller().subscribe("purpose-ticket"));
    }

    @Test
    void subscribeRejectsInvalidTicket() {
        when(ticketService.resolveUserId("invalid-ticket")).thenReturn(OptionalLong.empty());

        assertThrows(ResponseStatusException.class, () -> controller().subscribe("invalid-ticket"));
    }

    private SseController controller() {
        return new SseController(emitterService, ticketService);
    }
}
