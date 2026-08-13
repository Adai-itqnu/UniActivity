# Kế Hoạch Fix Backend Optimization - UniActivity
**Người thực hiện:** Daidev  
**Ngày bắt đầu:** 13/08/2026  
**Ước tính hoàn thành:** 19/08/2026 (6-7 ngày làm việc)

---

## 📅 Timeline Tổng Quan

```
Ngày 1-2:  Phase 1 - Critical Performance (N+1 Queries + Indexes)
Ngày 3-4:  Phase 2A - Transaction Management + Code Duplication  
Ngày 5:    Phase 2B - REST API Standardization + Validation
Ngày 6:    Phase 3 - Dependencies + Exception Handling
Ngày 7:    Testing & Verification
```

---

## 🚀 PHASE 1: Critical Performance (Ngày 1-2)
**Ưu tiên:** 🔴 CRITICAL  
**Thời gian ước tính:** 12-16 giờ

### Day 1 Morning: Fix N+1 Queries - Part 1 (4 giờ)

#### ✅ Task 1.1: ActivityRepository + ActivityService (90 phút)
**File cần sửa:**
- `UniActivity_BE/src/main/java/com/example/uniactivity/repository/ActivityRepository.java`
- `UniActivity_BE/src/main/java/com/example/uniactivity/service/ActivityService.java`

**Chi tiết:**
```java
// 1. Mở ActivityRepository.java
// 2. Thêm method mới:

@Query("SELECT DISTINCT a FROM Activity a " +
       "LEFT JOIN FETCH a.semester " +
       "LEFT JOIN FETCH a.createdBy " +
       "WHERE a.status = :status " +
       "ORDER BY a.createdAt DESC")
List<Activity> findAllByStatusWithDetails(@Param("status") ActivityStatus status);

@Query("SELECT DISTINCT a FROM Activity a " +
       "LEFT JOIN FETCH a.semester " +
       "LEFT JOIN FETCH a.createdBy " +
       "WHERE a.id = :id")
Optional<Activity> findByIdWithDetails(@Param("id") Long id);

// 3. Mở ActivityService.java
// 4. Tìm method getAllActivities() (line ~44)
// 5. Thay đổi:
//    FROM: List<Activity> activities = activityRepository.findAll();
//    TO:   List<Activity> activities = activityRepository.findAllByStatusWithDetails(ActivityStatus.ACTIVE);

// 6. Tìm method getVisibleActivitiesForStudent() (line ~90)
// 7. Apply tương tự cho các query trong method này
```

**Testing:**
```bash
# Test endpoint
curl -X GET http://localhost:8080/student/api/activities \
  -H "Authorization: Bearer YOUR_TOKEN"

# Check logs - should see 1 query instead of N+1
```

---

#### ✅ Task 1.2: PointRequestRepository + Service (90 phút)
**File cần sửa:**
- `UniActivity_BE/src/main/java/com/example/uniactivity/repository/PointRequestRepository.java`
- `UniActivity_BE/src/main/java/com/example/uniactivity/service/PointRequestService.java`

**Chi tiết:**
```java
// PointRequestRepository.java - THÊM methods:

@Query("SELECT DISTINCT pr FROM PointRequest pr " +
       "JOIN FETCH pr.student s " +
       "JOIN FETCH s.studentClass " +
       "JOIN FETCH pr.semester " +
       "LEFT JOIN FETCH pr.reviewedBy " +
       "WHERE s.studentClass.id = :classId " +
       "AND pr.status = :status")
List<PointRequest> findByClassIdAndStatusWithDetails(
    @Param("classId") Long classId,
    @Param("status") EvidenceStatus status
);

@Query("SELECT DISTINCT pr FROM PointRequest pr " +
       "JOIN FETCH pr.student " +
       "JOIN FETCH pr.semester " +
       "LEFT JOIN FETCH pr.reviewedBy " +
       "WHERE pr.id = :id")
Optional<PointRequest> findByIdWithDetails(@Param("id") Long id);

// PointRequestService.java - UPDATE methods để dùng new queries
// Line ~50: getPointRequestsByClass()
// Line ~119: reviewPointRequest() 
```

**Testing:**
```bash
curl -X GET http://localhost:8080/manager/api/point-requests?status=PENDING \
  -H "Authorization: Bearer MANAGER_TOKEN"
```

---

#### ✅ Task 1.3: ActivityRegistrationRepository (60 phút)
**File:** `UniActivity_BE/src/main/java/com/example/uniactivity/repository/ActivityRegistrationRepository.java`

```java
@Query("SELECT DISTINCT ar FROM ActivityRegistration ar " +
       "JOIN FETCH ar.student s " +
       "JOIN FETCH ar.activity a " +
       "LEFT JOIN FETCH ar.activitySlot " +
       "LEFT JOIN FETCH ar.scoreOption " +
       "WHERE s.id = :studentId")
List<ActivityRegistration> findByStudentIdWithDetails(@Param("studentId") Long studentId);

@Query("SELECT DISTINCT ar FROM ActivityRegistration ar " +
       "JOIN FETCH ar.student " +
       "JOIN FETCH ar.activity " +
       "WHERE ar.activity.id = :activityId")
List<ActivityRegistration> findByActivityIdWithDetails(@Param("activityId") Long activityId);
```

---

