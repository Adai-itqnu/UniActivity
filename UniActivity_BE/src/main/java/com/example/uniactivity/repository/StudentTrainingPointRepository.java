package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface StudentTrainingPointRepository extends JpaRepository<StudentTrainingPoint, Long> {
    
    Optional<StudentTrainingPoint> findByStudentAndSemester(User student, Semester semester);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT stp FROM StudentTrainingPoint stp WHERE stp.student = :student AND stp.semester = :semester")
    Optional<StudentTrainingPoint> findByStudentAndSemesterForUpdate(
            @Param("student") User student, @Param("semester") Semester semester);
    
    boolean existsByStudentAndSemester(User student, Semester semester);
}
