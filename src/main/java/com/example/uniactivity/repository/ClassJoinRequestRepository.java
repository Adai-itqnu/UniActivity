package com.example.uniactivity.repository;

import com.example.uniactivity.entity.ClassJoinRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
