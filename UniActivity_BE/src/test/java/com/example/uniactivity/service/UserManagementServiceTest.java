package com.example.uniactivity.service;

import com.example.uniactivity.dto.admin.UserDto;
import com.example.uniactivity.dto.admin.UserResponseDto;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.mapper.UserMapper;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StudentClassRepository studentClassRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountCodeGenerator accountCodeGenerator;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userRepository,
                studentClassRepository,
                passwordEncoder,
                new UserMapper(),
                accountCodeGenerator
        );
    }

    @Test
    void creatingStudentIgnoresRequestedUsernameAndGeneratesCode() {
        UserDto dto = dto("forced-name", "STUDENT");
        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(accountCodeGenerator.generateUniqueCode()).thenReturn("12345678");
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDto result = service.createUser(dto);

        assertEquals("12345678", result.getUsername());
        assertEquals("STUDENT", result.getRole());
        verify(userRepository, never()).existsByUsername("forced-name");
    }

    @Test
    void creatingManagerIgnoresRequestedUsernameAndGeneratesCode() {
        UserDto dto = dto("manager-picked", "MANAGER");
        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(accountCodeGenerator.generateUniqueCode()).thenReturn("87654321");
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDto result = service.createUser(dto);

        assertEquals("87654321", result.getUsername());
        assertEquals("MANAGER", result.getRole());
    }

    @Test
    void creatingAdminKeepsAndChecksEnteredUsername() {
        UserDto dto = dto("root-admin", "ADMIN");
        when(userRepository.existsByUsername("root-admin")).thenReturn(false);
        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDto result = service.createUser(dto);

        assertEquals("root-admin", result.getUsername());
        assertEquals("ADMIN", result.getRole());
        verify(userRepository).existsByUsername("root-admin");
        verify(accountCodeGenerator, never()).generateUniqueCode();
    }

    @Test
    void creatingAdminWithoutUsernameIsRejected() {
        UserDto dto = dto(" ", "ADMIN");

        ValidationException error = assertThrows(ValidationException.class, () -> service.createUser(dto));

        assertEquals("Username ADMIN không được để trống", error.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changingAdminToManagerRepairsUsernameAndRevokesTokens() {
        User existing = user(7L, "root-admin", Role.ADMIN, 3L);
        UserDto dto = dto("ignored", "MANAGER");
        dto.setEmail(existing.getEmail());
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(accountCodeGenerator.isValidCode("root-admin")).thenReturn(false);
        when(accountCodeGenerator.generateUniqueCode()).thenReturn("23456789");
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponseDto result = service.updateUser(existing.getId(), dto);

        assertEquals("23456789", result.getUsername());
        assertEquals("MANAGER", result.getRole());
        assertEquals(4L, existing.getTokenVersion());
    }

    @Test
    void changingStudentToAdminKeepsExistingAccountCode() {
        User existing = user(8L, "34567890", Role.STUDENT, 2L);
        UserDto dto = dto("attempted-admin-name", "ADMIN");
        dto.setEmail(existing.getEmail());
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponseDto result = service.updateUser(existing.getId(), dto);

        assertEquals("34567890", result.getUsername());
        assertEquals("ADMIN", result.getRole());
        assertEquals(2L, existing.getTokenVersion());
        verify(accountCodeGenerator, never()).generateUniqueCode();
    }

    @Test
    void updatingManagerWithValidCodeDoesNotGenerateAnotherCode() {
        User existing = user(9L, "45678901", Role.MANAGER, 1L);
        UserDto dto = dto("attempted-change", "MANAGER");
        dto.setEmail(existing.getEmail());
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(accountCodeGenerator.isValidCode("45678901")).thenReturn(true);
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponseDto result = service.updateUser(existing.getId(), dto);

        assertEquals("45678901", result.getUsername());
        assertEquals(1L, existing.getTokenVersion());
        verify(accountCodeGenerator, never()).generateUniqueCode();
    }

    private UserDto dto(String username, String role) {
        UserDto dto = new UserDto();
        dto.setUsername(username);
        dto.setEmail(role.toLowerCase() + "@example.edu.vn");
        dto.setFullName(role + " User");
        dto.setPassword("safe-password");
        dto.setRole(role);
        return dto;
    }

    private User user(long id, String username, Role role, long tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(role.name().toLowerCase() + id + "@example.edu.vn");
        user.setFullName(role + " " + id);
        user.setPasswordHash("encoded");
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
