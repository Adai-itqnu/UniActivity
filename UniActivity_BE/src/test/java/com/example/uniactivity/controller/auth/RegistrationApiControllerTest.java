package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationApiControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private BindingResult bindingResult;

    private RegistrationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistrationApiController();
        ReflectionTestUtils.setField(controller, "userService", userService);
    }

    @Test
    void unexpectedPersistenceFailureDoesNotExposeDatabaseDetails() {
        UserRegistrationDto dto = validDto();
        when(userService.registerUser(dto)).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry '12345678' for key users.username"
        ));

        ResponseEntity<?> response = controller.registerUser(dto, bindingResult);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                "Không thể tạo tài khoản. Vui lòng thử lại.",
                body(response).get("error")
        );
    }

    @Test
    void knownDuplicateErrorRemainsActionable() {
        UserRegistrationDto dto = validDto();
        when(userService.registerUser(dto)).thenThrow(
                new DuplicateException("Email", dto.getEmail())
        );

        ResponseEntity<?> response = controller.registerUser(dto, bindingResult);

        assertEquals("Email đã tồn tại: student@example.edu.vn", body(response).get("error"));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }
}
