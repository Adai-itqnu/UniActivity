package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "score_options")
@Data
public class ScoreOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String scoreCategory;

    @Column(nullable = false)
    private Integer scoreValue;

    @Column(columnDefinition = "TEXT")
    private String description;
}
