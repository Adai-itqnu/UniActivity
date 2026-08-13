package com.example.uniactivity.controller.student;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.PointRequestService;
import com.example.uniactivity.service.ScoringRulesService;
import com.example.uniactivity.service.TrainingPointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentDataApiControllerAuthorizationTest {

    @Mock UserRepository userRepository;
    @Mock ActivityRegistrationRepository activityRegistrationRepository;
    @Mock ActivityService activityService;
    @Mock TrainingPointService trainingPointService;
    @Mock PointRequestService pointRequestService;
    @Mock SemesterRepository semesterRepository;
    @Mock ScoringRulesService scoringRulesService;

    @InjectMocks
    StudentDataApiController controller;

    @Test
    void studentCannotReadAnotherUsersScores() {
        User student = new User();
        student.setId(10L);

        assertThrows(AuthorizationException.class,
                () -> controller.getUserScores(new CustomUserDetails(student), 11L));

        verify(userRepository, never()).findById(11L);
    }
}
