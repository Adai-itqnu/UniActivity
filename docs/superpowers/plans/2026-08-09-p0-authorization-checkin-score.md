# P0 Authorization, Check-in, and Score Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Prevent cross-class manager access, require valid QR/time/location for student check-in, make evidence review idempotent, and route GPA scores through manager approval.

**Architecture:** Controllers delegate authorization and state changes to focused transactional services. Repository queries filter by trusted relationships and use pessimistic locks for review transitions; automated score writes use stable source/reference keys. Existing endpoint paths and message-based responses remain compatible with the React frontend.

**Tech Stack:** Java 17, Spring Boot 3.5.8, Spring Security, Spring Data JPA, JUnit 5, Mockito, React 19.

## Global Constraints

- Preserve existing endpoint URLs and the response message field.
- Never authorize from classId, studentId, registrationId, or activityId without loading and checking the related entity.
- Manager operations may affect only students in manager.studentClass.
- Student check-in requires activity status OPEN, current time inside startTime/endTime, matching classId, valid QR token, and finite valid GPS when configured.
- Evidence review is transactional and idempotent; one registration can create at most one AUTO_ACTIVITY score contribution.
- GPA values are finite and between 0 and 10; GPA submission creates a pending point request rather than a score detail.
- Do not copy or commit UniActivity_BE/.env.

---

### Task 1: Centralize manager scope authorization

**Files:**
- Create: UniActivity_BE/src/main/java/com/example/uniactivity/exception/AuthorizationException.java
- Create: UniActivity_BE/src/main/java/com/example/uniactivity/exception/ConflictException.java
- Create: UniActivity_BE/src/main/java/com/example/uniactivity/service/ManagerScopeAuthorizationService.java
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/ManagerScopeAuthorizationServiceTest.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/exception/GlobalExceptionHandler.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/repository/ActivityRegistrationRepository.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerActivityController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerDataApiController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentDataApiController.java

**Interfaces:**
- Produces: requireManagedClass(User), requireStudent(User, Long), requireRegistration(User, Long), requireActivity(User, Long), registrationsForActivity(User, Long).
- HTTP mapping: AuthorizationException to 403 and ConflictException to 409.

- [ ] **Step 1: Write failing authorization tests**

Create tests proving:

    @Test void rejectsRegistrationFromAnotherClass()
    @Test void returnsRegistrationFromManagersClass()
    @Test void filtersActivityRegistrationsByManagersClass()
    @Test void rejectsActivityNotVisibleToManagersClass()
    @Test void rejectsManagerWithoutClass()

Use Mockito entities with explicit IDs and assert AuthorizationException for cross-class access.

- [ ] **Step 2: Run the new test and verify RED**

Run:

    cd UniActivity_BE
    JAVA_HOME=/home/daidev/.jdks/temurin-21.0.12 PATH=/home/daidev/.jdks/temurin-21.0.12/bin:$PATH bash ./mvnw -Dtest=ManagerScopeAuthorizationServiceTest test

Expected: compilation fails because ManagerScopeAuthorizationService and AuthorizationException do not exist.

- [ ] **Step 3: Implement exceptions and service**

Implement these exact public methods:

    public StudentClass requireManagedClass(User manager)
    public User requireStudent(User manager, Long studentId)
    public ActivityRegistration requireRegistration(User manager, Long registrationId)
    public Activity requireActivity(User manager, Long activityId)
    public List<ActivityRegistration> registrationsForActivity(User manager, Long activityId)

requireActivity loads the activity and calls ActivityService.isActivityVisibleToStudent(activity, manager); false produces AuthorizationException. registrationsForActivity calls:

    findByActivityAndStudent_StudentClassOrderByRegisteredAtAsc(activity, managedClass)

Add GlobalExceptionHandler handlers returning ErrorResponse with 403 and 409.

- [ ] **Step 4: Replace unsafe controller loads**

ManagerActivityController:

- Both QR endpoints call requireActivity before generating a token.
- getActivityRegistrations calls registrationsForActivity.
- manualCheckin calls requireRegistration before changing state.
- approve/reject signatures accept AuthenticationPrincipal and delegate scoped registration checks until Task 3 replaces their state logic.

ManagerDataApiController and StudentDataApiController user-score endpoints call requireStudent or require authenticated self. A STUDENT request for another user ID returns 403.

- [ ] **Step 5: Run tests and commit**

Run the focused test plus existing security tests. Expected: zero failures.

    git add UniActivity_BE/src/main UniActivity_BE/src/test
    git commit -m "fix: enforce manager class scope"

### Task 2: Require secure student check-in

**Files:**
- Create: UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentCheckinService.java
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentCheckinServiceTest.java
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/util/GeoUtilsTest.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/util/GeoUtils.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentCheckinController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerDataApiController.java

**Interfaces:**
- Produces:

    public ActivityRegistration checkIn(
        User student, Long activityId, Long classId, String token,
        Double latitude, Double longitude, Double accuracy)

