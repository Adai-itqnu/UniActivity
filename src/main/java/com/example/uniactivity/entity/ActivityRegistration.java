package com.example.uniactivity.entity;

import com.example.uniactivity.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_registrations")
@Data
public class ActivityRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne
    @JoinColumn(name = "activity_slot_id")
    private ActivitySlot activitySlot;

    @ManyToOne
    @JoinColumn(name = "score_option_id")
    private ScoreOption scoreOption;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(name = "attendance_confirmed")
    private Boolean attendanceConfirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
