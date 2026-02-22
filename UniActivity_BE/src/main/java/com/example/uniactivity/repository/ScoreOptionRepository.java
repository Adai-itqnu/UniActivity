package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ScoreOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreOptionRepository extends JpaRepository<ScoreOption, Long> {
    
    List<ScoreOption> findByActivity(Activity activity);
    
    List<ScoreOption> findByActivityId(Long activityId);
    
    void deleteByActivity(Activity activity);
    
    void deleteByActivityId(Long activityId);
}