- GeoUtils.haversineMeters throws ValidationException for non-finite or out-of-range coordinates.

- [ ] **Step 1: Write failing check-in tests**

Create one test per behavior:

    rejectsMissingClassId
    rejectsMissingToken
    rejectsWrongStudentClass
    rejectsInvalidQrToken
    rejectsNonOpenActivity
    rejectsBeforeStartTime
    rejectsAfterEndTime
    rejectsNaNLatitudeLongitudeAndAccuracy
    rejectsOutsideRadius
    rejectsCancelledRegistration
    marksRegisteredStudentAttended

Use activity times relative to LocalDateTime.now() with at least one hour margin.

- [ ] **Step 2: Verify RED**

Run StudentCheckinServiceTest and GeoUtilsTest. Expected: missing service and NaN cases fail.

- [ ] **Step 3: Implement validation in one transaction**

StudentCheckinService.checkIn validates in this order:

    require student.studentClass
    require classId and token
    require classId equals student.studentClass.id
    load activity and require status OPEN
    require startTime and endTime and now within inclusive window
    require dynamicQrTokenService.validateToken(token, activityId, classId)
    validate configured GPS using Double.isFinite and coordinate domains
    load registration and require status REGISTERED
    set status ATTENDED and save

Latitude must be -90..90, longitude -180..180, accuracy finite and 0..150.

- [ ] **Step 4: Make both endpoints delegate**

StudentCheckinController.performCheckin and ManagerDataApiController.performCheckin call StudentCheckinService. Keep notifications after successful return and remove duplicate QR/GPS/state validation.

- [ ] **Step 5: Run tests and commit**

Run new tests plus DynamicQrTokenServiceTest. Expected: zero failures.

    git add UniActivity_BE/src/main UniActivity_BE/src/test
    git commit -m "fix: require valid QR time and location for checkin"

### Task 3: Validate score options and make evidence review idempotent

**Files:**
- Create: UniActivity_BE/src/main/java/com/example/uniactivity/service/EvidenceReviewService.java
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/EvidenceReviewServiceTest.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/repository/ActivityRegistrationRepository.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/repository/ScoreOptionRepository.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/repository/TrainingPointDetailRepository.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/service/ActivityService.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/service/TrainingPointService.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentCheckinController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerDataApiController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerActivityController.java

**Interfaces:**
- Produces:

    public ScoreOption requireScoreOption(Long activityId, Long scoreOptionId)
    public EvidenceReviewResult approve(User manager, Long registrationId)
    public void reject(User manager, Long registrationId, String reason)
    public boolean addScoreOnce(User student, String criteriaCode, Integer score,
        String sourceType, Long referenceId, String description)

- EvidenceReviewResult contains score and activityName.

- [ ] **Step 1: Write failing tests**

Test cross-activity score option rejection, missing evidence rejection, approve-after-reject conflict, reject-after-approve conflict, and two sequential approve calls producing one addScoreOnce invocation.

- [ ] **Step 2: Verify RED**

Run EvidenceReviewServiceTest. Expected: missing service/repository methods.

- [ ] **Step 3: Add locked and source-reference repository queries**

Add:

    Optional<ActivityRegistration> findByIdForUpdate(Long id)
    Optional<ScoreOption> findByIdAndActivity_Id(Long optionId, Long activityId)
    Optional<TrainingPointDetail> findByStudentTrainingPointAndCriteriaCodeAndSourceTypeAndReferenceId(
        StudentTrainingPoint stp, String criteriaCode, String sourceType, Long referenceId)

findByIdForUpdate uses LockModeType.PESSIMISTIC_WRITE and Query selecting by ID.

- [ ] **Step 4: Implement score and review services**

TrainingPointService.addScoreOnce returns false when the exact source/reference already exists. Otherwise it creates one detail, recalculates totals and returns true.

EvidenceReviewService methods are Transactional. They load through findByIdForUpdate, call ManagerScopeAuthorizationService, require evidenceUrl nonblank and isApproved null, then set the final state. approve uses source AUTO_ACTIVITY and registration ID as reference. reject requires a nonblank trimmed reason.

- [ ] **Step 5: Delegate controllers and validate submission option**

Both evidence-upload paths call requireScoreOption(activityId, scoreOptionId). Manager approve/reject endpoints call EvidenceReviewService with authenticated manager. Notifications use EvidenceReviewResult only after the service succeeds.

- [ ] **Step 6: Run tests and commit**

Run EvidenceReviewServiceTest, ManagerScopeAuthorizationServiceTest and existing point tests. Expected: zero failures.

    git add UniActivity_BE/src/main UniActivity_BE/src/test
    git commit -m "fix: make evidence review scoped and idempotent"

### Task 4: Route GPA and claimed scores through approval

**Files:**
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/ScoringRulesServiceTest.java
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/PointRequestServiceValidationTest.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/service/ScoringRulesService.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/service/PointRequestService.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentPointController.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerDataApiController.java
- Modify: UniActivity_FE/src/pages/student/MyScores.jsx

