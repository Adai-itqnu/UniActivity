package com.example.uniactivity.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "evidence_images")
@Data
public class EvidenceImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String imageUrl;
}