### Day 1 Afternoon: Add Database Indexes (4 giờ)

#### ✅ Task 1.4: Entity Indexes - Part 1 (120 phút)

**File 1: Activity.java**
```java
// UniActivity_BE/src/main/java/com/example/uniactivity/entity/Activity.java

// Tìm @Entity annotation (line ~20)
// THÊM @Table annotation với indexes:

@Entity
@Table(name = "activities", indexes = {
    @Index(name = "idx_activity_semester", columnList = "semester_id"),
    @Index(name = "idx_activity_status", columnList = "status"),
    @Index(name = "idx_activity_scope", columnList = "scope"),
    @Index(name = "idx_activity_created_by", columnList = "created_by_id"),
    @Index(name = "idx_activity_reg_dates", columnList = "registration_start_date, registration_end_date")
})
public class Activity {
    // existing code...
}
```

**File 2: ActivityRegistration.java**
```java
@Entity
@Table(name = "activity_registrations", indexes = {
    @Index(name = "idx_ar_student", columnList = "student_id"),
    @Index(name = "idx_ar_activity", columnList = "activity_id"),
    @Index(name = "idx_ar_status", columnList = "status"),
    @Index(name = "idx_ar_student_activity", columnList = "student_id, activity_id"),
    @Index(name = "idx_ar_created", columnList = "created_at")
})
public class ActivityRegistration {
    // existing code...
}
```

**File 3: PointRequest.java**
```java
@Entity
@Table(name = "point_requests", indexes = {
    @Index(name = "idx_pr_student", columnList = "student_id"),
    @Index(name = "idx_pr_semester", columnList = "semester_id"),
    @Index(name = "idx_pr_status", columnList = "status"),
    @Index(name = "idx_pr_reviewer", columnList = "reviewed_by_id"),
    @Index(name = "idx_pr_created", columnList = "created_at")
})
public class PointRequest {
    // existing code...
}
```

**File 4: ClassJoinRequest.java**
```java
@Entity
@Table(name = "class_join_requests", indexes = {
    @Index(name = "idx_cjr_student", columnList = "student_id"),
    @Index(name = "idx_cjr_class", columnList = "student_class_id"),
    @Index(name = "idx_cjr_status", columnList = "status"),
    @Index(name = "idx_cjr_created", columnList = "created_at")
})
public class ClassJoinRequest {
    // existing code...
}
```

---

#### ✅ Task 1.5: Entity Indexes - Part 2 (120 phút)

**File 5: Registration.java**
```java
@Entity
@Table(name = "registrations", indexes = {
    @Index(name = "idx_reg_student", columnList = "student_id"),
    @Index(name = "idx_reg_slot", columnList = "activity_slot_id"),
    @Index(name = "idx_reg_student_slot", columnList = "student_id, activity_slot_id"),
    @Index(name = "idx_reg_checkin_time", columnList = "check_in_time")
})
public class Registration {
    // existing code...
}
```

**File 6: CheckInSession.java**
```java
@Entity
@Table(name = "check_in_sessions", indexes = {
    @Index(name = "idx_checkin_slot", columnList = "activity_slot_id"),
    @Index(name = "idx_checkin_active", columnList = "is_active"),
    @Index(name = "idx_checkin_expires", columnList = "expires_at"),
    @Index(name = "idx_checkin_created", columnList = "created_at")
})
public class CheckInSession {
    // existing code...
}
```

**File 7: Notification.java**
```java
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user", columnList = "user_id"),
    @Index(name = "idx_notif_read", columnList = "is_read"),
    @Index(name = "idx_notif_type", columnList = "notification_type"),
    @Index(name = "idx_notif_created", columnList = "created_at"),
    @Index(name = "idx_notif_user_read", columnList = "user_id, is_read")
})
public class Notification {
    // existing code...
}
```

**File 8: Evidence.java** (nếu có)
```java
@Entity
@Table(name = "evidences", indexes = {
    @Index(name = "idx_evidence_registration", columnList = "registration_id"),
    @Index(name = "idx_evidence_status", columnList = "status")
})
```

---

### Day 2 Morning: Apply Indexes to Database (2 giờ)

#### ✅ Task 1.6: Generate and Run Migration (120 phút)

**Bước 1: Update application.properties để generate DDL**
```properties
# Thêm vào application.properties (temporary)
spring.jpa.properties.javax.persistence.schema-generation.scripts.action=update
spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target=create.sql
spring.jpa.properties.javax.persistence.schema-generation.scripts.drop-target=drop.sql
```

**Bước 2: Run application để generate SQL**
```bash
cd UniActivity_BE
./mvnw spring-boot:run
# Ctrl+C sau khi app start xong
```

**Bước 3: Extract CREATE INDEX statements**
```bash
# File create.sql sẽ chứa các CREATE INDEX statements
# Copy các statements này vào file migration
```

