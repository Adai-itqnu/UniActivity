package com.example.uniactivity.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

@Component
public class UnifiedCodePolicy {
    public static final int CODE_LENGTH = 6;
    public static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final String DIGITS = "23456789";
    public static final String ALPHABET = LETTERS + DIGITS;

    private final RandomGenerator random;

    public UnifiedCodePolicy() {
        this(new SecureRandom());
    }

    UnifiedCodePolicy(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public String generateRandomCode() {
        char[] result = new char[CODE_LENGTH];
        result[0] = pick(LETTERS, random.nextInt(LETTERS.length()));
        result[1] = pick(DIGITS, random.nextInt(DIGITS.length()));
        for (int index = 2; index < CODE_LENGTH; index++) {
            result[index] = pick(ALPHABET, random.nextInt(ALPHABET.length()));
        }
        return new String(result);
    }

    public String deriveCode(byte[] source) {
        if (source == null || source.length < CODE_LENGTH) {
            throw new IllegalArgumentException("Code derivation requires at least 6 bytes");
        }
        char[] result = new char[CODE_LENGTH];
        result[0] = pick(LETTERS, Byte.toUnsignedInt(source[0]) % LETTERS.length());
        result[1] = pick(DIGITS, Byte.toUnsignedInt(source[1]) % DIGITS.length());
        for (int index = 2; index < CODE_LENGTH; index++) {
            result[index] = pick(ALPHABET, Byte.toUnsignedInt(source[index]) % ALPHABET.length());
        }
        return new String(result);
    }

    public String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isValid(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() != CODE_LENGTH) return false;
        boolean letter = false;
        boolean digit = false;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (ALPHABET.indexOf(character) < 0) return false;
            letter |= LETTERS.indexOf(character) >= 0;
            digit |= DIGITS.indexOf(character) >= 0;
        }
        return letter && digit;
    }

    private char pick(String alphabet, int index) {
        return alphabet.charAt(index);
    }
}
