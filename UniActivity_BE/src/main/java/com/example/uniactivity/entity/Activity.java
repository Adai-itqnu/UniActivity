package com.example.uniactivity.entity;

import com.example.uniactivity.enums.ActivityScope;
import com.example.uniactivity.enums.ActivityStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "activities")
@Data
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String bannerUrl;

    private String location;

    // GPS coordinates cho check-in theo vị trí
    private Double latitude;
    private Double longitude;

    // Bán kính cho phép check-in (mét), null = không giới hạn vị trí
    @Column(name = "checkin_radius")
    private Integer checkinRadius;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;

    @Enumerated(EnumType.STRING)
    private ActivityStatus status = ActivityStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityScope scope;

    @ManyToOne
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