**Bước 4: Tạo migration file**
```sql
-- UniActivity_BE/src/main/resources/db/migration/V2__add_performance_indexes.sql

-- Activity indexes
CREATE INDEX idx_activity_semester ON activities(semester_id);
CREATE INDEX idx_activity_status ON activities(status);
CREATE INDEX idx_activity_scope ON activities(scope);
CREATE INDEX idx_activity_created_by ON activities(created_by_id);
CREATE INDEX idx_activity_reg_dates ON activities(registration_start_date, registration_end_date);

-- ActivityRegistration indexes
CREATE INDEX idx_ar_student ON activity_registrations(student_id);
CREATE INDEX idx_ar_activity ON activity_registrations(activity_id);
CREATE INDEX idx_ar_status ON activity_registrations(status);
CREATE INDEX idx_ar_student_activity ON activity_registrations(student_id, activity_id);
CREATE INDEX idx_ar_created ON activity_registrations(created_at);

-- PointRequest indexes
CREATE INDEX idx_pr_student ON point_requests(student_id);
CREATE INDEX idx_pr_semester ON point_requests(semester_id);
CREATE INDEX idx_pr_status ON point_requests(status);
CREATE INDEX idx_pr_reviewer ON point_requests(reviewed_by_id);
CREATE INDEX idx_pr_created ON point_requests(created_at);

-- ClassJoinRequest indexes
CREATE INDEX idx_cjr_student ON class_join_requests(student_id);
CREATE INDEX idx_cjr_class ON class_join_requests(student_class_id);
CREATE INDEX idx_cjr_status ON class_join_requests(status);
CREATE INDEX idx_cjr_created ON class_join_requests(created_at);

-- Registration indexes
CREATE INDEX idx_reg_student ON registrations(student_id);
CREATE INDEX idx_reg_slot ON registrations(activity_slot_id);
CREATE INDEX idx_reg_student_slot ON registrations(student_id, activity_slot_id);
CREATE INDEX idx_reg_checkin_time ON registrations(check_in_time);

-- CheckInSession indexes
CREATE INDEX idx_checkin_slot ON check_in_sessions(activity_slot_id);
CREATE INDEX idx_checkin_active ON check_in_sessions(is_active);
CREATE INDEX idx_checkin_expires ON check_in_sessions(expires_at);
CREATE INDEX idx_checkin_created ON check_in_sessions(created_at);

-- Notification indexes
CREATE INDEX idx_notif_user ON notifications(user_id);
CREATE INDEX idx_notif_read ON notifications(is_read);
CREATE INDEX idx_notif_type ON notifications(notification_type);
CREATE INDEX idx_notif_created ON notifications(created_at);
CREATE INDEX idx_notif_user_read ON notifications(user_id, is_read);
```

**Bước 5: Apply migration**
```bash
# If using Flyway
./mvnw flyway:migrate

# If manual
mysql -u root -p uniactivity < src/main/resources/db/migration/V2__add_performance_indexes.sql
```

**Bước 6: Verify indexes**
```sql
-- Check indexes were created
SHOW INDEX FROM activities;
SHOW INDEX FROM activity_registrations;
SHOW INDEX FROM point_requests;
-- etc...
```

---

### Day 2 Afternoon: Testing & Verification (4 giờ)

#### ✅ Task 1.7: Performance Testing (240 phút)

**Test Script:**
```bash
#!/bin/bash
# test-performance.sh

echo "=== Testing Activity List Performance ==="
time curl -X GET http://localhost:8080/student/api/activities \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -o /dev/null -s

echo "=== Testing Point Requests Performance ==="
time curl -X GET http://localhost:8080/manager/api/point-requests \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -o /dev/null -s

echo "=== Testing Registrations Performance ==="
time curl -X GET http://localhost:8080/student/api/registrations \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -o /dev/null -s
```

**Enable SQL Logging:**
```properties
# application.properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

**Checklist:**
- [ ] Verify 1 query per list operation (not N+1)
- [ ] Verify response time improved (should be 60-80% faster)
- [ ] Verify all indexes exist in database
- [ ] Test all major endpoints still work correctly
- [ ] Check application logs for errors

**Rollback Plan (nếu có issue):**
```sql
-- Drop indexes if needed
DROP INDEX idx_activity_semester ON activities;
-- etc...
```

---

## 🟠 PHASE 2A: Transaction Management + Code Cleanup (Ngày 3-4)
**Ưu tiên:** HIGH  
**Thời gian ước tính:** 10-12 giờ

### Day 3 Morning: Fix @Transactional Issues (4 giờ)

#### ✅ Task 2.1: ActivityService.java (60 phút)
```java
// Find: public ActivityResponseDto createActivity(
// Add: @Transactional

@Transactional
public ActivityResponseDto createActivity(ActivityRequestDto dto, Long createdById) {
    // existing code...
}

// Find: public ActivityResponseDto updateActivity(
// Add: @Transactional

@Transactional
public ActivityResponseDto updateActivity(Long id, ActivityRequestDto dto) {
    // existing code...
}

// Find: public void deleteActivity(
// Add: @Transactional

@Transactional
public void deleteActivity(Long id) {
    // existing code...
}
```

---

#### ✅ Task 2.2: PointRequestService.java (45 phút)
```java
// Line ~119: reviewPointRequest()
// CHANGE: @Transactional(readOnly = true)
// TO: @Transactional

@Transactional // BỎ readOnly = true
public PointRequestResponseDto reviewPointRequest(
    Long requestId, 
    ReviewPointRequestDto dto, 
    Long reviewerId
) {
    // existing code...
}

// Verify other methods with write operations
```

---

#### ✅ Task 2.3: ClassJoinRequestService.java (45 phút)
```java
// Line ~102: reviewRequest()
// CHANGE: @Transactional(readOnly = true)
// TO: @Transactional

