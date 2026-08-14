# Báo Cáo Kiểm Tra và Tối Ưu Backend - UniActivity
**Ngày phân tích:** 13/08/2026  
**Phạm vi:** Toàn bộ Spring Boot Backend

---

## 📊 Tổng Quan

Dự án **UniActivity** có cấu trúc tổ chức tốt với:
- ✅ Spring Boot 3.5.8 (phiên bản mới nhất)
- ✅ Java 21 (LTS)
- ✅ Kiến trúc phân tầng rõ ràng (Controller → Service → Repository)
- ✅ Security implementation tốt với JWT + Session hybrid
- ✅ Proper authorization checks với AuthorizationServiced

**Tuy nhiên**, có một số vấn đề quan trọng cần được tối ưu để cải thiện performance, maintainability và security.

---

## 🔴 Vấn Đề Nghiêm Trọng (Critical)

### 1. **N+1 Query Problem - CRITICAL PERFORMANCE ISSUE**

**Mô tả:**
- Các entity có 31 mối quan hệ `@ManyToOne` với LAZY loading
- Mapper classes truy cập các lazy-loaded relationships → gây ra N+1 queries
- Không có JOIN FETCH trong các repository queries

**Ví dụ cụ thể:**
```java
// ActivityMapper.java - Khi convert entity → DTO
ActivityResponseDto dto = mapper.toResponseDto(activity);
// ↓ Triggers lazy load
dto.setSemester(activity.getSemester()); // +1 query for each activity
```

**Impact:**
- `getAllActivities()` với 100 activities → 1 query chính + 100 queries cho semester
- `getPointRequests()` → N queries cho student, semester, reviewer
- List operations chậm đáng kể khi có nhiều records

**Giải pháp:**

#### Option 1: Sử dụng JOIN FETCH (Recommended)
```java
// ActivityRepository.java
@Query("SELECT a FROM Activity a " +
       "LEFT JOIN FETCH a.semester " +
       "LEFT JOIN FETCH a.createdBy " +
       "WHERE a.status = :status")
List<Activity> findAllWithDetails(@Param("status") ActivityStatus status);
```

#### Option 2: Entity Graphs
```java
@EntityGraph(attributePaths = {"semester", "createdBy"})
List<Activity> findByStatus(ActivityStatus status);
```

#### Option 3: DTO Projection (Best for read-only)
```java
@Query("SELECT new com.example.uniactivity.dto.ActivityResponseDto(" +
       "a.id, a.name, s.name, u.fullName) " +
       "FROM Activity a JOIN a.semester s JOIN a.createdBy u")
List<ActivityResponseDto> findAllActivitiesDto();
```

**Files cần fix:**
- `ActivityRepository.java`
- `PointRequestRepository.java`
- `ActivityRegistrationRepository.java`
- `ClassJoinRequestRepository.java`
- Tất cả các repository có list operations

---

### 2. **Missing Database Indexes - PERFORMANCE**

**Hiện tại:** Chỉ có 3 unique indexes được định nghĩa
```java
// User.java
@Column(unique = true) // username
@Column(unique = true) // email

// OAuthExchangeCode.java  
@Column(unique = true) // code
```

**Vấn đề:** Không có indexes cho các foreign keys và query conditions thường dùng

**Indexes cần thêm:**

