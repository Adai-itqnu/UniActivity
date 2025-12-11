package com.example.uniactivity.repository;

import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.Faculty;
import com.example.uniactivity.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {
    boolean existsByCode(String code);
    boolean existsByJoinCode(String joinCode);
    Optional<StudentClass> findByCode(String code);
    Optional<StudentClass> findByJoinCode(String joinCode);
    List<StudentClass> findByFaculty(Faculty faculty);
    List<StudentClass> findByAcademicYear(AcademicYear academicYear);
    List<StudentClass> findByFacultyAndAcademicYear(Faculty faculty, AcademicYear academicYear);
}
