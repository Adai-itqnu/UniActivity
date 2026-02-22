package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {
    
    List<ActivityRegistration> findByStudentOrderByRegisteredAtDesc(User student);
    
    Optional<ActivityRegistration> findByActivityAndStudent(Activity activity, User student);
    
    boolean existsByActivityAndStudent(Activity activity, User student);
    
    List<ActivityRegistration> findByActivityOrderByRegisteredAtAsc(Activity activity);
    
    long countByActivity(Activity activity);
    
    long countByActivityAndStatus(Activity activity, RegistrationStatus status);
    
    @Query("SELECT r FROM ActivityRegistration r WHERE r.student = :student AND r.status = :status")
    List<ActivityRegistration> findByStudentAndStatus(@Param("student") User student, @Param("status") RegistrationStatus status);
}
