package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "activity_slots")
@Data
public class ActivitySlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @ManyToOne
    @JoinColumn(name = "class_id")
    private StudentClass studentClass;

    @Column(nullable = false)
    private Integer maxQuantity;

    private Integer currentQuantity = 0;
}
