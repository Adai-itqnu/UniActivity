package com.example.uniactivity.service;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountCodeGenerator accountCodeGenerator;

    @Transactional
    public User registerUser(UserRegistrationDto registrationDto) {
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new DuplicateException("Email", registrationDto.getEmail());
        }

        // Tự sinh mã tài khoản 8 chữ số ngẫu nhiên, đảm bảo không trùng
        String username = accountCodeGenerator.generateUniqueCode();

        User user = new User();
        user.setFullName(registrationDto.getFullName());
        user.setUsername(username);
        user.setEmail(registrationDto.getEmail());
        user.setPhone(registrationDto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(registrationDto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);

        // Đăng ký công khai không bao giờ được cấp quyền đặc biệt.
        // ADMIN và MANAGER chỉ được tạo qua luồng quản trị có xác thực.
        user.setRole(Role.STUDENT);

        userRepository.save(user);
        return user;
    }
}
