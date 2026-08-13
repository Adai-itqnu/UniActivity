package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.enums.ActivityScope;
import com.example.uniactivity.enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    
    List<Activity> findBySemester(Semester semester);
    
    List<Activity> findByStatus(ActivityStatus status);
    
    List<Activity> findByScope(ActivityScope scope);
    
    List<Activity> findBySemesterAndStatus(Semester semester, ActivityStatus status);
    
    List<Activity> findAllByOrderByCreatedAtDesc();
    
    List<Activity> findByStatusOrderByCreatedAtDesc(ActivityStatus status);
    
    long countByStatus(ActivityStatus status);
    
    // ========================================
    // Optimized queries with JOIN FETCH to prevent N+1
    // ========================================
    
    /**
     * Find all activities with eager loading of semester and createdBy
     * Prevents N+1 query problem
     */
    @Query("SELECT DISTINCT a FROM Activity a " +
           "LEFT JOIN FETCH a.semester " +
           "LEFT JOIN FETCH a.createdBy " +
           "ORDER BY a.createdAt DESC")
    List<Activity> findAllWithDetailsOrderByCreatedAtDesc();
    
    /**
     * Find activity by ID with eager loading
     */
    @Query("SELECT a FROM Activity a " +
           "LEFT JOIN FETCH a.semester " +
           "LEFT JOIN FETCH a.createdBy " +
           "WHERE a.id = :id")
    Optional<Activity> findByIdWithDetails(@Param("id") Long id);
    
    /**
     * Find activities by status with eager loading
     */
    @Query("SELECT DISTINCT a FROM Activity a " +
           "LEFT JOIN FETCH a.semester " +
           "LEFT JOIN FETCH a.createdBy " +
           "WHERE a.status = :status " +
           "ORDER BY a.createdAt DESC")
    List<Activity> findByStatusWithDetailsOrderByCreatedAtDesc(@Param("status") ActivityStatus status);
}
