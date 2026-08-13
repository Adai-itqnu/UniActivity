package com.example.uniactivity.entity;

import com.example.uniactivity.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "registrations", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "activity_id"})
    },
    indexes = {
        @Index(name = "idx_reg_student", columnList = "student_id"),
        @Index(name = "idx_reg_activity", columnList = "activity_id"),
        @Index(name = "idx_reg_status", columnList = "status"),
        @Index(name = "idx_reg_registered_at", columnList = "registered_at")
    }
)
@Data
public class Registration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(updatable = false)
    private LocalDateTime registeredAt;

    private LocalDateTime checkInTime;

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
    }
}
