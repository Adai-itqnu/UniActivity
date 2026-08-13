package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.ScoreOption;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ConflictException;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvidenceSubmissionService {

    private static final int MAX_FILES = 3;

    private final ActivityService activityService;
    private final ActivityRegistrationRepository registrationRepository;
    private final EvidenceReviewService evidenceReviewService;
    private final FileUploadService fileUploadService;

    @Transactional
    public EvidenceSubmissionResult submit(
            User student, Long activityId, Long scoreOptionId, List<MultipartFile> files) {
        Activity activity = activityService.findActivityById(activityId);
        ActivityRegistration registration = registrationRepository
                .findByActivityAndStudentForUpdate(activity, student)
                .orElseThrow(() -> new ValidationException("Bạn chưa đăng ký hoạt động này"));
        if (registration.getStatus() != RegistrationStatus.ATTENDED) {
            throw new ValidationException("Bạn cần check-in trước khi nộp minh chứng");
        }
        if (Boolean.TRUE.equals(registration.getIsApproved())) {
            throw new ConflictException("Minh chứng đã được duyệt, không thể nộp lại");
        }
        if (registration.getIsApproved() == null
                && registration.getEvidenceUrl() != null
                && !registration.getEvidenceUrl().isBlank()) {
            throw new ConflictException("Minh chứng đang chờ duyệt, không thể thay đổi");
        }
        validateFiles(files);
        ScoreOption scoreOption =
                evidenceReviewService.requireScoreOption(activityId, scoreOptionId);

        List<String> uploadedPaths;
        try {
            uploadedPaths = fileUploadService.uploadEvidenceImages(
                    files.toArray(MultipartFile[]::new));
        } catch (IOException e) {
            throw new ValidationException("Không thể lưu ảnh minh chứng");
        }
        if (uploadedPaths.isEmpty()) {
            throw new ValidationException("Không có ảnh minh chứng hợp lệ");
        }

        registration.setScoreOption(scoreOption);
        registration.setEvidenceUrl(String.join(",", uploadedPaths));
        registration.setIsApproved(null);
        registration.setRejectionReason(null);
        registrationRepository.save(registration);
        return new EvidenceSubmissionResult(registration, uploadedPaths);
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("Vui lòng chọn ít nhất 1 ảnh");
        }
        if (files.size() > MAX_FILES) {
            throw new ValidationException("Tối đa 3 ảnh");
        }
        for (MultipartFile file : files) {
            if (!fileUploadService.isValidImage(file)) {
                throw new ValidationException("Chỉ chấp nhận tệp ảnh hợp lệ");
            }
            if (file.getSize() > fileUploadService.getMaxFileSize()) {
                throw new ValidationException("Mỗi ảnh không được vượt quá 5MB");
            }
        }
    }

    public record EvidenceSubmissionResult(
            ActivityRegistration registration, List<String> paths) {
    }
}
