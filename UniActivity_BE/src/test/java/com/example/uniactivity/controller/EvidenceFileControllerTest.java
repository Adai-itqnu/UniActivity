package com.example.uniactivity.controller;

import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.FileUploadService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceFileControllerTest {

    @Test
    void studentCannotReadAnotherStudentsEvidence() {
        FileUploadService files = mock(FileUploadService.class);
        ActivityRegistrationRepository registrations =
                mock(ActivityRegistrationRepository.class);
        PointRequestRepository requests = mock(PointRequestRepository.class);
        EvidenceFileController controller =
                new EvidenceFileController(files, registrations, requests);

        String filename = "11111111-1111-1111-1111-111111111111.png";
        String path = "/uploads/evidence/" + filename;
        ActivityRegistration registration = new ActivityRegistration();
        registration.setStudent(student(2L));
        registration.setEvidenceUrl(path);
        when(registrations.findByEvidenceUrlContaining(path))
                .thenReturn(List.of(registration));
        when(requests.findByEvidenceImageUrlContaining(path)).thenReturn(List.of());

        assertThrows(AuthorizationException.class,
                () -> controller.evidence(new CustomUserDetails(student(1L)), filename));
    }

    private static User student(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.STUDENT);
        return user;
    }
}
