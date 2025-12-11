package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "student_training_points", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "semester_id"})
})
@Data
public class StudentTrainingPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    private Integer totalScore = 0;

    private String classification;

    private String status = "DRAFT";
}
