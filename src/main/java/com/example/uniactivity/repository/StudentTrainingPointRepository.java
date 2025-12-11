package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentTrainingPointRepository extends JpaRepository<StudentTrainingPoint, Long> {
    
    Optional<StudentTrainingPoint> findByStudentAndSemester(User student, Semester semester);
    
    boolean existsByStudentAndSemester(User student, Semester semester);
}
