package com.example.uniactivity.entity;

import com.example.uniactivity.enums.CommonStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "academic_years")
@Data
public class AcademicYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private Integer startYear;
    private Integer endYear;

    @Enumerated(EnumType.STRING)
    private CommonStatus status = CommonStatus.ACTIVE;
}
