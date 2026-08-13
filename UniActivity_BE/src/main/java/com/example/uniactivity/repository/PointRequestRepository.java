package com.example.uniactivity.repository;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PointRequestRepository extends JpaRepository<PointRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PointRequest pr WHERE pr.id = :id")
    Optional<PointRequest> findByIdForUpdate(@Param("id") Long id);
    
    List<PointRequest> findByStudent(User student);
    
    List<PointRequest> findByStudentAndSemester(User student, Semester semester);
    
    List<PointRequest> findByStatus(EvidenceStatus status);
    
    List<PointRequest> findByStudentAndStatus(User student, EvidenceStatus status);
    
    /**
     * Find pending point requests for students in a specific class
     */
    @Query("SELECT pr FROM PointRequest pr WHERE pr.student.studentClass = :studentClass AND pr.status = :status ORDER BY pr.createdAt DESC")
    List<PointRequest> findByStudentClassAndStatus(@Param("studentClass") StudentClass studentClass, @Param("status") EvidenceStatus status);
    
    /**
     * Find all point requests for students in a specific class
     */
    @Query("SELECT pr FROM PointRequest pr WHERE pr.student.studentClass = :studentClass ORDER BY pr.createdAt DESC")
    List<PointRequest> findByStudentClass(@Param("studentClass") StudentClass studentClass);
    
    /**
     * Count pending requests for a class
     */
    @Query("SELECT COUNT(pr) FROM PointRequest pr WHERE pr.student.studentClass = :studentClass AND pr.status = :status")
    long countByStudentClassAndStatus(@Param("studentClass") StudentClass studentClass, @Param("status") EvidenceStatus status);
    
    // ========================================
    // Optimized queries with JOIN FETCH to prevent N+1
    // ========================================
    
    /**
     * Find point requests by class with eager loading of related entities
     * Prevents N+1 query problem
     */
    @Query("SELECT DISTINCT pr FROM PointRequest pr " +
           "JOIN FETCH pr.student s " +
           "JOIN FETCH s.studentClass " +
           "JOIN FETCH pr.semester " +
           "LEFT JOIN FETCH pr.reviewer " +
           "WHERE s.studentClass = :studentClass " +
           "ORDER BY pr.createdAt DESC")
    List<PointRequest> findByStudentClassWithDetails(@Param("studentClass") StudentClass studentClass);
    
    /**
     * Find point requests by class and status with eager loading
     */
    @Query("SELECT DISTINCT pr FROM PointRequest pr " +
           "JOIN FETCH pr.student s " +
           "JOIN FETCH s.studentClass " +
           "JOIN FETCH pr.semester " +
           "LEFT JOIN FETCH pr.reviewer " +
           "WHERE s.studentClass = :studentClass " +
           "AND pr.status = :status " +
           "ORDER BY pr.createdAt DESC")
    List<PointRequest> findByStudentClassAndStatusWithDetails(
        @Param("studentClass") StudentClass studentClass, 
        @Param("status") EvidenceStatus status
    );
    
    /**
     * Find point requests by student with eager loading
     */
    @Query("SELECT DISTINCT pr FROM PointRequest pr " +
           "JOIN FETCH pr.student s " +
           "LEFT JOIN FETCH s.studentClass " +
           "JOIN FETCH pr.semester " +
           "LEFT JOIN FETCH pr.reviewer " +
           "WHERE pr.student = :student " +
           "ORDER BY pr.createdAt DESC")
    List<PointRequest> findByStudentWithDetails(@Param("student") User student);
}
