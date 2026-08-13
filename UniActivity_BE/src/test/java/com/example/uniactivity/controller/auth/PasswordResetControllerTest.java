package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.ForgotPasswordRequest;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.PasswordResetTokenRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.service.MailService;
import com.example.uniactivity.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock MailService mailService;
    @Mock OtpService otpService;

    @Test
    void forgotPasswordReturnsSameResponseForKnownAndUnknownEmail() {
        PasswordResetController controller =
                new PasswordResetController(userRepository, tokenRepository, mailService, otpService);
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("student@example.com");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        ResponseEntity<Map<String, String>> unknown = controller.processForgotPassword(request);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setEmailVerified(true);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.countByEmailAndTypeAndCreatedAtAfter(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        when(otpService.issue(request.getEmail(), "PASSWORD_RESET"))
                .thenReturn(new OtpService.IssuedOtp("123456"));
        ResponseEntity<Map<String, String>> known = controller.processForgotPassword(request);

        assertEquals(unknown.getStatusCode(), known.getStatusCode());
        assertEquals(unknown.getBody(), known.getBody());
        assertEquals(200, known.getStatusCode().value());
    }

    @Test
    void forgotPasswordDoesNotSendMailForUnknownEmail() {
        PasswordResetController controller =
                new PasswordResetController(userRepository, tokenRepository, mailService, otpService);
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        controller.processForgotPassword(request);

        verify(mailService, never()).sendEmail(anyString(), anyString(), anyString());
    }
}
