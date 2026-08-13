package com.example.uniactivity.service;

import com.example.uniactivity.entity.AcademicYear;
import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivitySlot;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.mapper.ActivityMapper;
import com.example.uniactivity.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityVisibilityScopeTest {

    @Mock ActivityRepository activityRepository;
    @Mock ActivitySlotRepository activitySlotRepository;
    @Mock ScoreOptionRepository scoreOptionRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock FacultyRepository facultyRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock StudentClassRepository studentClassRepository;
    @Mock ActivityMapper activityMapper;
    @Mock ActivityRegistrationRepository activityRegistrationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SseEmitterService sseEmitterService;
    @Mock TransactionTemplate transactionTemplate;
    @InjectMocks ActivityService activityService;

    @Test
    void academicYearOnlySlotIsNotTreatedAsSchoolWide() {
        Activity activity = new Activity();
        activity.setId(10L);
        ActivitySlot slot = new ActivitySlot();
        slot.setAcademicYear(academicYear(2025L));
        when(activitySlotRepository.findByActivityId(10L)).thenReturn(List.of(slot));

        assertFalse(activityService.isActivityVisibleToStudent(activity, student(academicYear(2024L))));
        assertTrue(activityService.isActivityVisibleToStudent(activity, student(academicYear(2025L))));
    }

    private static User student(AcademicYear year) {
        StudentClass studentClass = new StudentClass();
        studentClass.setId(1L);
        studentClass.setAcademicYear(year);
        User student = new User();
        student.setStudentClass(studentClass);
        return student;
    }

    private static AcademicYear academicYear(Long id) {
        AcademicYear year = new AcademicYear();
        year.setId(id);
        return year;
    }
}
