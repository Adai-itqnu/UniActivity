package com.example.uniactivity.controller.auth;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.security.JwtTokenProvider;
import com.example.uniactivity.service.OAuthExchangeCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthControllerTest {

    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthExchangeCodeService exchangeCodeService;

    @Mock
    private Authentication authentication;

    private JwtAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new JwtAuthController(
                authenticationManager, jwtTokenProvider, userRepository, exchangeCodeService
        );
    }

    @Test
    void refreshRejectsLockedAccount() {
        User user = user(UserStatus.LOCKED, 2L);
        stubValidRefreshToken(user);

        ResponseEntity<?> response = controller.refreshToken(Map.of("refreshToken", REFRESH_TOKEN));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refreshRejectsRevokedTokenVersion() {
        User user = user(UserStatus.ACTIVE, 5L);
        stubValidRefreshToken(user);
        when(jwtTokenProvider.getTokenVersion(REFRESH_TOKEN)).thenReturn(4L);

        ResponseEntity<?> response = controller.refreshToken(Map.of("refreshToken", REFRESH_TOKEN));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refreshCreatesAccessTokenWithCurrentVersion() {
        User user = user(UserStatus.ACTIVE, 5L);
        stubValidRefreshToken(user);
        when(jwtTokenProvider.getTokenVersion(REFRESH_TOKEN)).thenReturn(5L);
        when(jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), 5L
        )).thenReturn("new-access-token");

        ResponseEntity<?> response = controller.refreshToken(Map.of("refreshToken", REFRESH_TOKEN));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(jwtTokenProvider).generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), 5L
        );
    }

    @Test
    void logoutAtomicallyIncrementsAuthenticatedUsersTokenVersion() {
        User user = user(UserStatus.ACTIVE, 7L);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(user));
        when(userRepository.incrementTokenVersionById(user.getId())).thenReturn(1);

        ResponseEntity<?> response = controller.logoutJwt(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).incrementTokenVersionById(user.getId());
    }

    @Test
    void oauthExchangeConsumesCodeAndReturnsNormalJwtResponse() {
        User user = user(UserStatus.ACTIVE, 8L);
        when(exchangeCodeService.consume("one-time-code")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), 8L
        )).thenReturn("oauth-access-token");
        when(jwtTokenProvider.generateRefreshToken(user.getId(), 8L)).thenReturn("oauth-refresh-token");

        ResponseEntity<?> response = controller.exchangeOAuthCode(Map.of("code", "one-time-code"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("oauth-access-token", body.get("accessToken"));
        assertEquals("oauth-refresh-token", body.get("refreshToken"));
    }

    @Test
    void oauthExchangeRejectsMissingConsumedOrExpiredCode() {
        when(exchangeCodeService.consume("invalid-code")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.exchangeOAuthCode(Map.of("code", "invalid-code"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private void stubValidRefreshToken(User user) {
        when(jwtTokenProvider.validateToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(UserStatus status, long tokenVersion) {
        User user = new User();
        user.setId(21L);
        user.setUsername("student21");
        user.setPasswordHash("encoded");
        user.setFullName("Student 21");
        user.setEmail("student21@example.edu.vn");
        user.setRole(Role.STUDENT);
        user.setStatus(status);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