@Transactional // BỎ readOnly = true
public ClassJoinRequestResponseDto reviewRequest(
    Long requestId,
    ReviewRequestDto dto,
    Long reviewerId
) {
    // existing code...
}
```

---

#### ✅ Task 2.4: UserManagementService.java (30 phút)
```java
// Find all methods that modify user data
// Add @Transactional if missing

@Transactional
public void updateUserStatus(Long userId, UserStatus status) {
    // existing code...
}

@Transactional
public void updateUserRole(Long userId, Role role) {
    // existing code...
}
```

---

#### ✅ Task 2.5: ScoringRuleService.java (30 phút)
```java
@Transactional
public void applyScoringRule(Long studentId, Long semesterId) {
    // existing code with multiple saves
}

@Transactional
public void recalculateScores(Long semesterId) {
    // existing code...
}
```

---

### Day 3 Afternoon: Refactor Code Duplication (4 giờ)

#### ✅ Task 2.6: Extract Slot Statistics Logic (90 phút)

**File: ActivityService.java**
```java
// ADD new private method:

private void enrichActivityWithSlotStats(ActivityResponseDto dto, Activity activity) {
    // Get registration counts for all slots in one query
    Map<Long, Long> registrationCounts = registrationRepository
        .countRegistrationsByActivitySlots(activity.getId());
    
    // Enrich each slot with current registration count
    List<ActivitySlotResponseDto> slotsWithStats = activity.getActivitySlots().stream()
        .map(slot -> {
            ActivitySlotResponseDto slotDto = activitySlotMapper.toDto(slot);
            Long count = registrationCounts.getOrDefault(slot.getId(), 0L);
            slotDto.setCurrentRegistrations(count.intValue());
            return slotDto;
        })
        .collect(Collectors.toList());
    
    dto.setActivitySlots(slotsWithStats);
}

// UPDATE getAllActivities() - Line ~44
public List<ActivityResponseDto> getAllActivities() {
    List<Activity> activities = activityRepository.findAllByStatusWithDetails(ActivityStatus.ACTIVE);
    return activities.stream()
        .map(activity -> {
            ActivityResponseDto dto = activityMapper.toResponseDto(activity);
            enrichActivityWithSlotStats(dto, activity); // USE new method
            return dto;
        })
        .collect(Collectors.toList());
}

// UPDATE getVisibleActivitiesForStudent() - Line ~90
public List<ActivityResponseDto> getVisibleActivitiesForStudent(Long studentId) {
    // ... existing code to filter activities ...
    return filteredActivities.stream()
        .map(activity -> {
            ActivityResponseDto dto = activityMapper.toResponseDto(activity);
            enrichActivityWithSlotStats(dto, activity); // USE new method
            return dto;
        })
        .collect(Collectors.toList());
}
```

**File: ActivityRegistrationRepository.java**
```java
// ADD new method for batch counting:

@Query("SELECT r.activitySlot.id, COUNT(r) " +
       "FROM Registration r " +
       "WHERE r.activitySlot.activity.id = :activityId " +
       "GROUP BY r.activitySlot.id")
Map<Long, Long> countRegistrationsByActivitySlots(@Param("activityId") Long activityId);
```

---

#### ✅ Task 2.7: Create ManagerNotificationService (90 phút)

**New File: `UniActivity_BE/src/main/java/com/example/uniactivity/service/ManagerNotificationService.java`**

```java
package com.example.uniactivity.service;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerNotificationService {
    
    private final UserRepository userRepository;
    private final SseService sseService;
    
    /**
     * Notify all managers of a class about an event
     */
    public void notifyClassManagers(Long classId, String eventType, Object data) {
        List<User> managers = userRepository.findManagersByClassId(classId);
        
        for (User manager : managers) {
            try {
                sseService.sendEventToUser(manager.getId(), eventType, data);
            } catch (Exception e) {
                // Log but don't fail - SSE may not be connected
                log.warn("Failed to send SSE to manager {}: {}", 
                    manager.getId(), e.getMessage());
            }
        }
    }
    
    /**
     * Notify managers about point request updates
     */
    public void notifyPointRequestUpdate(Long classId, Object pointRequestData) {
        notifyClassManagers(classId, "point-request-update", pointRequestData);
    }
    
    /**
     * Notify managers about join request updates
     */
    public void notifyJoinRequestUpdate(Long classId, Object joinRequestData) {
        notifyClassManagers(classId, "join-request-update", joinRequestData);
    }
}
```

**UPDATE ClassJoinRequestService.java:**
```java
@RequiredArgsConstructor
public class ClassJoinRequestService {
    // ... existing fields ...
    private final ManagerNotificationService managerNotificationService; // ADD

    public ClassJoinRequestResponseDto reviewRequest(...) {
        // ... existing code ...
        
        // REPLACE lines 166-185 with:
        managerNotificationService.notifyJoinRequestUpdate(
            request.getStudentClass().getId(),
            mapper.toResponseDto(request)
        );
        
        return mapper.toResponseDto(request);
    }
}
```

**UPDATE PointRequestService.java:**
```java
@RequiredArgsConstructor
public class PointRequestService {
    // ... existing fields ...
    private final ManagerNotificationService managerNotificationService; // ADD

