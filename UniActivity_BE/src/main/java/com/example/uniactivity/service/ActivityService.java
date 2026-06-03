package com.example.uniactivity.service;

import com.example.uniactivity.dto.activity.*;
import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.ActivityMapper;
import com.example.uniactivity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivitySlotRepository activitySlotRepository;
    private final ScoreOptionRepository scoreOptionRepository;
    private final SemesterRepository semesterRepository;
    private final FacultyRepository facultyRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentClassRepository studentClassRepository;
    private final ActivityMapper activityMapper;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    // ========================================
    // Activity CRUD
    // ========================================
    
    public List<ActivityResponseDto> getAllActivities() {
        return activityRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(activity -> {
                    ActivityResponseDto dto = activityMapper.toResponseDto(activity);
                    // Calculate stats
                    var slots = activitySlotRepository.findByActivityId(activity.getId());
                    int maxSlots = slots.stream().mapToInt(s -> s.getMaxQuantity() != null ? s.getMaxQuantity() : 0).sum();
                    int registered = slots.stream().mapToInt(s -> s.getCurrentQuantity() != null ? s.getCurrentQuantity() : 0).sum();
                    dto.setMaxSlots(maxSlots);
                    dto.setRegisteredCount(registered);
                    // Count checked-in users
                    int checkedIn = (int) activityRegistrationRepository.countByActivityAndStatus(activity, RegistrationStatus.ATTENDED);
                    dto.setCheckedInCount(checkedIn);
                    // Check if registration deadline has passed
                    dto.setIsDeadlinePassed(activity.getRegistrationDeadline() != null 
                        && activity.getRegistrationDeadline().isBefore(java.time.LocalDateTime.now()));
                    // Check if activity has ended (based on endTime)
                    dto.setIsEnded(activity.getEndTime() != null 
                        && activity.getEndTime().isBefore(java.time.LocalDateTime.now()));
                    return dto;
                })
                .toList();
    }
    
    public ActivityResponseDto getActivityById(Long id) {
        return activityMapper.toResponseDto(findActivityById(id));
    }
    
    public long countActivities() {
        return activityRepository.count();
    }
    
    public long countActiveActivities() {
        return activityRepository.countByStatus(ActivityStatus.OPEN);
    }
    
    public List<ActivityResponseDto> getRecentActivities(int limit) {
        return activityRepository.findAllByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .map(activityMapper::toResponseDto)
                .toList();
    }
    
    /**
     * Get activities visible to a specific student based on their class/faculty
     */
    public List<ActivityResponseDto> getVisibleActivitiesForStudent(User student) {
        // Get all activities, not just OPEN ones - let manager see ended activities too
        return activityRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(activity -> isActivityVisibleToStudent(activity, student))
                .map(activity -> {
                    ActivityResponseDto dto = activityMapper.toResponseDto(activity);
                    var slots = activitySlotRepository.findByActivityId(activity.getId());
                    int maxSlots = slots.stream().mapToInt(s -> s.getMaxQuantity() != null ? s.getMaxQuantity() : 0).sum();
                    int registered = slots.stream().mapToInt(s -> s.getCurrentQuantity() != null ? s.getCurrentQuantity() : 0).sum();
                    dto.setMaxSlots(maxSlots);
                    dto.setRegisteredCount(registered);
                    // Count checked-in users
                    int checkedIn = (int) activityRegistrationRepository.countByActivityAndStatus(activity, RegistrationStatus.ATTENDED);
                    dto.setCheckedInCount(checkedIn);
                    dto.setIsDeadlinePassed(activity.getRegistrationDeadline() != null 
                        && activity.getRegistrationDeadline().isBefore(java.time.LocalDateTime.now()));
                    // Calculate if activity has ended
                    dto.setIsEnded(activity.getEndTime() != null 
                        && activity.getEndTime().isBefore(java.time.LocalDateTime.now()));
                    return dto;
                })
                .toList();
    }
    
    /**
     * Check if an activity is visible to a student based on slot scope
     */
    public boolean isActivityVisibleToStudent(Activity activity, User student) {
        if (student.getStudentClass() == null) {
            return false; // Student must be in a class
        }
        
        var slots = activitySlotRepository.findByActivityId(activity.getId());
        if (slots.isEmpty()) {
            return false; // No slots = not visible
        }
        
        StudentClass studentClass = student.getStudentClass();
        Faculty studentFaculty = studentClass.getFaculty();
        
        for (ActivitySlot slot : slots) {
            // School-wide slot (no faculty, no class)
            if (slot.getFaculty() == null && slot.getStudentClass() == null) {
                return true;
            }
            
            // Faculty-wide slot (matches student's faculty, no specific class)
            if (slot.getFaculty() != null && slot.getStudentClass() == null) {
                if (studentFaculty != null && slot.getFaculty().getId().equals(studentFaculty.getId())) {
                    return true;
                }
            }
            
            // Class-specific slot
            if (slot.getStudentClass() != null) {
                if (slot.getStudentClass().getId().equals(studentClass.getId())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find the matching slot for a student to register
     */
    public ActivitySlot findMatchingSlotForStudent(Activity activity, User student) {
        if (student.getStudentClass() == null) return null;
        
        var slots = activitySlotRepository.findByActivityId(activity.getId());
        StudentClass studentClass = student.getStudentClass();
        Faculty studentFaculty = studentClass.getFaculty();
        
        // Priority: Class-specific > Faculty-wide > School-wide
        for (ActivitySlot slot : slots) {
            if (slot.getStudentClass() != null && slot.getStudentClass().getId().equals(studentClass.getId())) {
                return slot;
            }
        }
        
        for (ActivitySlot slot : slots) {
            if (slot.getFaculty() != null && slot.getStudentClass() == null && studentFaculty != null 
                && slot.getFaculty().getId().equals(studentFaculty.getId())) {
                return slot;
            }
        }
        
        for (ActivitySlot slot : slots) {
            if (slot.getFaculty() == null && slot.getStudentClass() == null) {
                return slot;
            }
        }
        
        return null;
    }

    /**
     * Tạo hoạt động mới
     * Nếu status là OPEN → gửi thông báo cho tất cả sinh viên phù hợp (theo slot scope)
     */
    @Transactional
    public ActivityResponseDto createActivity(ActivityDto dto) {
        Activity entity = activityMapper.toEntity(dto);
        
        if (entity.getStatus() == null) {
            entity.setStatus(ActivityStatus.DRAFT);
        }
        
        setSemester(entity, dto.getSemesterId());
        Activity saved = activityRepository.save(entity);
        
        // Khi tạo hoạt động với status OPEN → thông báo cho sinh viên ngay
        if (saved.getStatus() == ActivityStatus.OPEN) {
            notifyStudentsAboutNewActivity(saved);
        }
        
        return activityMapper.toResponseDto(saved);
    }

    /**
     * Cập nhật hoạt động
     * Nếu status chuyển sang OPEN (từ DRAFT/CLOSED) → gửi thông báo cho sinh viên
     */
    @Transactional
    public ActivityResponseDto updateActivity(Long id, ActivityDto dto) {
        Activity entity = findActivityById(id);
        // Lưu status cũ để so sánh
        ActivityStatus oldStatus = entity.getStatus();
        
        activityMapper.updateEntity(dto, entity);
        setSemester(entity, dto.getSemesterId());
        
        Activity saved = activityRepository.save(entity);
        
        // Khi chuyển sang OPEN (publish) từ trạng thái khác → thông báo sinh viên
        if (saved.getStatus() == ActivityStatus.OPEN && oldStatus != ActivityStatus.OPEN) {
            notifyStudentsAboutNewActivity(saved);
        }
        
        return activityMapper.toResponseDto(saved);
    }
    
    @Transactional
    public void deleteActivity(Long id) {
        Activity entity = findActivityById(id);
        activitySlotRepository.deleteByActivity(entity);
        scoreOptionRepository.deleteByActivity(entity);
        activityRepository.delete(entity);
    }

    // ========================================
    // Activity Slot CRUD
    // ========================================
    
    public List<ActivitySlotResponseDto> getSlotsByActivity(Long activityId) {
        return activitySlotRepository.findByActivityId(activityId).stream()
                .map(activityMapper::toSlotResponseDto)
                .toList();
    }
    
    @Transactional
    public ActivitySlotResponseDto createSlot(Long activityId, ActivitySlotDto dto) {
        Activity activity = findActivityById(activityId);
        
        ActivitySlot entity = activityMapper.toSlotEntity(dto);
        entity.setActivity(activity);
        setSlotRelations(entity, dto);
        
        ActivitySlotResponseDto result = activityMapper.toSlotResponseDto(activitySlotRepository.save(entity));
        
        // Khi thêm slot vào hoạt động OPEN → gửi thông báo cho người dùng phù hợp
        if (activity.getStatus() == ActivityStatus.OPEN) {
            notifyStudentsAboutNewActivity(activity);
        }
        
        return result;
    }
    
    @Transactional
    public void deleteSlot(Long slotId) {
        activitySlotRepository.deleteById(slotId);
    }

    // ========================================
    // Score Option CRUD
    // ========================================
    
    public List<ScoreOptionResponseDto> getScoreOptionsByActivity(Long activityId) {
        return scoreOptionRepository.findByActivityId(activityId).stream()
                .map(activityMapper::toScoreOptionResponseDto)
                .toList();
    }
    
    @Transactional
    public ScoreOptionResponseDto createScoreOption(Long activityId, ScoreOptionDto dto) {
        Activity activity = findActivityById(activityId);
        
        ScoreOption entity = activityMapper.toScoreOptionEntity(dto);
        entity.setActivity(activity);
        
        return activityMapper.toScoreOptionResponseDto(scoreOptionRepository.save(entity));
    }
    
    @Transactional
    public void deleteScoreOption(Long scoreOptionId) {
        scoreOptionRepository.deleteById(scoreOptionId);
    }

    public ScoreOption findScoreOptionById(Long id) {
        return scoreOptionRepository.findById(id).orElse(null);
    }
    
    // ========================================
    // Helper Methods
    // ========================================
    
    public Activity findActivityById(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Hoạt động", id));
    }
    
    private void setSemester(Activity entity, Long semesterId) {
        entity.setSemester(semesterId != null 
                ? semesterRepository.findById(semesterId)
                        .orElseThrow(() -> new NotFoundException("Học kỳ", semesterId))
                : null);
    }
    
    private void setSlotRelations(ActivitySlot entity, ActivitySlotDto dto) {
        entity.setFaculty(dto.getFacultyId() != null 
                ? facultyRepository.findById(dto.getFacultyId())
                        .orElseThrow(() -> new NotFoundException("Khoa", dto.getFacultyId()))
                : null);
        
        entity.setAcademicYear(dto.getAcademicYearId() != null 
                ? academicYearRepository.findById(dto.getAcademicYearId())
                        .orElseThrow(() -> new NotFoundException("Khóa học", dto.getAcademicYearId()))
                : null);
        
        entity.setStudentClass(dto.getClassId() != null 
                ? studentClassRepository.findById(dto.getClassId())
                        .orElseThrow(() -> new NotFoundException("Lớp", dto.getClassId()))
                : null);
    }
    
    // ========================================
    // Notification khi Admin publish hoạt động
    // ========================================
    
    /**
     * Gửi thông báo "Hoạt động mới" cho tất cả sinh viên VÀ manager phù hợp
     * + Gửi SSE event "new_activity" để FE tự re-fetch danh sách activities
     * Xác định người nhận dựa trên slot scope:
     * - Slot toàn trường (không có faculty/class) → tất cả sinh viên + manager có lớp
     * - Slot theo khoa → sinh viên + manager cùng khoa
     * - Slot theo lớp → sinh viên + manager cùng lớp
     */
    private void notifyStudentsAboutNewActivity(Activity activity) {
        try {
            var slots = activitySlotRepository.findByActivityId(activity.getId());
            if (slots.isEmpty()) {
                log.info("Activity {} has no slots, skipping notification", activity.getId());
                return;
            }
            
            // Collect all eligible student + manager IDs dựa trên slot scope
            Set<Long> studentIds = new HashSet<>();
            Set<Long> managerIds = new HashSet<>();
            
            for (ActivitySlot slot : slots) {
                if (slot.getFaculty() == null && slot.getStudentClass() == null) {
                    // Slot toàn trường → lấy tất cả sinh viên + manager có lớp
                    List<User> allStudents = userRepository.findByRoleAndStudentClassIsNotNull(Role.STUDENT);
                    allStudents.forEach(s -> studentIds.add(s.getId()));
                    List<User> allManagers = userRepository.findByRoleAndStudentClassIsNotNull(Role.MANAGER);
                    allManagers.forEach(m -> managerIds.add(m.getId()));
                } else if (slot.getFaculty() != null && slot.getStudentClass() == null) {
                    // Slot theo khoa → lấy sinh viên + manager thuộc khoa đó
                    List<User> facultyStudents = userRepository.findByRoleAndStudentClassFaculty(Role.STUDENT, slot.getFaculty());
                    facultyStudents.forEach(s -> studentIds.add(s.getId()));
                    List<User> facultyManagers = userRepository.findByRoleAndStudentClassFaculty(Role.MANAGER, slot.getFaculty());
                    facultyManagers.forEach(m -> managerIds.add(m.getId()));
                } else if (slot.getStudentClass() != null) {
                    // Slot theo lớp → lấy sinh viên + manager thuộc lớp đó
                    List<User> classUsers = userRepository.findByStudentClass(slot.getStudentClass());
                    classUsers.stream()
                        .filter(u -> u.getRole() == Role.STUDENT)
                        .forEach(s -> studentIds.add(s.getId()));
                    classUsers.stream()
                        .filter(u -> u.getRole() == Role.MANAGER)
                        .forEach(m -> managerIds.add(m.getId()));
                }
            }
            
            // Gửi notification cho từng sinh viên phù hợp
            if (!studentIds.isEmpty()) {
                for (Long studentId : studentIds) {
                    User student = userRepository.findById(studentId).orElse(null);
                    if (student != null) {
                        notificationService.notifyNewActivity(student, activity.getName());
                    }
                }
                log.info("Notified {} students about new activity: {}", studentIds.size(), activity.getName());
            }
            
            // Gửi notification cho từng manager phù hợp
            if (!managerIds.isEmpty()) {
                for (Long managerId : managerIds) {
                    User manager = userRepository.findById(managerId).orElse(null);
                    if (manager != null) {
                        notificationService.notifyNewActivityForManager(manager, activity.getName());
                    }
                }
                log.info("Notified {} managers about new activity: {}", managerIds.size(), activity.getName());
            }
            
            // Gửi SSE event "new_activity" để FE tự re-fetch danh sách activities
            Set<Long> allUserIds = new HashSet<>();
            allUserIds.addAll(studentIds);
            allUserIds.addAll(managerIds);
            if (!allUserIds.isEmpty()) {
                Map<String, Object> ssePayload = new HashMap<>();
                ssePayload.put("activityId", activity.getId());
                ssePayload.put("activityName", activity.getName());
                ssePayload.put("action", "created");
                sseEmitterService.sendToUsers(allUserIds, "new_activity", ssePayload);
                log.info("Sent new_activity SSE event to {} users", allUserIds.size());
            }
        } catch (Exception e) {
            // Không throw exception để không ảnh hưởng đến việc tạo/update activity
            log.error("Failed to notify about activity {}: {}", activity.getId(), e.getMessage());
        }
    }
    
    // ========================================
    // Registration with Optimistic Locking
    // ========================================
    
    /**
     * Đăng ký sinh viên vào hoạt động với Optimistic Locking retry
     * Tránh race condition khi nhiều sinh viên đăng ký cùng lúc
     */
    public Map<String, Object> registerStudentForActivity(User student, Long activityId) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doRegister(student, activityId);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict for activity {} (attempt {}/{})", activityId, attempt, maxRetries);
                if (attempt == maxRetries) {
                    throw new RuntimeException("Hệ thống đang bận, vui lòng thử lại sau giây lát");
                }
                // Wait briefly before retrying
                try { Thread.sleep(50 + (long)(Math.random() * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new RuntimeException("Đăng ký thất bại, vui lòng thử lại");
    }
    
    @Transactional
    private Map<String, Object> doRegister(User student, Long activityId) {
        if (student.getStudentClass() == null) {
            throw new RuntimeException("Bạn phải tham gia lớp trước khi đăng ký hoạt động");
        }
        
        Activity activity = findActivityById(activityId);
        
        // Validate activity status
        if (activity.getStatus() != ActivityStatus.OPEN) {
            throw new RuntimeException("Hoạt động chưa mở đăng ký hoặc đã kết thúc");
        }
        
        // Validate registration deadline
        if (activity.getRegistrationDeadline() != null
                && LocalDateTime.now().isAfter(activity.getRegistrationDeadline())) {
            throw new RuntimeException("Đã hết hạn đăng ký hoạt động này");
        }
        
        // Check if already registered (allow re-registration for CANCELLED)
        var existing = activityRegistrationRepository.findByActivityAndStudent(activity, student);
        if (existing.isPresent()) {
            ActivityRegistration existingReg = existing.get();
            if (existingReg.getStatus() == RegistrationStatus.CANCELLED) {
                // Re-activate the cancelled registration
                existingReg.setStatus(RegistrationStatus.REGISTERED);
                existingReg.setRegisteredAt(LocalDateTime.now());
                existingReg.setEvidenceUrl(null);
                existingReg.setIsApproved(null);
                existingReg.setRejectionReason(null);
                existingReg.setScoreOption(null);
                activityRegistrationRepository.save(existingReg);
                
                // Update slot count (optimistic lock protected)
                if (existingReg.getActivitySlot() != null) {
                    ActivitySlot slot = activitySlotRepository.findById(existingReg.getActivitySlot().getId()).orElse(null);
                    if (slot != null) {
                        slot.setCurrentQuantity(slot.getCurrentQuantity() + 1);
                        activitySlotRepository.save(slot); // @Version will catch conflicts
                    }
                }
                
                // Notify managers about re-registration
                sendRegistrationUpdateToManagers(student, activity, true);
                
                return Map.of("message", "Đăng ký lại thành công!", "registrationId", existingReg.getId());
            }
            throw new RuntimeException("Bạn đã đăng ký hoạt động này rồi");
        }
        
        // Check visibility
        if (!isActivityVisibleToStudent(activity, student)) {
            throw new RuntimeException("Bạn không thể đăng ký hoạt động này");
        }
        
        // Find matching slot (re-read from DB for freshest data)
        ActivitySlot slot = findMatchingSlotForStudent(activity, student);
        if (slot == null) {
            throw new RuntimeException("Không tìm thấy slot phù hợp");
        }
        
        // Re-read slot from DB to get latest version
        slot = activitySlotRepository.findById(slot.getId()).orElseThrow();
        
        // Check slot capacity
        if (slot.getCurrentQuantity() >= slot.getMaxQuantity()) {
            throw new RuntimeException("Slot đã đầy, không thể đăng ký");
        }
        
        // Create registration
        ActivityRegistration reg = new ActivityRegistration();
        reg.setStudent(student);
        reg.setActivity(activity);
        reg.setActivitySlot(slot);
        reg.setStatus(RegistrationStatus.REGISTERED);
        activityRegistrationRepository.save(reg);
        
        // Update slot count (optimistic lock protected by @Version)
        slot.setCurrentQuantity(slot.getCurrentQuantity() + 1);
        activitySlotRepository.save(slot);
        
        // Kiểm tra slot đã đầy chưa → thông báo Admin
        if (slot.getCurrentQuantity() >= slot.getMaxQuantity()) {
            try {
                String slotInfo = "Slot";
                if (slot.getStudentClass() != null) {
                    slotInfo = "Lớp " + slot.getStudentClass().getName();
                } else if (slot.getFaculty() != null) {
                    slotInfo = "Khoa " + slot.getFaculty().getName();
                } else {
                    slotInfo = "Toàn trường";
                }
                notificationService.notifyAdminsSlotFull(activity.getName(), slotInfo + " (" + slot.getCurrentQuantity() + "/" + slot.getMaxQuantity() + ")");
            } catch (Exception e) {
                log.error("Failed to notify admins about slot full: {}", e.getMessage());
            }
        }
        
        // Notify managers
        sendRegistrationUpdateToManagers(student, activity, true);
        
        return Map.of("message", "Đăng ký thành công!", "registrationId", reg.getId());
    }
    
    /**
     * Hủy đăng ký hoạt động với Optimistic Locking retry
     */
    public Map<String, Object> cancelStudentRegistration(User student, Long activityId) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doCancel(student, activityId);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on cancel for activity {} (attempt {}/{})", activityId, attempt, maxRetries);
                if (attempt == maxRetries) {
                    throw new RuntimeException("Hệ thống đang bận, vui lòng thử lại sau giây lát");
                }
                try { Thread.sleep(50 + (long)(Math.random() * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new RuntimeException("Hủy đăng ký thất bại, vui lòng thử lại");
    }
    
    @Transactional
    private Map<String, Object> doCancel(User student, Long activityId) {
        Activity activity = findActivityById(activityId);
        
        var registration = activityRegistrationRepository.findByActivityAndStudent(activity, student);
        if (registration.isEmpty()) {
            throw new RuntimeException("Bạn chưa đăng ký hoạt động này");
        }
        
        ActivityRegistration reg = registration.get();
        
        // Only allow cancelling REGISTERED status
        if (reg.getStatus() != RegistrationStatus.REGISTERED) {
            throw new RuntimeException("Không thể hủy đăng ký ở trạng thái hiện tại");
        }
        
        // Set status to CANCELLED
        reg.setStatus(RegistrationStatus.CANCELLED);
        activityRegistrationRepository.save(reg);
        
        // Decrease slot count (optimistic lock protected)
        if (reg.getActivitySlot() != null) {
            ActivitySlot slot = activitySlotRepository.findById(reg.getActivitySlot().getId()).orElse(null);
            if (slot != null) {
                slot.setCurrentQuantity(Math.max(0, slot.getCurrentQuantity() - 1));
                activitySlotRepository.save(slot);
            }
        }
        
        // Notify managers
        sendRegistrationUpdateToManagers(student, activity, false);
        
        return Map.of("message", "Đã hủy đăng ký thành công");
    }
    
    /**
     * Gửi SSE event + notification cho tất cả Manager có lớp liên quan khi sinh viên đăng ký/hủy
     */
    private void sendRegistrationUpdateToManagers(User student, Activity activity, boolean isRegister) {
        try {
            if (student.getStudentClass() == null) return;
            
            // Tìm tất cả manager của lớp sinh viên
            List<User> managers = userRepository.findByStudentClassAndRole(student.getStudentClass(), Role.MANAGER);
            Set<Long> managerIds = managers.stream().map(User::getId).collect(Collectors.toSet());
            
            // Gửi SSE event "activity_registration_update" để FE tự re-fetch
            if (!managerIds.isEmpty()) {
                Map<String, Object> ssePayload = new HashMap<>();
                ssePayload.put("activityId", activity.getId());
                ssePayload.put("activityName", activity.getName());
                ssePayload.put("studentName", student.getFullName());
                ssePayload.put("action", isRegister ? "registered" : "cancelled");
                sseEmitterService.sendToUsers(managerIds, "activity_registration_update", ssePayload);
            }
        } catch (Exception e) {
            log.error("Failed to send registration update to managers: {}", e.getMessage());
        }
    }
    
    // ========================================
    // Scheduled: Kiểm tra hoạt động hết hạn đăng ký
    // ========================================
    
    /**
     * Kiểm tra mỗi phút: tìm hoạt động OPEN có registrationDeadline vừa qua
     * Gửi thông báo cho Admin
     */
    @Scheduled(fixedRate = 60000) // Mỗi 60 giây
    @Transactional
    public void checkActivityDeadlines() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneMinuteAgo = now.minusMinutes(1);
            
            // Tìm hoạt động OPEN có deadline trong khoảng [now-1min, now]
            List<Activity> activities = activityRepository.findAll().stream()
                .filter(a -> a.getStatus() == ActivityStatus.OPEN)
                .filter(a -> a.getRegistrationDeadline() != null)
                .filter(a -> a.getRegistrationDeadline().isAfter(oneMinuteAgo) && a.getRegistrationDeadline().isBefore(now))
                .collect(Collectors.toList());
            
            for (Activity activity : activities) {
                notificationService.notifyAdminsDeadlinePassed(activity.getName(), activity.getId());
                log.info("Deadline passed notification sent for activity: {} (ID: {})", activity.getName(), activity.getId());
            }
        } catch (Exception e) {
            log.error("Error checking activity deadlines: {}", e.getMessage());
        }
    }
}
