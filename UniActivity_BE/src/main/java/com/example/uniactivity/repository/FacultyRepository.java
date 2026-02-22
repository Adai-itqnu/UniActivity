package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Faculty;
import com.example.uniactivity.enums.CommonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    boolean existsByCode(String code);
    List<Faculty> findByStatus(CommonStatus status);
}

