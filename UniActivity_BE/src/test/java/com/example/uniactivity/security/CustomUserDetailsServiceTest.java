package com.example.uniactivity.security;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        user = new User();
        user.setUsername("12345678");
        user.setEmail("student@example.edu.vn");
        user.setPasswordHash("encoded");
        user.setFullName("Student");
        user.setRole(Role.STUDENT);
    }

    @Test
    void loadsAccountByEightDigitCode() {
        when(userRepository.findByUsernameOrEmail("12345678", "12345678"))
                .thenReturn(Optional.of(user));

        CustomUserDetails result = (CustomUserDetails) service.loadUserByUsername("12345678");

        assertSame(user, result.getUser());
        verify(userRepository).findByUsernameOrEmail("12345678", "12345678");
    }

    @Test
    void loadsSameAccountByEmail() {
        when(userRepository.findByUsernameOrEmail(user.getEmail(), user.getEmail()))
                .thenReturn(Optional.of(user));

        CustomUserDetails result = (CustomUserDetails) service.loadUserByUsername(user.getEmail());

        assertSame(user, result.getUser());
        verify(userRepository).findByUsernameOrEmail(user.getEmail(), user.getEmail());
    }
}
