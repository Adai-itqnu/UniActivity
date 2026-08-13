package com.example.uniactivity.repository;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassJoinRequestRepository extends JpaRepository<ClassJoinRequest, Long> {
    
    List<ClassJoinRequest> findByStudentClass(StudentClass studentClass);
    
    List<ClassJoinRequest> findByStudentClassAndStatus(StudentClass studentClass, JoinRequestStatus status);
    
    List<ClassJoinRequest> findByUser(User user);
    
    Optional<ClassJoinRequest> findByUserAndStatus(User user, JoinRequestStatus status);
    
    Optional<ClassJoinRequest> findByUserAndStudentClass(User user, StudentClass studentClass);
    
    boolean existsByUserAndStudentClassAndStatus(User user, StudentClass studentClass, JoinRequestStatus status);
    
    long countByStudentClassAndStatus(StudentClass studentClass, JoinRequestStatus status);
    
    // ========================================
    // Optimized queries with JOIN FETCH to prevent N+1
    // ========================================
    
    @Query("SELECT DISTINCT cjr FROM ClassJoinRequest cjr " +
           "JOIN FETCH cjr.user u " +
           "JOIN FETCH cjr.studentClass sc " +
           "LEFT JOIN FETCH sc.faculty " +
           "LEFT JOIN FETCH cjr.processedBy " +
           "WHERE cjr.studentClass = :studentClass " +
           "ORDER BY cjr.createdAt DESC")
    List<ClassJoinRequest> findByStudentClassWithDetails(StudentClass studentClass);
    
    @Query("SELECT DISTINCT cjr FROM ClassJoinRequest cjr " +
           "JOIN FETCH cjr.user u " +
           "JOIN FETCH cjr.studentClass sc " +
           "LEFT JOIN FETCH sc.faculty " +
           "LEFT JOIN FETCH cjr.processedBy " +
           "WHERE cjr.studentClass = :studentClass " +
           "AND cjr.status = :status " +
           "ORDER BY cjr.createdAt DESC")
    List<ClassJoinRequest> findByStudentClassAndStatusWithDetails(StudentClass studentClass, JoinRequestStatus status);
}