    public PointRequestResponseDto reviewPointRequest(...) {
        // ... existing code ...
        
        // REPLACE lines 185-202 with:
        managerNotificationService.notifyPointRequestUpdate(
            request.getStudent().getStudentClass().getId(),
            mapper.toResponseDto(request)
        );
        
        return mapper.toResponseDto(request);
    }
}
```

---

### Day 4: REST API Standardization (4 giờ)

#### ✅ Task 2.8: Create ApiResponse Wrapper (60 phút)

**New File: `UniActivity_BE/src/main/java/com/example/uniactivity/dto/common/ApiResponse.java`**

```java
package com.example.uniactivity.dto.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
    
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
```

---

#### ✅ Task 2.9: Update Controllers to use @RestController (120 phút)

**Files to update:**
1. `AdminController.java`
2. `FacultyController.java`
3. `SemesterController.java`
4. `AcademicYearController.java`
5. `StudentClassController.java`

**Pattern for each file:**
```java
// CHANGE:
@Controller
@ResponseBody
public class AdminController {

// TO:
@RestController
public class AdminController {

// UPDATE methods to use ApiResponse:
@PostMapping("/api/activities")
public ResponseEntity<ApiResponse<ActivityResponseDto>> createActivity(
    @Valid @RequestBody ActivityRequestDto dto
) {
    ActivityResponseDto result = activityService.createActivity(dto);
    return ResponseEntity.ok(ApiResponse.success("Tạo hoạt động thành công", result));
}

@GetMapping("/api/activities")
public ResponseEntity<ApiResponse<List<ActivityResponseDto>>> getAllActivities() {
    List<ActivityResponseDto> activities = activityService.getAllActivities();
    return ResponseEntity.ok(ApiResponse.success(activities));
}
```

---

## 🟡 PHASE 2B: Input Validation (Ngày 5)
**Thời gian ước tính:** 6-8 giờ

### Day 5 Morning: Add DTO Validation (4 giờ)

#### ✅ Task 2.10: ActivityRequestDto Validation (60 phút)

**File: `UniActivity_BE/src/main/java/com/example/uniactivity/dto/activity/ActivityRequestDto.java`**

```java
import jakarta.validation.constraints.*;

public class ActivityRequestDto {
    
    @NotBlank(message = "Tên hoạt động không được để trống")
    @Size(min = 5, max = 255, message = "Tên hoạt động phải từ 5-255 ký tự")
    private String name;
    
    @NotNull(message = "Phải chọn học kỳ")
    private Long semesterId;
    
    @NotNull(message = "Ngày bắt đầu đăng ký không được để trống")
    private LocalDateTime registrationStartDate;
    
    @NotNull(message = "Ngày kết thúc đăng ký không được để trống")
    private LocalDateTime registrationEndDate;
    
    @NotNull(message = "Phải chọn phạm vi hoạt động")
    private ActivityScope scope;
    
    @Min(value = 1, message = "Điểm hoạt động phải lớn hơn 0")
    @Max(value = 100, message = "Điểm hoạt động không được vượt quá 100")
    private Integer activityPoint;
    
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;
    
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
    private boolean isDateRangeValid() {
        if (registrationStartDate == null || registrationEndDate == null) {
            return true; // Let @NotNull handle null cases
        }
        return registrationEndDate.isAfter(registrationStartDate);
    }
    
