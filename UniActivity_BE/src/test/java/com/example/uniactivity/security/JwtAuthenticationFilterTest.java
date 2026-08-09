package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "signed-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequest() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isAccessToken(TOKEN)).thenReturn(false);

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void lockedUserCannotAuthenticateWithOtherwiseValidAccessToken() throws Exception {
        User user = activeUser(3L);
        user.setStatus(UserStatus.LOCKED);
        stubBearerAccessToken(user, 3L);

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void revokedTokenVersionCannotAuthenticate() throws Exception {
        User user = activeUser(4L);
        stubBearerAccessToken(user, 3L);

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void activeUserWithMatchingAccessTokenIsAuthenticated() throws Exception {
        User user = activeUser(5L);
        stubBearerAccessToken(user, 5L);

        runFilter();

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertEquals(user.getId(), principal.getUser().getId());
    }

    @Test
    void queryTokenIsIgnoredOutsideSseSubscribe() throws Exception {
        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    void queryAccessTokenIsIgnoredEvenForSseSubscribe() throws Exception {
        lenient().when(request.getServletPath()).thenReturn("/sse/subscribe");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void sessionAuthenticationCannotAuthorizeBearerOnlyApiRequest() throws Exception {
        when(request.getServletPath()).thenReturn("/api/profile");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("session-user", null, java.util.List.of()));

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void sessionAuthenticationIsPreservedForLegacyMvcRequest() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        var sessionAuthentication =
                UsernamePasswordAuthenticationToken.authenticated("session-user", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(sessionAuthentication);

        runFilter();

        assertEquals(sessionAuthentication, SecurityContextHolder.getContext().getAuthentication());
    }

    private void stubBearerAccessToken(User user, long tokenVersion) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        stubValidAccessToken(user, tokenVersion);
    }

    private void stubValidAccessToken(User user, long tokenVersion) {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isAccessToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(user.getId());
        lenient().when(jwtTokenProvider.getTokenVersion(TOKEN)).thenReturn(tokenVersion);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User activeUser(long tokenVersion) {
        User user = new User();
        user.setId(11L);
        user.setUsername("student11");
        user.setPasswordHash("encoded");
        user.setFullName("Student 11");
        user.setEmail("student11@example.edu.vn");
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private void runFilter() throws Exception {
        new JwtAuthenticationFilter(jwtTokenProvider, userRepository)
                .doFilterInternal(request, response, filterChain);
    }
}
