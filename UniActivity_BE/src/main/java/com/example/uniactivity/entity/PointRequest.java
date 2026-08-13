package com.example.uniactivity.entity;

import com.example.uniactivity.enums.EvidenceStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_requests", indexes = {
    @Index(name = "idx_pr_student", columnList = "student_id"),
    @Index(name = "idx_pr_semester", columnList = "semester_id"),
    @Index(name = "idx_pr_status", columnList = "status"),
    @Index(name = "idx_pr_reviewer", columnList = "reviewer_id"),
    @Index(name = "idx_pr_created_at", columnList = "created_at")
})
@Data
public class PointRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false)
    private String criteriaCode; // 1.1, 1.3, etc.

    private Integer claimedScore;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String evidenceImageUrl;

    @Enumerated(EnumType.STRING)
    private EvidenceStatus status = EvidenceStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    private LocalDateTime reviewedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
