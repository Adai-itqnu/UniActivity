package com.example.uniactivity.service;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Random RANDOM = new Random();

    @Transactional
    public User registerUser(UserRegistrationDto registrationDto) {
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new RuntimeException("Email đã tồn tại");
        }

        // Tự sinh mã sinh viên 8 chữ số ngẫu nhiên, đảm bảo không trùng
        String username = generateUniqueUsername();

        User user = new User();
        user.setFullName(registrationDto.getFullName());
        user.setUsername(username);
        user.setEmail(registrationDto.getEmail());
        user.setPhone(registrationDto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(registrationDto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);

        // Logic: Người đầu tiên là ADMIN, sau đó là STUDENT
        if (userRepository.count() == 0) {
            user.setRole(Role.ADMIN);
        } else {
            user.setRole(Role.STUDENT);
        }

        userRepository.save(user);
        return user;
    }

    /**
     * Sinh mã sinh viên 8 chữ số ngẫu nhiên (10000000 - 99999999).
     * Retry nếu bị trùng (xác suất cực thấp).
     */
    private String generateUniqueUsername() {
        for (int i = 0; i < 10; i++) {
            String code = String.valueOf(10000000 + RANDOM.nextInt(90000000));
            if (!userRepository.existsByUsername(code)) {
                return code;
            }
        }
        throw new RuntimeException("Không thể tạo mã sinh viên. Vui lòng thử lại.");
    }
}
