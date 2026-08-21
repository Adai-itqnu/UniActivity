package com.example.uniactivity.controller.manager;

import com.example.uniactivity.dto.admin.StudentClassResponseDto;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.ClassJoinRequestService;
import com.example.uniactivity.service.ManagerScopeAuthorizationService;
import com.example.uniactivity.service.NotificationService;
import com.example.uniactivity.service.StudentClassService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerMemberControllerTest {

    @Mock UserRepository userRepository;
    @Mock ClassJoinRequestService classJoinRequestService;
    @Mock StudentClassRepository studentClassRepository;
    @Mock NotificationService notificationService;
    @Mock ManagerScopeAuthorizationService managerScopeAuthorizationService;
    @Mock StudentClassService studentClassService;
    @InjectMocks ManagerMemberController controller;

    @Test
    void regenerateJoinCodeDelegatesMutationToStudentClassService() {
        User manager = new User();
        StudentClass studentClass = new StudentClass();
        studentClass.setId(10L);
        StudentClassResponseDto responseWithCode = new StudentClassResponseDto();
        responseWithCode.setJoinCode("A7K9P2");
        when(managerScopeAuthorizationService.requireManagedClass(manager)).thenReturn(studentClass);
        when(studentClassService.regenerateJoinCode(10L)).thenReturn(responseWithCode);
        CustomUserDetails userDetails = new CustomUserDetails(manager);

        ResponseEntity<?> response = controller.regenerateJoinCode(userDetails);

        verify(studentClassService).regenerateJoinCode(10L);
        verifyNoInteractions(studentClassRepository);
        assertEquals("A7K9P2", ((Map<?, ?>) response.getBody()).get("joinCode"));
    }
}
