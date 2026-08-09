package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.service.OAuthExchangeCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationSuccessHandlerTest {

    @Mock
    private OAuthExchangeCodeService exchangeCodeService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Test
    void oauthRedirectContainsOnlyOneTimeExchangeCode() throws Exception {
        User user = new User();
        user.setId(41L);
        user.setUsername("google-user");
        user.setEmail("verified@example.edu.vn");
        user.setFullName("Verified User");
        user.setPasswordHash("encoded");
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);

        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(user));
        when(exchangeCodeService.issue(user)).thenReturn("one-time_code-123");

        CustomAuthenticationSuccessHandler handler = new CustomAuthenticationSuccessHandler(
                exchangeCodeService, "http://localhost:5173"
        );
        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirect.capture());
        assertEquals("http://localhost:5173/login?code=one-time_code-123", redirect.getValue());
        assertFalse(redirect.getValue().contains("token="));
        assertFalse(redirect.getValue().contains("refreshToken"));
        assertFalse(redirect.getValue().contains("user="));
    }
}
