package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Semester findByIsCurrentTrue();
    List<Semester> findAllByOrderByStartDateDesc();
}