    // existing fields and methods...
}
```

---

#### ✅ Task 2.11: Other DTOs Validation (180 phút)

**FacultyRequestDto:**
```java
@NotBlank(message = "Tên khoa không được để trống")
@Size(max = 100, message = "Tên khoa tối đa 100 ký tự")
private String name;

@Size(max = 500, message = "Mô tả tối đa 500 ký tự")
private String description;
```

**SemesterRequestDto:**
```java
@NotBlank(message = "Tên học kỳ không được để trống")
private String name;

@NotNull(message = "Ngày bắt đầu không được để trống")
private LocalDate startDate;

@NotNull(message = "Ngày kết thúc không được để trống")
private LocalDate endDate;

@NotNull(message = "Phải chọn năm học")
private Long academicYearId;

@AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
private boolean isDateValid() {
    return endDate == null || startDate == null || endDate.isAfter(startDate);
}
```

**ReviewPointRequestDto:**
```java
@NotNull(message = "Phải chọn trạng thái duyệt")
private EvidenceStatus status;

@Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
private String reviewNotes;

@Min(value = 0, message = "Điểm không được âm")
@Max(value = 100, message = "Điểm không được vượt quá 100")
private Integer approvedPoints;
```

**RegistrationRequestDto:**
```java
@NotNull(message = "Phải chọn hoạt động")
private Long activityId;

@NotNull(message = "Phải chọn ca học")
private Long activitySlotId;
```

---

### Day 5 Afternoon: Add @Valid to Controllers (3 giờ)

#### ✅ Task 2.12: Update All Controller Methods (180 phút)

**Pattern - Add @Valid to all @RequestBody parameters:**

```java
// AdminController.java
@PostMapping("/api/activities")
public ResponseEntity<ApiResponse<ActivityResponseDto>> createActivity(
    @Valid @RequestBody ActivityRequestDto dto // ADD @Valid
) {
    // ...
}

// FacultyController.java
@PostMapping
public ResponseEntity<ApiResponse<FacultyResponseDto>> createFaculty(
    @Valid @RequestBody FacultyRequestDto dto // ADD @Valid
) {
    // ...
}

// SemesterController.java
@PostMapping
public ResponseEntity<ApiResponse<SemesterResponseDto>> createSemester(
    @Valid @RequestBody SemesterRequestDto dto // ADD @Valid
) {
    // ...
}

// ManagerActivityController.java
@PostMapping
public ResponseEntity<ApiResponse<ActivityResponseDto>> createActivity(
    @Valid @RequestBody ActivityRequestDto dto // ADD @Valid
) {
    // ...
}

// PointRequestController.java
@PutMapping("/{id}/review")
public ResponseEntity<ApiResponse<PointRequestResponseDto>> reviewRequest(
    @PathVariable Long id,
    @Valid @RequestBody ReviewPointRequestDto dto // ADD @Valid
) {
    // ...
}
```

**Checklist - Controllers to update:**
- [ ] AdminController
- [ ] FacultyController
- [ ] SemesterController
- [ ] AcademicYearController
- [ ] StudentClassController
- [ ] ManagerActivityController
- [ ] PointRequestController
- [ ] ClassJoinRequestController
- [ ] StudentActivityController
- [ ] StudentRegistrationController

---

## 🔧 PHASE 3: Dependencies & Exception Handling (Ngày 6)
**Thời gian ước tính:** 6 giờ

### Day 6 Morning: Update Dependencies (2 giờ)

#### ✅ Task 3.1: Update pom.xml (30 phút)

```xml
<!-- UniActivity_BE/pom.xml -->

<properties>
    <!-- UPDATE these versions -->
    <zxing.version>3.5.4</zxing.version>  <!-- Was: 3.5.2 -->
    <poi.version>5.5.1</poi.version>       <!-- Was: 5.2.5 -->
    <jjwt.version>0.13.0</jjwt.version>    <!-- Was: 0.12.6 -->
</properties>
```

#### ✅ Task 3.2: Test After Update (90 phút)

```bash
# Clean and rebuild
./mvnw clean install

# Run tests
./mvnw test

# Test QR code generation (ZXing)
# Test Excel export (POI)
# Test JWT token generation/validation (JJWT)

# If any breaking changes, check migration guides:
# - https://github.com/zxing/zxing/releases
# - https://poi.apache.org/changes.html
# - https://github.com/jwtk/jjwt/releases
```

---

### Day 6 Afternoon: Improve Exception Handling (4 giờ)

#### ✅ Task 3.3: Add Missing Exception Handlers (240 phút)

**File: `UniActivity_BE/src/main/java/com/example/uniactivity/exception/GlobalExceptionHandler.java`**

```java
// ADD these new handlers:

@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
        DataIntegrityViolationException ex) {
    
    String message = "Dữ liệu vi phạm ràng buộc database";
    
    // More specific messages
    String exMsg = ex.getMessage();
    if (exMsg != null) {
        if (exMsg.contains("Duplicate entry")) {
            message = "Dữ liệu đã tồn tại trong hệ thống";
        } else if (exMsg.contains("foreign key constraint")) {
            message = "Không thể xóa do có dữ liệu liên quan";
        } else if (exMsg.contains("cannot be null")) {
            message = "Thiếu thông tin bắt buộc";
        }
    }
    
    log.warn("Data integrity violation: {}", ex.getMessage());
    
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(message));
}

@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
        ConstraintViolationException ex) {
    
    Map<String, String> errors = new HashMap<>();
    ex.getConstraintViolations().forEach(violation -> {
        String propertyPath = violation.getPropertyPath().toString();
        String message = violation.getMessage();
        errors.put(propertyPath, message);
    });
    
    log.warn("Constraint violation: {}", errors);
    
    ApiResponse<Object> response = ApiResponse.error("Validation failed");
    response.setData(errors);
    
    return ResponseEntity
        .badRequest()
        .body(response);
}

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiResponse<Object>> handleInvalidJson(
        HttpMessageNotReadableException ex) {
    
    String message = "Dữ liệu JSON không hợp lệ";
    
    // More specific error for date/time format issues
    if (ex.getMessage().contains("LocalDateTime")) {
        message = "Định dạng ngày giờ không hợp lệ. Sử dụng: yyyy-MM-dd HH:mm:ss";
    } else if (ex.getMessage().contains("LocalDate")) {
        message = "Định dạng ngày không hợp lệ. Sử dụng: yyyy-MM-dd";
    }
    
    log.warn("Invalid JSON: {}", ex.getMessage());
    
    return ResponseEntity
        .badRequest()
        .body(ApiResponse.error(message));
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex) {
    
    String message = String.format(
        "Tham số '%s' không hợp lệ. Mong đợi kiểu: %s",
        ex.getName(),
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
    );
    
    log.warn("Type mismatch: {}", ex.getMessage());
    
    return ResponseEntity
        .badRequest()
        .body(ApiResponse.error(message));
}

@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ApiResponse<Object>> handleMissingParameter(
        MissingServletRequestParameterException ex) {
    
    String message = String.format(
        "Thiếu tham số bắt buộc: %s",
        ex.getParameterName()
    );
    
    return ResponseEntity
        .badRequest()
        .body(ApiResponse.error(message));
}

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
        AccessDeniedException ex) {
    
    log.warn("Access denied: {}", ex.getMessage());
    
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("Bạn không có quyền truy cập tài nguyên này"));
}

