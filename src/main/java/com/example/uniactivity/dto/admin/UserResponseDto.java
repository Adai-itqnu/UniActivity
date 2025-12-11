package com.example.uniactivity.dto.admin;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponseDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    
    // Flattened class info
    private Long classId;
    private String className;
}
