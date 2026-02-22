package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.enums.ActivityScope;
import com.example.uniactivity.enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    
    List<Activity> findBySemester(Semester semester);
    
    List<Activity> findByStatus(ActivityStatus status);
    
    List<Activity> findByScope(ActivityScope scope);
    
    List<Activity> findBySemesterAndStatus(Semester semester, ActivityStatus status);
    
    List<Activity> findAllByOrderByCreatedAtDesc();
    
    List<Activity> findByStatusOrderByCreatedAtDesc(ActivityStatus status);
    
    long countByStatus(ActivityStatus status);
}