@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Object>> handleGenericException(
        Exception ex) {
    
    log.error("Unexpected error", ex);
    
    // Don't expose internal error details in production
    String message = "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau";
    
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(message));
}

// IMPROVE existing MethodArgumentNotValidException handler:
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(
        MethodArgumentNotValidException ex) {
    
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error -> {
        errors.put(error.getField(), error.getDefaultMessage());
    });
    
    // Also handle global errors (like @AssertTrue)
    ex.getBindingResult().getGlobalErrors().forEach(error -> {
        errors.put(error.getObjectName(), error.getDefaultMessage());
    });
    
    log.warn("Validation failed: {}", errors);
    
    ApiResponse<Object> response = ApiResponse.error("Validation failed");
    response.setData(errors);
    
    return ResponseEntity
        .badRequest()
        .body(response);
}
```

---

## ✅ PHASE 4: Testing & Verification (Ngày 7)
**Thời gian ước tính:** 6-8 giờ

### Day 7 Morning: Integration Testing (4 giờ)

#### ✅ Task 4.1: Manual Testing Checklist (120 phút)

```bash
# test-all-endpoints.sh

#!/bin/bash

BASE_URL="http://localhost:8080"
ADMIN_TOKEN="..."
MANAGER_TOKEN="..."
STUDENT_TOKEN="..."

echo "=== 1. Test Activity List (should be fast now) ==="
time curl -X GET $BASE_URL/student/api/activities \
  -H "Authorization: Bearer $STUDENT_TOKEN"

echo "\n=== 2. Test Point Requests ==="
time curl -X GET $BASE_URL/manager/api/point-requests \
  -H "Authorization: Bearer $MANAGER_TOKEN"

echo "\n=== 3. Test Create Activity with Validation ==="
curl -X POST $BASE_URL/admin/api/activities \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "",
    "semesterId": null
  }'
# Should return validation errors

echo "\n=== 4. Test Invalid JSON ==="
curl -X POST $BASE_URL/admin/api/activities \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ invalid json }'
# Should return "Dữ liệu JSON không hợp lệ"

echo "\n=== 5. Test Transaction Rollback ==="
# Create activity with invalid slot data
# Should rollback the entire transaction

echo "\n=== 6. Test Duplicate Entry ==="
# Try to create duplicate faculty/semester
# Should return "Dữ liệu đã tồn tại"

echo "\n=== 7. Test Foreign Key Constraint ==="
# Try to delete semester that has activities
# Should return "Không thể xóa do có dữ liệu liên quan"

echo "\n=== 8. Test Check-in Performance ==="
time curl -X POST $BASE_URL/student/api/checkin \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "qrSecret=..."
```

---

#### ✅ Task 4.2: Performance Verification (120 phút)

**Enable Query Logging:**
```properties
# application.properties
spring.jpa.properties.hibernate.generate_statistics=true
logging.level.org.hibernate.stat=DEBUG
```

**Check Metrics:**
```bash
# Start app and test endpoints
./mvnw spring-boot:run

# In another terminal, monitor logs
tail -f logs/application.log | grep "queries executed"

# Expected: 1-2 queries per request (not N+1)
```

**Load Testing (optional):**
```bash
# Install Apache Bench
sudo apt install apache2-utils

# Test with 100 concurrent requests
ab -n 1000 -c 100 \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/student/api/activities

# Compare before/after results
```

---

### Day 7 Afternoon: Documentation & Deployment Prep (3 giờ)

#### ✅ Task 4.3: Update Documentation (90 phút)

**Create: `docs/api-changes-v2.md`**
```markdown
# API Changes - Version 2.0

## Breaking Changes
None - all changes are backward compatible

## New Response Format
All endpoints now return standardized response:
```json
{
  "success": true,
  "message": "Optional message",
  "data": { ... },
  "timestamp": "2026-08-13 21:00:00"
}
```

## Improved Error Messages
- Validation errors now include field-level details
- Database constraint violations have user-friendly messages
- JSON parsing errors are more specific

## Performance Improvements
- 60-80% faster response times for list operations
- Database indexes added for all foreign keys
- N+1 query issues resolved
```

---

#### ✅ Task 4.4: Deployment Checklist (90 phút)

**Create: `docs/deployment-checklist-v2.md`**
```markdown
# Deployment Checklist - Version 2.0

## Pre-Deployment

### 1. Database Migration
- [ ] Backup current database
- [ ] Review migration script: V2__add_performance_indexes.sql
- [ ] Test migration on staging database
- [ ] Verify indexes were created

### 2. Code Review
- [ ] All tests passing
- [ ] No console errors
- [ ] Code reviewed by team
- [ ] Performance tested

### 3. Configuration
- [ ] Update application.properties for production
- [ ] Verify environment variables
- [ ] Check CORS settings
- [ ] Review logging levels

## Deployment Steps

### 1. Database
```bash
# Backup
mysqldump -u root -p uniactivity > backup_$(date +%Y%m%d).sql

# Run migration
mysql -u root -p uniactivity < V2__add_performance_indexes.sql

# Verify
mysql -u root -p uniactivity -e "SHOW INDEX FROM activities;"
```

