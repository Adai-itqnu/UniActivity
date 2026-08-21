package com.example.uniactivity.service;

import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.repository.ClassJoinRequestRepository;
import com.example.uniactivity.repository.StudentClassRepository;
import com.example.uniactivity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassJoinRequestServiceTest {

    @Mock ClassJoinRequestRepository joinRequestRepository;
    @Mock StudentClassRepository studentClassRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SseEmitterService sseEmitterService;
    @Mock UnifiedCodePolicy codePolicy;
    @InjectMocks ClassJoinRequestService service;

    @Test
    void createJoinRequestNormalizesCodeBeforeClassLookup() {
        User student = new User();
        student.setId(1L);
        StudentClass studentClass = new StudentClass();
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        lenient().when(codePolicy.normalize(" a7k9p2 ")).thenReturn("A7K9P2");
        lenient().when(codePolicy.isValid("A7K9P2")).thenReturn(true);
        lenient().when(studentClassRepository.findByJoinCode(anyString()))
                .thenReturn(Optional.of(studentClass));
        when(joinRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(service.createJoinRequest(student, " a7k9p2 "));

        verify(studentClassRepository).findByJoinCode("A7K9P2");
    }

    @Test
    void createJoinRequestRejectsMalformedCodeBeforeClassLookup() {
        User student = new User();
        student.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(codePolicy.normalize("not-a-code")).thenReturn("NOT-A-CODE");
        when(codePolicy.isValid("NOT-A-CODE")).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> service.createJoinRequest(student, "not-a-code"));

        verify(codePolicy).normalize("not-a-code");
        verify(codePolicy).isValid("NOT-A-CODE");
        verify(studentClassRepository, never()).findByJoinCode(any());
    }
}
