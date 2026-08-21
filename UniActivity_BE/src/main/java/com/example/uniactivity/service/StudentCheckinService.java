package com.example.uniactivity.service;

import com.example.uniactivity.entity.Activity;
import com.example.uniactivity.entity.ActivityRegistration;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.ValidationException;
import com.example.uniactivity.repository.ActivityRegistrationRepository;
import com.example.uniactivity.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StudentCheckinService {

    private static final double MAX_GPS_ACCURACY_METERS = 150.0;

    private final ActivityService activityService;
    private final ActivityRegistrationRepository registrationRepository;
    private final DynamicQrTokenService qrTokenService;
    private final UnifiedCodePolicy codePolicy;

    @Transactional
    public ActivityRegistration checkIn(
            User student, Long activityId, Long classId, String token,
            Double latitude, Double longitude, Double accuracy) {
        if (student == null || student.getStudentClass() == null) {
            throw new ValidationException("Bạn phải tham gia lớp trước khi check-in");
        }
        Long targetClassId = classId != null ? classId : student.getStudentClass().getId();
        if (token == null || token.isBlank()) {
            throw new ValidationException("Thiếu mã QR hoặc mã check-in");
        }
        if (!targetClassId.equals(student.getStudentClass().getId())) {
            throw new ValidationException("Mã check-in này không dành cho lớp của bạn");
        }

        Activity activity = activityService.findActivityById(activityId);
        validateActivityWindow(activity);

        String normalizedToken = codePolicy.normalize(token);
        boolean isValidToken = codePolicy.isValid(normalizedToken)
                ? qrTokenService.validateCheckinCode(normalizedToken, activityId, targetClassId)
                : qrTokenService.validateToken(token, activityId, targetClassId);

        if (!isValidToken) {
            throw new ValidationException("Mã QR hoặc mã check-in không hợp lệ hoặc đã hết hạn");
        }
        validateLocation(activity, latitude, longitude, accuracy);

        ActivityRegistration registration = registrationRepository
                .findByActivityAndStudentForUpdate(activity, student)
                .orElseThrow(() -> new ValidationException("Bạn chưa đăng ký hoạt động này"));
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new ValidationException("Đăng ký không ở trạng thái có thể check-in");
        }
        registration.setStatus(RegistrationStatus.ATTENDED);
        return registrationRepository.save(registration);
    }

    private void validateActivityWindow(Activity activity) {
        if (activity.getStatus() != ActivityStatus.OPEN) {
            throw new ValidationException("Hoạt động không mở check-in");
        }
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            throw new ValidationException("Hoạt động chưa cấu hình thời gian check-in");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new ValidationException("Chưa đến hoặc đã hết thời gian check-in");
        }
    }

    private void validateLocation(
            Activity activity, Double latitude, Double longitude, Double accuracy) {
        if (activity.getLatitude() == null || activity.getLongitude() == null
                || activity.getCheckinRadius() == null || activity.getCheckinRadius() <= 0) {
            return;
        }
        if (latitude == null || longitude == null || accuracy == null) {
            throw new ValidationException("Hoạt động yêu cầu vị trí GPS và độ chính xác");
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || !Double.isFinite(accuracy)) {
            throw new ValidationException("Dữ liệu GPS không hợp lệ");
        }
        if (accuracy < 0 || accuracy > MAX_GPS_ACCURACY_METERS) {
            throw new ValidationException("Độ chính xác GPS không đạt yêu cầu");
        }
        double distance = GeoUtils.haversineMeters(
                activity.getLatitude(), activity.getLongitude(), latitude, longitude);
        if (distance > activity.getCheckinRadius()) {
            throw new ValidationException("Bạn đang ở ngoài phạm vi check-in");
        }
    }
}
