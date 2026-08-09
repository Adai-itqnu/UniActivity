package com.example.uniactivity.repository;

import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.TrainingPointDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingPointDetailRepository extends JpaRepository<TrainingPointDetail, Long> {
    
    List<TrainingPointDetail> findByStudentTrainingPoint(StudentTrainingPoint studentTrainingPoint);
    
    Optional<TrainingPointDetail> findByStudentTrainingPointAndCriteriaCode(
            StudentTrainingPoint studentTrainingPoint, String criteriaCode);

    Optional<TrainingPointDetail>
            findByStudentTrainingPointAndCriteriaCodeAndSourceTypeAndReferenceId(
                    StudentTrainingPoint studentTrainingPoint,
                    String criteriaCode,
                    String sourceType,
                    Long referenceId);
    
    void deleteByStudentTrainingPointAndCriteriaCode(
            StudentTrainingPoint studentTrainingPoint, String criteriaCode);
}
