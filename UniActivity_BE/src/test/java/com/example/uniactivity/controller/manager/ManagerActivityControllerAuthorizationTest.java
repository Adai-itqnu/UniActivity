package com.example.uniactivity.controller.manager;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ActivityService;
import com.example.uniactivity.service.DynamicQrTokenService;
import com.example.uniactivity.service.EvidenceReviewService;
import com.example.uniactivity.service.ManagerScopeAuthorizationService;
import com.example.uniactivity.service.NotificationService;
import com.example.uniactivity.service.QrCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerActivityControllerAuthorizationTest {

    @Mock ActivityService activityService;
    @Mock QrCodeService qrCodeService;
    @Mock DynamicQrTokenService dynamicQrTokenService;
    @Mock ActivityRegistrationRepository activityRegistrationRepository;
    @Mock NotificationService notificationService;
    @Mock ManagerScopeAuthorizationService managerScopeAuthorizationService;
    @Mock EvidenceReviewService evidenceReviewService;
    @Mock Model model;

    @InjectMocks
    ManagerActivityController controller;

    @Test
    void activityDetailLoadsActivityThroughManagerScope() {
        User manager = new User();
        manager.setId(7L);
        Activity activity = new Activity();
        activity.setId(50L);
        when(managerScopeAuthorizationService.requireActivity(manager, 50L))
                .thenReturn(activity);

        String view = controller.activityDetail(
                new CustomUserDetails(manager), 50L, model);

        assertEquals("manager/activity-detail", view);
        verify(managerScopeAuthorizationService).requireActivity(manager, 50L);
        verify(activityService, never()).getActivityById(50L);
        verify(model).addAttribute("activity", activity);
    }
}