```java
// Activity.java
@Entity
@Table(name = "activities", indexes = {
    @Index(name = "idx_activity_semester", columnList = "semester_id"),
    @Index(name = "idx_activity_status", columnList = "status"),
    @Index(name = "idx_activity_scope", columnList = "scope"),
    @Index(name = "idx_activity_created_by", columnList = "created_by_id"),
    @Index(name = "idx_activity_dates", columnList = "registration_start_date, registration_end_date")
})
public class Activity { ... }

// ActivityRegistration.java
@Table(name = "activity_registrations", indexes = {
    @Index(name = "idx_reg_student", columnList = "student_id"),
    @Index(name = "idx_reg_activity", columnList = "activity_id"),
    @Index(name = "idx_reg_status", columnList = "status"),
    @Index(name = "idx_reg_student_activity", columnList = "student_id, activity_id")
})

// PointRequest.java
@Table(name = "point_requests", indexes = {
    @Index(name = "idx_pr_student", columnList = "student_id"),
    @Index(name = "idx_pr_semester", columnList = "semester_id"),
    @Index(name = "idx_pr_status", columnList = "status"),
    @Index(name = "idx_pr_reviewer", columnList = "reviewed_by_id")
})

// ClassJoinRequest.java
@Table(name = "class_join_requests", indexes = {
    @Index(name = "idx_cjr_student", columnList = "student_id"),
    @Index(name = "idx_cjr_class", columnList = "student_class_id"),
    @Index(name = "idx_cjr_status", columnList = "status")
})

// CheckInSession.java (quan trọng cho real-time check-in)
@Table(name = "check_in_sessions", indexes = {
    @Index(name = "idx_checkin_slot", columnList = "activity_slot_id"),
    @Index(name = "idx_checkin_active", columnList = "is_active"),
    @Index(name = "idx_checkin_expires", columnList = "expires_at")
})

// Registration.java
@Table(name = "registrations", indexes = {
    @Index(name = "idx_registration_student_slot", columnList = "student_id, activity_slot_id")
})

// Notification.java
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user", columnList = "user_id"),
    @Index(name = "idx_notif_read", columnList = "is_read"),
    @Index(name = "idx_notif_created", columnList = "created_at")
})
```

---

## 🟠 Vấn Đề Quan Trọng (High Priority)

### 3. **Code Duplication trong Service Layer**

**ActivityService.java**
- Duplicate slot statistics calculation (lines 44-66 vs 90-112)

**Giải pháp:**
```java
private void enrichActivityWithStats(ActivityResponseDto dto, Activity activity) {
    Map<Long, Long> registrationCounts = registrationRepository
        .countRegistrationsByActivitySlots(activity.getId());
    
    List<ActivitySlotResponseDto> slotsWithStats = activity.getActivitySlots().stream()
        .map(slot -> {
            ActivitySlotResponseDto slotDto = activitySlotMapper.toDto(slot);
            slotDto.setCurrentRegistrations(
                registrationCounts.getOrDefault(slot.getId(), 0L).intValue()
            );
            return slotDto;
        })
        .collect(Collectors.toList());
    
    dto.setActivitySlots(slotsWithStats);
}
```

**ClassJoinRequestService.java & PointRequestService.java**
- Duplicate dashboard update logic (lines 166-185 vs 185-202)

**Giải pháp:** Tạo shared service
```java
@Service
public class ManagerNotificationService {
    
    public void notifyClassManagers(Long classId, String eventType, Object data) {
        List<User> managers = userRepository.findManagersByClassId(classId);
        for (User manager : managers) {
            sseService.sendEventToUser(manager.getId(), eventType, data);
        }
    }
}
```

---

### 4. **Inconsistent @Transactional Usage**

**Vấn đề tìm thấy:**

❌ **Missing @Transactional:**
- `ActivityService.createActivity()` - có multiple repository calls
- `ActivityService.updateActivity()` - có multiple saves
- `UserManagementService.updateUserStatus()` - có side effects
- `ScoringRuleService.applyScoringRule()` - calculate và save nhiều lần

❌ **Incorrect @Transactional(readOnly = true):**
- `PointRequestService.reviewPointRequest()` - line 119 (có write operation nhưng đánh dấu readOnly)
- `ClassJoinRequestService.reviewRequest()` - line 102 (tương tự)

✅ **Correct usage:**
- `RegistrationService.registerForActivity()` - có @Transactional
- `AuthService.register()` - có @Transactional

**Giải pháp:**
```java
// ActivityService.java
@Transactional // THÊM annotation này
public ActivityResponseDto createActivity(ActivityRequestDto dto, Long createdById) {
    // Multiple repository operations
    Activity activity = activityRepository.save(...);
    activitySlotRepository.saveAll(...);
    return mapper.toDto(activity);
}

// PointRequestService.java
@Transactional // BỎ readOnly = true
public PointRequestResponseDto reviewPointRequest(Long requestId, ...) {
    PointRequest request = findById(requestId);
    request.setStatus(dto.getStatus());
    request.setReviewedById(reviewerId);
    pointRequestRepository.save(request); // Write operation
    return mapper.toDto(request);
}
```

---

### 5. **REST API Inconsistency**

**Vấn đề:**

1. **Mixed Controller Annotations:**
```java
// ❌ Inconsistent
@Controller
@ResponseBody
public class AdminController { ... }

// ✅ Should use
@RestController
public class AdminController { ... }
```

