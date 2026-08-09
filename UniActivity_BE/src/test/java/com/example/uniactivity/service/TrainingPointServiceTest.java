package com.example.uniactivity.service;

import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.TrainingPointDetail;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.StudentTrainingPointRepository;
import com.example.uniactivity.repository.TrainingPointDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPointServiceTest {

    @Mock StudentTrainingPointRepository studentTrainingPointRepository;
    @Mock TrainingPointDetailRepository detailRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock ScoringRulesService scoringRulesService;

    private TrainingPointService service;
    private User student;
    private Semester semester;
    private StudentTrainingPoint trainingPoint;

    @BeforeEach
    void setUp() {
        service = new TrainingPointService(
                studentTrainingPointRepository, detailRepository,
                semesterRepository, scoringRulesService);
        student = new User();
        student.setId(1L);
        semester = new Semester();
        semester.setId(2L);
        trainingPoint = new StudentTrainingPoint();
        trainingPoint.setId(3L);
        trainingPoint.setStudent(student);
        trainingPoint.setSemester(semester);
    }

    @Test
    void sumsDifferentActivityContributionsForSameCriteria() {
        when(semesterRepository.findByIsCurrentTrue()).thenReturn(semester);
        when(studentTrainingPointRepository.findByStudentAndSemester(student, semester))
                .thenReturn(Optional.of(trainingPoint));
        when(detailRepository.findByStudentTrainingPoint(trainingPoint))
                .thenReturn(List.of(detail("3.1", 3, 100L), detail("3.1", 5, 101L)));

        Map<String, Integer> result = service.getScoresByCriteria(student);

        assertEquals(8, result.get("3.1"));
    }

    @Test
    void sameSourceReferenceDoesNotCreateSecondContribution() {
        when(semesterRepository.findByIsCurrentTrue()).thenReturn(semester);
        when(studentTrainingPointRepository.findByStudentAndSemester(student, semester))
                .thenReturn(Optional.of(trainingPoint));
        when(detailRepository
                .findByStudentTrainingPointAndCriteriaCodeAndSourceTypeAndReferenceId(
                        trainingPoint, "3.1", "AUTO_ACTIVITY", 100L))
                .thenReturn(Optional.of(detail("3.1", 3, 100L)));

        boolean added = service.addScoreOnce(
                student, "3.1", 3, "AUTO_ACTIVITY", 100L, "Hoạt động");

        assertFalse(added);
    }

    private TrainingPointDetail detail(String criteria, int score, Long reference) {
        TrainingPointDetail detail = new TrainingPointDetail();
        detail.setStudentTrainingPoint(trainingPoint);
        detail.setCriteriaCode(criteria);
        detail.setScore(score);
        detail.setSourceType("AUTO_ACTIVITY");
        detail.setReferenceId(reference);
        return detail;
    }
}
