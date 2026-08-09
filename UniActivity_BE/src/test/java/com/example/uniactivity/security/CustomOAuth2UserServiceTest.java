package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
    }

    @Test
    void rejectsGoogleIdentityWithoutVerifiedEmailClaim() {
        OAuth2User oauthUser = oauthUser("unverified@example.edu.vn", false, "google-unverified");

        assertThrows(OAuth2AuthenticationException.class, () -> service.processOAuth2User(oauthUser));
        verify(userRepository, never()).findByEmail("unverified@example.edu.vn");
    }

    @Test
    void verifiedGoogleLoginRetainsExistingRoleAndStatus() {
        User existing = new User();
        existing.setId(51L);
        existing.setEmail("manager@example.edu.vn");
        existing.setUsername("manager51");
        existing.setPasswordHash("encoded");
        existing.setFullName("Manager 51");
        existing.setRole(Role.MANAGER);
        existing.setStatus(UserStatus.LOCKED);

        OAuth2User oauthUser = oauthUser(existing.getEmail(), true, "google-51");
        when(userRepository.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));

        CustomUserDetails result = (CustomUserDetails) service.processOAuth2User(oauthUser);

        assertEquals(Role.MANAGER, result.getUser().getRole());
        assertEquals(UserStatus.LOCKED, result.getUser().getStatus());
        verify(userRepository).save(existing);
    }

    private OAuth2User oauthUser(String email, boolean emailVerified, String subject) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", subject,
                        "email", email,
                        "email_verified", emailVerified,
                        "name", "Google User"
                ),
                "sub"
        );
    }
}