2. **Inconsistent Response Structure:**
```java
// Some controllers:
return ResponseEntity.ok(Map.of("success", true, "data", dto));

// Others:
return ResponseEntity.ok(dto);

// Others:
return new ResponseEntity<>(dto, HttpStatus.OK);
```

3. **URL Pattern Inconsistency:**
- Admin: `/admin/api/*` và `/admin/academic-years/api/*`
- Manager: `/manager/api/*` 
- Student: `/student/api/*`

**Giải pháp:**

Tạo standardized response wrapper:
```java
@Getter
@Setter
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}

// Usage
@PostMapping
public ResponseEntity<ApiResponse<ActivityResponseDto>> createActivity(@RequestBody ActivityRequestDto dto) {
    ActivityResponseDto result = activityService.createActivity(dto);
    return ResponseEntity.ok(ApiResponse.success(result));
}
```

---

### 6. **Missing Input Validation**

**Controllers thiếu validation:**
- `AdminController` - không có `@Valid` annotation
- `FacultyController.createFaculty()` - no validation
- `SemesterController.createSemester()` - no validation

**DTO classes thiếu validation constraints:**

```java
// ActivityRequestDto.java - THÊM validation
public class ActivityRequestDto {
    @NotBlank(message = "Tên hoạt động không được để trống")
    @Size(max = 255, message = "Tên hoạt động tối đa 255 ký tự")
    private String name;
    
    @NotNull(message = "Phải chọn học kỳ")
    private Long semesterId;
    
    @NotNull(message = "Ngày bắt đầu đăng ký không được để trống")
    @FutureOrPresent(message = "Ngày bắt đầu phải là hiện tại hoặc tương lai")
    private LocalDateTime registrationStartDate;
    
    @NotNull(message = "Ngày kết thúc đăng ký không được để trống")
    @Future(message = "Ngày kết thúc phải là tương lai")
    private LocalDateTime registrationEndDate;
    
    @Min(value = 1, message = "Điểm hoạt động phải lớn hơn 0")
    private Integer activityPoint;
    
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
    private boolean isDateRangeValid() {
        if (registrationStartDate == null || registrationEndDate == null) {
            return true; // Let @NotNull handle this
        }
        return registrationEndDate.isAfter(registrationStartDate);
    }
}
```

---

## 🟡 Vấn Đề Trung Bình (Medium Priority)

### 7. **Dependency Updates Available**

**Cần update (pom.xml):**
```xml
<!-- Current: 3.5.2 → Latest: 3.5.4 -->
<zxing.version>3.5.4</zxing.version>

<!-- Current: 5.2.5 → Latest: 5.5.1 -->
<poi.version>5.5.1</poi.version>

<!-- Current: 0.12.6 → Latest: 0.13.0 -->
<jjwt.version>0.13.0</jjwt.version>
```

---

### 8. **Exception Handling Gaps**

**GlobalExceptionHandler.java** - Thiếu handlers cho:
- `DataIntegrityViolationException` - database constraint violations
- `ConstraintViolationException` - validation errors
- `HttpMessageNotReadableException` - malformed JSON
- `MethodArgumentNotValidException` - đã có nhưng có thể improve message

**Giải pháp:**
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
        DataIntegrityViolationException ex) {
    String message = "Dữ liệu vi phạm ràng buộc database";
    
    if (ex.getMessage().contains("Duplicate entry")) {
        message = "Dữ liệu đã tồn tại trong hệ thống";
    } else if (ex.getMessage().contains("foreign key constraint")) {
        message = "Không thể xóa do có dữ liệu liên quan";
    }
    
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of(
            "success", false,
            "message", message,
            "timestamp", LocalDateTime.now()
        ));
}

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<Map<String, Object>> handleInvalidJson(
        HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(Map.of(
            "success", false,
            "message", "Dữ liệu JSON không hợp lệ",
            "timestamp", LocalDateTime.now()
        ));
}
```

---

### 9. **Security Improvements**

**JWT Secret Management:**
- ⚠️ No rotation mechanism for JWT secret
- Recommendation: Implement key versioning

**QR Code Security:**
- ✅ Good: SHA-256 hashing, 60s expiration
- ⚠️ Consider: Add rate limiting for QR generation

**Password Reset:**
- ✅ Good: Token expiration, one-time use
- ⚠️ Consider: Add maximum attempts limit

---

### 10. **Repository Query Optimization**

**Các method cần optimize với custom query:**

```java
// ActivityRepository.java - THÊM
@Query("SELECT a FROM Activity a " +
       "LEFT JOIN FETCH a.semester " +
       "LEFT JOIN FETCH a.createdBy " +
       "WHERE a.status = :status " +
       "ORDER BY a.createdAt DESC")
