package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otpCode;

    // "PASSWORD_RESET" hoặc "EMAIL_VERIFY"
    @Column(nullable = false)
    private String type = "PASSWORD_RESET";

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    private boolean used = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
