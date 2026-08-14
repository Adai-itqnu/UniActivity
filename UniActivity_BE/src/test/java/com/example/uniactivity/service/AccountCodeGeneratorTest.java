package com.example.uniactivity.service;

import com.example.uniactivity.exception.AccountCodeGenerationException;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountCodeGeneratorTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void retriesCollisionAndReturnsEightDigitCode() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(90_000_000)).thenReturn(1, 2);
        when(userRepository.existsByUsername("10000001")).thenReturn(true);
        when(userRepository.existsByUsername("10000002")).thenReturn(false);

        AccountCodeGenerator generator = new AccountCodeGenerator(userRepository, random);

        assertEquals("10000002", generator.generateUniqueCode());
    }

    @Test
    void validatesExactlyEightDigits() {
        AccountCodeGenerator generator = new AccountCodeGenerator(userRepository, mock(SecureRandom.class));

        assertTrue(generator.isValidCode("12345678"));
        assertFalse(generator.isValidCode("1234567"));
        assertFalse(generator.isValidCode("123456789"));
        assertFalse(generator.isValidCode("manager1"));
        assertFalse(generator.isValidCode(null));
    }

    @Test
    void failsAfterTenCollisions() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(90_000_000)).thenReturn(7);
        when(userRepository.existsByUsername("10000007")).thenReturn(true);
        AccountCodeGenerator generator = new AccountCodeGenerator(userRepository, random);

        AccountCodeGenerationException error = assertThrows(
                AccountCodeGenerationException.class,
                generator::generateUniqueCode
        );

        assertEquals("Không thể tạo mã tài khoản. Vui lòng thử lại.", error.getMessage());
    }
}
