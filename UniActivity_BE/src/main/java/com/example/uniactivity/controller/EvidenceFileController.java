package com.example.uniactivity.controller;

import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.AuthorizationException;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.security.CustomUserDetails;
import com.example.uniactivity.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class EvidenceFileController {

    private final FileUploadService fileUploadService;
    private final ActivityRegistrationRepository registrationRepository;
    private final PointRequestRepository pointRequestRepository;

    @GetMapping("/uploads/evidence/{filename:.+}")
    public ResponseEntity<Resource> evidence(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String filename) {
        User currentUser = userDetails.getUser();
        String evidencePath = "/uploads/evidence/" + filename;
        List<ActivityRegistration> registrations =
                registrationRepository.findByEvidenceUrlContaining(evidencePath);
        List<PointRequest> requests =
                pointRequestRepository.findByEvidenceImageUrlContaining(evidencePath);

        boolean authorized = currentUser.getRole() == Role.ADMIN
                || registrations.stream().anyMatch(r -> ownsExactPath(r, evidencePath, currentUser))
                || requests.stream().anyMatch(r -> ownsExactPath(r, evidencePath, currentUser));
        if (!authorized) {
            throw new AuthorizationException("Bạn không có quyền xem tệp minh chứng này");
        }

        try {
            Path path = fileUploadService.resolveEvidenceFile(filename);
            MediaType mediaType = filename.endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : filename.endsWith(".jpg")
                    ? MediaType.IMAGE_JPEG
                    : MediaType.parseMediaType("image/webp");
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                    .body(new FileSystemResource(path));
        } catch (IOException exception) {
            throw new NotFoundException("Không tìm thấy tệp minh chứng");
        }
    }

    private boolean ownsExactPath(
            ActivityRegistration registration, String path, User currentUser) {
        return containsExactPath(registration.getEvidenceUrl(), path)
                && canViewStudent(currentUser, registration.getStudent());
    }

    private boolean ownsExactPath(PointRequest request, String path, User currentUser) {
        return containsExactPath(request.getEvidenceImageUrl(), path)
                && canViewStudent(currentUser, request.getStudent());
    }

    private boolean canViewStudent(User currentUser, User owner) {
        if (currentUser.getId().equals(owner.getId())) {
            return true;
        }
        if (currentUser.getRole() != Role.MANAGER) {
            return false;
        }
        StudentClass managedClass = currentUser.getStudentClass();
        return managedClass != null && owner.getStudentClass() != null
                && managedClass.getId().equals(owner.getStudentClass().getId());
    }

    private boolean containsExactPath(String storedPaths, String expected) {
        return storedPaths != null && java.util.Arrays.stream(storedPaths.split(","))
                .map(String::trim)
                .anyMatch(expected::equals);
    }
}
