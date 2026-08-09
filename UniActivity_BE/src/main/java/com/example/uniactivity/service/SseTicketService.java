package com.example.uniactivity.service;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class SseTicketService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public String issue(User user) {
        return jwtTokenProvider.generateSseTicket(user.getId(), user.getTokenVersion());
    }

    public OptionalLong resolveUserId(String ticket) {
        if (ticket == null || ticket.isBlank()
                || !jwtTokenProvider.validateToken(ticket)
                || !jwtTokenProvider.isSseTicket(ticket)) {
            return OptionalLong.empty();
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(ticket);
            long tokenVersion = jwtTokenProvider.getTokenVersion(ticket);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null
                    || user.getStatus() != UserStatus.ACTIVE
                    || user.getTokenVersion() != tokenVersion) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(userId);
        } catch (RuntimeException ex) {
            return OptionalLong.empty();
        }
    }
}
