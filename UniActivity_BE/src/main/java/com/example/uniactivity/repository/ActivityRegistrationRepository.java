package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ActivityRegistration r WHERE r.id = :id")
    Optional<ActivityRegistration> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ActivityRegistration r WHERE r.activity = :activity AND r.student = :student")
    Optional<ActivityRegistration> findByActivityAndStudentForUpdate(
            @Param("activity") Activity activity, @Param("student") User student);
    
    List<ActivityRegistration> findByStudentOrderByRegisteredAtDesc(User student);
    
    Optional<ActivityRegistration> findByActivityAndStudent(Activity activity, User student);
    
    boolean existsByActivityAndStudent(Activity activity, User student);
    
    List<ActivityRegistration> findByActivityOrderByRegisteredAtAsc(Activity activity);

    List<ActivityRegistration> findByActivityAndStudent_StudentClassOrderByRegisteredAtAsc(
            Activity activity, StudentClass studentClass);

    Page<ActivityRegistration> findByActivityAndStudent_StudentClassOrderByRegisteredAtAsc(
            Activity activity, StudentClass studentClass, Pageable pageable);
    
    long countByActivity(Activity activity);
    
    long countByActivityAndStatus(Activity activity, RegistrationStatus status);
    
    @Query("SELECT r FROM ActivityRegistration r WHERE r.student = :student AND r.status = :status")
    List<ActivityRegistration> findByStudentAndStatus(@Param("student") User student, @Param("status") RegistrationStatus status);

    @Query("SELECT COUNT(r) FROM ActivityRegistration r WHERE r.student.studentClass = :studentClass " +
            "AND r.evidenceUrl IS NOT NULL AND r.evidenceUrl <> '' AND r.isApproved IS NULL")
    long countPendingEvidenceByStudentClass(@Param("studentClass") StudentClass studentClass);

    List<ActivityRegistration> findByEvidenceUrlContaining(String evidencePath);
    
    // ========================================
    // Optimized queries with JOIN FETCH to prevent N+1
    // ========================================
    
    /**
     * Find registrations by student with eager loading of related entities
     * Prevents N+1 query problem
     */
    @Query("SELECT DISTINCT ar FROM ActivityRegistration ar " +
           "JOIN FETCH ar.student s " +
           "JOIN FETCH ar.activity a " +
           "LEFT JOIN FETCH a.semester " +
           "LEFT JOIN FETCH ar.activitySlot " +
           "LEFT JOIN FETCH ar.scoreOption " +
           "WHERE ar.student = :student " +
           "ORDER BY ar.registeredAt DESC")
    List<ActivityRegistration> findByStudentWithDetailsOrderByRegisteredAtDesc(@Param("student") User student);
    
    /**
     * Find registrations by activity with eager loading
     */
    @Query("SELECT DISTINCT ar FROM ActivityRegistration ar " +
           "JOIN FETCH ar.student s " +
           "LEFT JOIN FETCH s.studentClass " +
           "JOIN FETCH ar.activity a " +
           "LEFT JOIN FETCH ar.activitySlot slot " +
           "LEFT JOIN FETCH ar.scoreOption " +
           "WHERE ar.activity = :activity " +
           "ORDER BY ar.registeredAt ASC")
    List<ActivityRegistration> findByActivityWithDetailsOrderByRegisteredAtAsc(@Param("activity") Activity activity);

    /**
     * Paginated: find registrations by activity with eager loading
     */
    @Query(value = "SELECT DISTINCT ar FROM ActivityRegistration ar " +
           "JOIN FETCH ar.student s " +
           "LEFT JOIN FETCH s.studentClass " +
           "JOIN FETCH ar.activity a " +
           "LEFT JOIN FETCH ar.activitySlot slot " +
           "LEFT JOIN FETCH ar.scoreOption " +
           "WHERE ar.activity = :activity " +
           "ORDER BY ar.registeredAt ASC",
           countQuery = "SELECT COUNT(ar) FROM ActivityRegistration ar WHERE ar.activity = :activity")
    Page<ActivityRegistration> findByActivityWithDetailsPaged(@Param("activity") Activity activity, Pageable pageable);
}
