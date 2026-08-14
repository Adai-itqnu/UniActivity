package com.example.uniactivity.service;

import com.example.uniactivity.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class AccountCodeGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final SecureRandom random;

    public AccountCodeGenerator(UserRepository userRepository) {
        this(userRepository, new SecureRandom());
    }

    AccountCodeGenerator(UserRepository userRepository, SecureRandom random) {
        this.userRepository = userRepository;
        this.random = random;
    }

    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = Integer.toString(10_000_000 + random.nextInt(90_000_000));
            if (!userRepository.existsByUsername(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không thể tạo mã tài khoản. Vui lòng thử lại.");
    }

    public boolean isValidCode(String value) {
        return value != null && value.matches("^[0-9]{8}$");
    }
}
