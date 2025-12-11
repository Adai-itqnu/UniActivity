package com.example.uniactivity.repository;

import com.example.uniactivity.entity.AcademicYear;
import com.example.uniactivity.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    boolean existsByCode(String code);
    List<AcademicYear> findByStatus(CommonStatus status);
    List<AcademicYear> findAllByOrderByStartYearDesc();
}