### 2. Application
```bash
# Build
./mvnw clean package -DskipTests

# Stop current version
systemctl stop uniactivity

# Deploy new version
cp target/uniactivity-*.jar /opt/uniactivity/app.jar

# Start
systemctl start uniactivity

# Monitor logs
tail -f /var/log/uniactivity/application.log
```

### 3. Smoke Tests
- [ ] Health check: GET /actuator/health
- [ ] Login works
- [ ] Activity list loads (check performance)
- [ ] Create/update operations work
- [ ] No error logs

## Rollback Plan
```bash
# If issues occur:

# 1. Stop application
systemctl stop uniactivity

# 2. Restore previous version
cp /opt/uniactivity/app.jar.backup /opt/uniactivity/app.jar

# 3. Rollback database (if needed)
mysql -u root -p uniactivity < backup_YYYYMMDD.sql

# 4. Start application
systemctl start uniactivity
```

## Post-Deployment

### Monitor for 24 hours:
- [ ] Response times (should be 60-80% faster)
- [ ] Error rates (should be same or lower)
- [ ] Database query counts (should be 70-90% lower)
- [ ] Memory usage (should be stable)
- [ ] User feedback

### Performance Metrics to Track:
```sql
-- Check index usage
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'uniactivity'
ORDER BY TABLE_NAME, INDEX_NAME;

-- Check slow queries
SELECT * FROM mysql.slow_log
WHERE start_time > NOW() - INTERVAL 1 DAY
ORDER BY query_time DESC
LIMIT 10;
```
```

---

## 📊 Summary & Metrics

### Estimated Time Breakdown:
- **Phase 1 (Critical):** 12-16 hours
- **Phase 2A (High):** 10-12 hours
- **Phase 2B (Validation):** 6-8 hours
- **Phase 3 (Medium):** 6 hours
- **Phase 4 (Testing):** 6-8 hours
- **TOTAL:** 40-50 hours (~6-7 working days)

### Expected Improvements:
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Activity List Response Time | 800ms | 200ms | 75% faster |
| Point Requests Response Time | 1200ms | 300ms | 75% faster |
| Database Queries (List) | 101 queries | 1-2 queries | 98% reduction |
| Database Load | 100% | 10-30% | 70-90% reduction |
| API Response Consistency | 60% | 100% | +40% |
| Input Validation Coverage | 40% | 95% | +55% |

### Files Modified Summary:
- **Entities:** 8 files (add indexes)
- **Repositories:** 5 files (add JOIN FETCH queries)
- **Services:** 8 files (fix @Transactional, refactor duplication)
- **Controllers:** 10 files (add @Valid, use ApiResponse)
- **DTOs:** 6 files (add validation)
- **Exception Handler:** 1 file (add handlers)
- **Dependencies:** 1 file (pom.xml)
- **New Files:** 3 files (ApiResponse, ManagerNotificationService, migration)
- **TOTAL:** ~42 files

---

## 🎯 Priority Ranking

### MUST DO (Cannot skip):
1. ✅ Fix N+1 queries (Phase 1, Task 1.1-1.3)
2. ✅ Add database indexes (Phase 1, Task 1.4-1.6)
3. ✅ Fix @Transactional issues (Phase 2A, Task 2.1-2.5)

### SHOULD DO (High value):
4. ✅ Refactor code duplication (Phase 2A, Task 2.6-2.7)
5. ✅ Standardize API responses (Phase 2A, Task 2.8-2.9)
6. ✅ Add input validation (Phase 2B, Task 2.10-2.12)

### NICE TO HAVE (Can defer):
7. Update dependencies (Phase 3, Task 3.1-3.2)
8. Improve exception handling (Phase 3, Task 3.3)

---

## 📝 Daily Checklist Template

### End of Each Day:
- [ ] Commit changes với descriptive message
- [ ] Run tests to ensure nothing broke
- [ ] Update this plan if timeline changes
- [ ] Note any blockers or issues
- [ ] Push to feature branch

### Git Commit Message Template:
```
feat(performance): [Task X.X] Brief description

- Detailed change 1
- Detailed change 2

Affects: ServiceName, ControllerName
Tests: All passing
Performance: X% improvement (if applicable)
```

---

## 🚨 Risk Mitigation

### Potential Issues & Solutions:

**Issue 1: Migration fails**
- Solution: Rollback script ready, test on staging first

**Issue 2: Breaking changes from dependency updates**
- Solution: Check release notes, update code if needed

**Issue 3: Performance not improved as expected**
- Solution: Profile queries, check index usage with EXPLAIN

**Issue 4: Validation too strict, users complain**
- Solution: Review and relax constraints based on feedback

---

## ✅ Definition of Done

Phase is complete when:
- [ ] All tasks in phase completed
- [ ] Tests passing
- [ ] Code reviewed (self or peer)
- [ ] Committed to Git
- [ ] Documented (if needed)
- [ ] Performance verified (Phase 1)

Project is complete when:
- [ ] All phases done
- [ ] Integration tests passing
- [ ] Performance metrics achieved
- [ ] Documentation updated
- [ ] Ready for deployment

---

**Bắt đầu từ:** Phase 1, Task 1.1  
**Mục tiêu đầu tiên:** Fix N+1 queries trong ActivityRepository (90 phút)

**Chúc may mắn! 🚀**
