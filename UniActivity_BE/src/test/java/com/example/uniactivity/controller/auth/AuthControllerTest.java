package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private Model model;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService);
    }

    @Test
    void unexpectedPersistenceFailureDoesNotExposeDatabaseDetails() {
        UserRegistrationDto dto = validDto();
        when(userService.registerUser(dto)).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry '12345678' for key users.username"
        ));

        String view = controller.registerUserAccount(dto, bindingResult, model);

        assertEquals("auth/register", view);
        verify(model).addAttribute("error", "Không thể tạo tài khoản. Vui lòng thử lại.");
    }

    @Test
    void unrelatedIllegalStateExceptionIsSanitized() {
        UserRegistrationDto dto = validDto();
        when(userService.registerUser(dto)).thenThrow(
                new IllegalStateException("Bean factory configuration at /internal/path")
        );

        String view = controller.registerUserAccount(dto, bindingResult, model);

        assertEquals("auth/register", view);
        verify(model).addAttribute("error", "Không thể tạo tài khoản. Vui lòng thử lại.");
    }

    private UserRegistrationDto validDto() {
        UserRegistrationDto dto = new UserRegistrationDto();
        dto.setFullName("Student");
        dto.setEmail("student@example.edu.vn");
        dto.setPassword("safe-password");
        dto.setConfirmPassword("safe-password");
        dto.setTermsAccepted(true);
        return dto;
    }
}
