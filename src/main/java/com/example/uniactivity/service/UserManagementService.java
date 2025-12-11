package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.UserDto;
import com.example.uniactivity.dto.admin.UserResponseDto;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.exception.DuplicateException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.UserMapper;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDto)
                .toList();
    }
    
    public List<UserResponseDto> getUsersByRole(Role role) {
        return userRepository.findByRole(role).stream()
                .map(userMapper::toResponseDto)
                .toList();
    }
    
    public long countStudents() {
        return userRepository.countByRole(Role.STUDENT);
    }
    
    public long countActiveStudents() {
        return userRepository.countByRoleAndStatus(Role.STUDENT, UserStatus.ACTIVE);
    }
    
    public long countAllUsers() {
        return userRepository.count();
    }

    public UserResponseDto getUserById(Long id) {
        return userMapper.toResponseDto(findById(id));
    }

    @Transactional
    public UserResponseDto createUser(UserDto dto) {
        validateUnique(dto, null);
        
        User entity = userMapper.toEntity(dto);
        
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        
        entity.setStatus(UserStatus.ACTIVE);
        setStudentClass(entity, dto.getClassId());
        
        return userMapper.toResponseDto(userRepository.save(entity));
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserDto dto) {
        User entity = findById(id);
        validateUnique(dto, entity);
        
        userMapper.updateEntity(dto, entity);
        setStudentClass(entity, dto.getClassId());
        
        return userMapper.toResponseDto(userRepository.save(entity));
    }
    
    @Transactional
    public void toggleUserStatus(Long id) {
        User entity = findById(id);
        entity.setStatus(entity.getStatus() == UserStatus.ACTIVE ? UserStatus.LOCKED : UserStatus.ACTIVE);
        userRepository.save(entity);
    }
    
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User entity = findById(id);
        entity.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(entity);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        userRepository.delete(findById(id));
    }
    
    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Người dùng", id));
    }
    
    private void validateUnique(UserDto dto, User existing) {
        if (existing == null) {
            // Create mode
            if (userRepository.existsByUsername(dto.getUsername())) {
                throw new DuplicateException("Username", dto.getUsername());
            }
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new DuplicateException("Email", dto.getEmail());
            }
        } else {
            // Update mode
            if (!existing.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
                throw new DuplicateException("Email", dto.getEmail());
            }
        }
    }
    
    private void setStudentClass(User entity, Long classId) {
        entity.setStudentClass(classId != null 
                ? studentClassRepository.findById(classId)
                        .orElseThrow(() -> new NotFoundException("Lớp", classId))
                : null);
    }
}
