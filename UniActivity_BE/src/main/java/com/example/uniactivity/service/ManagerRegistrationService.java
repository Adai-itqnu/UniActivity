package com.example.uniactivity.service;

import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerRegistrationService {

    private final ActivityRegistrationRepository registrationRepository;
    private final ManagerScopeAuthorizationService scopeAuthorizationService;

    @Transactional
    public ActivityRegistration manualCheckIn(User manager, Long registrationId) {
        ActivityRegistration registration = registrationRepository
                .findByIdForUpdate(registrationId)
                .orElseThrow(() -> new NotFoundException("Đăng ký", registrationId));
        scopeAuthorizationService.assertRegistrationInScope(manager, registration);
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new ConflictException("Đăng ký không ở trạng thái có thể điểm danh");
        }
        registration.setStatus(RegistrationStatus.ATTENDED);
        return registrationRepository.save(registration);
    }
}
