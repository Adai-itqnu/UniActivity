package com.example.uniactivity.service;

import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerRegistrationServiceTest {

    @Mock ActivityRegistrationRepository registrationRepository;
    @Mock ManagerScopeAuthorizationService scopeAuthorizationService;

    private ManagerRegistrationService service;
    private User manager;
    private ActivityRegistration registration;

    @BeforeEach
    void setUp() {
        service = new ManagerRegistrationService(
                registrationRepository, scopeAuthorizationService);
        manager = new User();
        manager.setId(1L);
        registration = new ActivityRegistration();
        registration.setId(9L);
        registration.setStatus(RegistrationStatus.REGISTERED);
    }

    @Test
    void manualCheckinLoadsRegistrationWithWriteLock() {
        when(registrationRepository.findByIdForUpdate(9L))
                .thenReturn(Optional.of(registration));
        when(registrationRepository.save(registration)).thenReturn(registration);

        ActivityRegistration result = service.manualCheckIn(manager, 9L);

        assertEquals(RegistrationStatus.ATTENDED, result.getStatus());
        verify(scopeAuthorizationService).assertRegistrationInScope(manager, registration);
        verify(registrationRepository).save(registration);
    }

    @Test
    void repeatedManualCheckinIsAConflict() {
        registration.setStatus(RegistrationStatus.ATTENDED);
        when(registrationRepository.findByIdForUpdate(9L))
                .thenReturn(Optional.of(registration));

        assertThrows(ConflictException.class,
                () -> service.manualCheckIn(manager, 9L));
    }

    @Test
    void cancelledRegistrationCannotBeCheckedIn() {
        registration.setStatus(RegistrationStatus.CANCELLED);
        when(registrationRepository.findByIdForUpdate(9L))
                .thenReturn(Optional.of(registration));

        assertThrows(ConflictException.class,
                () -> service.manualCheckIn(manager, 9L));
    }
}
