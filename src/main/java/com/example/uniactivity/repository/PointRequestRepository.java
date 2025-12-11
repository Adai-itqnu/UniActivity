package com.example.uniactivity.repository;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointRequestRepository extends JpaRepository<PointRequest, Long> {
    
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
}
