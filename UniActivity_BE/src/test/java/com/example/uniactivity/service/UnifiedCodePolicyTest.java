package com.example.uniactivity.service;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedCodePolicyTest {
    @Mock RandomGenerator random;

    @Test
    void randomCodeUsesSixApprovedCharactersWithLetterAndDigit() {
        when(random.nextInt(anyInt())).thenReturn(0, 0, 1, 2, 3, 4);
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);

        String code = policy.generateRandomCode();

        assertEquals(6, code.length());
        assertTrue(policy.isValid(code));
        assertTrue(code.chars().anyMatch(Character::isLetter));
        assertTrue(code.chars().anyMatch(Character::isDigit));
    }

    @Test
    void derivedCodeIsDeterministicValidAndMixed() {
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);
        byte[] source = { 1, 2, 3, 4, 5, 6 };

        String first = policy.deriveCode(source);
        String second = policy.deriveCode(source);

        assertEquals(first, second);
        assertTrue(policy.isValid(first));
    }

    @Test
    void normalizationUppercasesAndValidationRejectsAmbiguousCharacters() {
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);

        assertEquals("A7K9P2", policy.normalize(" a7k9p2 "));
        assertTrue(policy.isValid("A7K9P2"));
        assertFalse(policy.isValid("A7O9P2"));
        assertFalse(policy.isValid("ABCDEF"));
        assertFalse(policy.isValid("234567"));
    }
}