**Interfaces:**
- Produces:

    public void validateGpa(double currentGpa, double previousGpa)
    public int getMaximumClaimedScore(String criteriaCode)

- PointRequestService.createPointRequest requires claimedScore nonnull and 0..maximum.

- [ ] **Step 1: Write failing validation tests**

Test GPA NaN, infinity, below 0 and above 10. Test point request null, negative and above criteria maximum. Test valid boundary values.

- [ ] **Step 2: Verify RED**

Run both new test classes. Expected: invalid values are currently accepted.

- [ ] **Step 3: Implement explicit score ceilings**

getMaximumClaimedScore returns these exact maxima:

    1.1=22, 1.3=5, 1.4=4, 2.1=20, 2.2=5,
    2.3=0, 3.3=0, 5.1=7, 5.3=3, 6.1=10

Unknown criteria throws ValidationException. validateGpa requires both numbers finite and in 0..10. calculateAcademicScore calls validateGpa first.

- [ ] **Step 4: Change GPA save to create a pending request**

StudentPointController.saveGpaScore accepts currentGpa, previousGpa, description and evidenceImageUrl. It calculates the score and calls createPointRequest with criteria 1.1. It does not call TrainingPointService.

ManagerDataApiController duplicate GPA endpoint delegates to the same request service behavior.

MyScores.jsx includes description and the uploaded evidenceImageUrl in the save-gpa-score body. Success copy says the request was submitted for approval.

- [ ] **Step 5: Run tests and commit**

Run new tests and existing point/security tests. Expected: zero failures.

    git add UniActivity_BE/src UniActivity_FE/src/pages/student/MyScores.jsx
    git commit -m "fix: require approval for GPA and claimed scores"

### Task 5: Add database uniqueness and aggregate score reads

**Files:**
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/entity/ActivityRegistration.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/entity/TrainingPointDetail.java
- Modify: UniActivity_BE/src/main/java/com/example/uniactivity/service/TrainingPointService.java
- Modify: UniActivity_BE/database_schema.sql
- Create: UniActivity_BE/src/test/java/com/example/uniactivity/service/TrainingPointServiceTest.java

**Interfaces:**
- Registration unique key: activity_id plus student_id.
- Automated detail unique key: student_training_point_id, criteria_code, source_type, reference_id.
- getScoresByCriteria sums all detail rows with the same criteria code.

- [ ] **Step 1: Write failing aggregate/idempotency tests**

Test two different activity references under criteria 3.1 sum correctly, while the same source/reference does not create a second contribution.

- [ ] **Step 2: Verify RED**

Run TrainingPointServiceTest. Expected: current map overwrite or criteria-level accumulation fails the expected model.

- [ ] **Step 3: Implement entity/schema constraints and aggregation**

Add named UniqueConstraint declarations to both entities and matching UNIQUE KEY statements in database_schema.sql. Replace map put in getScoresByCriteria with merge(criteriaCode, score, Integer::sum).

- [ ] **Step 4: Run tests and commit**

Run TrainingPointServiceTest and all focused tests from Tasks 1-4.

    git add UniActivity_BE/src UniActivity_BE/database_schema.sql
    git commit -m "fix: enforce unique registration and score references"

### Task 6: Regression verification and residual audit

**Files:**
- Modify: docs/security-improvement-progress.md
- Create: docs/security-audit-2026-08-09-follow-up.md

**Interfaces:**
- Produces: reproducible test/audit evidence and a remaining-risk list for OTP, upload, sessions, dependencies and migrations.

- [ ] **Step 1: Run all focused backend tests**

Run every new test plus JwtAuthControllerTest, JwtAuthenticationFilterTest, JwtTokenProviderTest, DynamicQrTokenServiceTest and SecretSeparationValidatorTest. Expected: zero failures.

- [ ] **Step 2: Run full backend and frontend verification**

    cd UniActivity_BE && JAVA_HOME=/home/daidev/.jdks/temurin-21.0.12 PATH=/home/daidev/.jdks/temurin-21.0.12/bin:$PATH bash ./mvnw test
    cd UniActivity_FE && node --test src/utils/*.test.js
    cd UniActivity_FE && npm run lint
    cd UniActivity_FE && npm run build
    cd UniActivity_FE && npm audit --omit=dev

Record exact failures caused by unavailable MySQL or dependency installation; do not report them as passing.

- [ ] **Step 3: Re-scan exploitable patterns**

Use ripgrep to confirm no optional QR branch, cross-activity score lookup, direct AUTO_GPA write, unscoped registration findById in manager review, or raw exception message remains in changed endpoints.

- [ ] **Step 4: Update security documentation**

Mark only verified P0 items complete. Follow-up report lists unresolved OTP/reset, upload validation, registration transaction boundary, stateless API chain, dependency advisories and frontend lint.

- [ ] **Step 5: Commit and push feature branch**

    git add docs
    git commit -m "docs: record P0 hardening verification"
    git push -u origin security/p0-access-checkin-score

Expected: push succeeds without .env or generated secrets.
