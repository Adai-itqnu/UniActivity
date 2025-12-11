package com.example.uniactivity.repository;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long count();
    
    // Filter methods
    List<User> findByRole(Role role);
    List<User> findByStatus(UserStatus status);
    List<User> findByStudentClass(StudentClass studentClass);
    List<User> findByRoleAndStatus(Role role, UserStatus status);
    long countByRole(Role role);
    long countByRoleAndStatus(Role role, UserStatus status);
    
    // Class members search
    List<User> findByStudentClassAndFullNameContainingIgnoreCaseOrStudentClassAndUsernameContainingIgnoreCase(
        StudentClass class1, String name, StudentClass class2, String username);
    
    long countByStudentClass(StudentClass studentClass);
}