List<Activity> findAllActiveWithDetails(@Param("status") ActivityStatus status);

// PointRequestRepository.java - THÊM
@Query("SELECT pr FROM PointRequest pr " +
       "JOIN FETCH pr.student s " +
       "JOIN FETCH pr.semester " +
       "LEFT JOIN FETCH pr.reviewedBy " +
       "WHERE s.studentClass.id = :classId " +
       "AND pr.status = :status")
List<PointRequest> findByClassIdAndStatusWithDetails(
    @Param("classId") Long classId, 
    @Param("status") EvidenceStatus status
);

// NotificationRepository.java - THÊM pagination
@Query("SELECT n FROM Notification n " +
       "WHERE n.user.id = :userId " +
       "ORDER BY n.createdAt DESC")
Page<Notification> findByUserIdOrderByCreatedAtDesc(
    @Param("userId") Long userId, 
    Pageable pageable
);
```

---

## 🟢 Điểm Mạnh (Strengths)

1. ✅ **Security Implementation tốt:**
   - JWT + Session hybrid authentication
   - Proper authorization với AuthorizationService
   - IDOR protection với manager class ownership verification
   - OAuth2 exchange code pattern (prevents token exposure)
   - BCrypt password hashing

2. ✅ **Code Organization:**
   - Clear layered architecture
   - Proper separation of concerns
   - Good use of DTOs và Mappers

3. ✅ **Testing:**
   - Karate framework cho API testing
   - Unit tests cho các service classes

4. ✅ **Configuration:**
   - Environment-based configuration
   - Proper CORS setup
   - Secret separation validation

---

## 📋 Action Items - Ưu Tiên Thực Hiện

### Phase 1: Critical Performance (1-2 ngày)
- [ ] **Fix N+1 queries** - Thêm JOIN FETCH vào repositories
- [ ] **Add database indexes** - Thêm @Index annotations vào entities
- [ ] **Run database migration** để apply indexes

### Phase 2: Code Quality (2-3 ngày)
- [ ] Fix @Transactional usage
- [ ] Refactor duplicate code trong Service layer
- [ ] Standardize REST API responses
- [ ] Add missing input validations

### Phase 3: Security & Optimization (1-2 ngày)
- [ ] Update dependencies (ZXing, Apache POI, JJWT)
- [ ] Improve exception handling
- [ ] Add rate limiting cho sensitive endpoints
- [ ] Optimize repository queries với custom queries

### Phase 4: Documentation & Testing (1 ngày)
- [ ] Document API endpoints
- [ ] Add more integration tests
- [ ] Performance testing với realistic data

---

## 📊 Metrics & Monitoring Recommendations

**Cần thêm:**
1. **Database query monitoring** - Log slow queries (> 100ms)
2. **API response time metrics** - Track endpoint performance
3. **Memory usage monitoring** - Watch for leaks
4. **Error rate tracking** - Monitor exception frequency

**Tools đề xuất:**
- Spring Boot Actuator (health checks, metrics)
- Micrometer + Prometheus (metrics collection)
- Log aggregation (ELK stack hoặc tương đương)

---

## 🎯 Kết Luận

Backend của UniActivity có **foundation tốt** với security implementation đúng chuẩn và architecture rõ ràng. Tuy nhiên, **performance issues** (đặc biệt N+1 queries và missing indexes) cần được fix **ngay lập tức** trước khi scale lên production với nhiều users.

**Ước tính impact sau khi fix:**
- ⚡ **Response time**: Cải thiện 60-80% cho list operations
- 📉 **Database load**: Giảm 70-90% số queries không cần thiết
- 🚀 **Scalability**: Hệ thống có thể handle 5-10x users hiện tại
- 🐛 **Bugs**: Giảm race conditions và transaction issues

**Khuyến nghị:** Ưu tiên implement Phase 1 (Critical Performance) trước khi deploy production.
