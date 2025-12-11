package com.example.uniactivity.repository;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivitySlotRepository extends JpaRepository<ActivitySlot, Long> {
    
    List<ActivitySlot> findByActivity(Activity activity);
    
    List<ActivitySlot> findByActivityId(Long activityId);
    
    void deleteByActivity(Activity activity);
    
    void deleteByActivityId(Long activityId);
}
