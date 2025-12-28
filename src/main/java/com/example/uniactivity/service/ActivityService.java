package com.example.uniactivity.service;

import com.example.uniactivity.dto.activity.*;
import com.example.uniactivity.entity.*;
import com.example.uniactivity.enums.ActivityStatus;
import com.example.uniactivity.enums.RegistrationStatus;
import com.example.uniactivity.exception.NotFoundException;
import com.example.uniactivity.mapper.ActivityMapper;
import com.example.uniactivity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
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

    @Transactional
    public ActivityResponseDto createActivity(ActivityDto dto) {
        Activity entity = activityMapper.toEntity(dto);
        
        if (entity.getStatus() == null) {
            entity.setStatus(ActivityStatus.DRAFT);
        }
        
        setSemester(entity, dto.getSemesterId());
        return activityMapper.toResponseDto(activityRepository.save(entity));
    }

    @Transactional
    public ActivityResponseDto updateActivity(Long id, ActivityDto dto) {
        Activity entity = findActivityById(id);
        activityMapper.updateEntity(dto, entity);
        setSemester(entity, dto.getSemesterId());
        
        return activityMapper.toResponseDto(activityRepository.save(entity));
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
        
        return activityMapper.toSlotResponseDto(activitySlotRepository.save(entity));
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
}
