package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "training_point_details")
@Data
public class TrainingPointDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_training_point_id", nullable = false)
    private StudentTrainingPoint studentTrainingPoint;

    @Column(nullable = false)
    private String criteriaCode;

    @Column(nullable = false)
    private Integer score;

    private String sourceType; // ACTIVITY, MANUAL, VIOLATION, AUTO_GPA

    private Long referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
