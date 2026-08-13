# UniActivity Security and Logic Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current UniActivity backend and frontend safe to deploy by fixing schema blockers, authorization gaps, check-in bypasses, score duplication, unsafe uploads, weak password reset, XSS, transaction races, dependency advisories, and the broken default test workflow.

**Architecture:** Continue the existing `security/p0-access-checkin-score` worktree and keep controllers thin. Authorization and state transitions live in focused transactional services, immutable source/reference keys make scoring idempotent, uploads pass through one storage boundary, and OTP lifecycle rules live in one service. Database invariants are enforced by versioned Flyway migrations rather than relying on `ddl-auto=update`.

**Tech Stack:** Java 21, Spring Boot 3.5.8, Spring Security, Spring Data JPA, MySQL, H2 for tests, Flyway, JUnit 5, Mockito, React 19, Vite, ESLint, Node test runner.

## Global Constraints

- Execute in `.worktrees/security-p0-access-checkin-score`; do not overwrite the dirty `main` worktree.
- Preserve existing public endpoint paths and the JSON `message` field unless a task explicitly changes the contract.
- Never authorize using a request-supplied ID without loading the related entity and checking the authenticated user.
- Manager operations are restricted to `manager.studentClass`; student profile/score access defaults to authenticated self.
- All state-changing concurrency fixes must be protected by both a transaction/lock and a database constraint where possible.
- Use TDD for every behavior change: observe RED, implement the minimum fix, then observe GREEN.
- Never add `UniActivity_BE/.env`, real credentials, generated uploads, `.idea`, or test reports to Git.
- No task may be marked complete while its focused verification command is failing.

---

### Task 1: Stabilize the test harness and fix the schema-startup blocker

**Files:**
- Modify: `UniActivity_BE/pom.xml`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/Activity.java:14`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/UniactivityApplicationTests.java`
- Create: `UniActivity_BE/src/test/resources/application-test.properties`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/entity/ActivityMappingTest.java`
- Modify: `.gitignore`

**Interfaces:**
- Produces: a default `./mvnw test` path that does not require the developer's MySQL database or dynamic Byte Buddy attachment.
- Produces: an `activities.created_by` index mapping consistent with `@JoinColumn(name = "created_by")`.

- [ ] **Step 1: Write a mapping regression test and isolate the context test**

```java
@DataJpaTest
@ActiveProfiles("test")
class ActivityMappingTest {
    @Autowired EntityManager entityManager;

    @Test
    void activityMetadataBootsWithCreatedByIndex() {
        assertThat(entityManager.getMetamodel().entity(Activity.class)).isNotNull();
    }
}
```

Add `@ActiveProfiles("test")` to `UniactivityApplicationTests` and create test properties using an in-memory H2 database, `create-drop`, disabled mail delivery, and distinct test-only JWT/QR secrets.

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
cd UniActivity_BE
./mvnw -Dtest=ActivityMappingTest test
```

Expected: context/schema metadata fails because `created_by_id` is not the mapped column, or because H2/test configuration is not yet present.

- [ ] **Step 3: Fix the mapping and deterministic Mockito startup**

Change the index declaration to:

```java
@Index(name = "idx_activity_created_by", columnList = "created_by")
```

Add H2 and `byte-buddy-agent` as test dependencies. Configure Maven Surefire with:

```xml
<argLine>-javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar</argLine>
```

Add `.idea/`, `target/`, `karate-reports/`, and frontend build output to `.gitignore` without removing any existing ignore rules.

- [ ] **Step 4: Verify focused and default tests**

Run:

```bash
cd UniActivity_BE
./mvnw -Dtest=ActivityMappingTest,UniactivityApplicationTests test
./mvnw -Dtest='!ApiTestRunner' test
```

Expected: both commands exit 0 without a local MySQL server and without manual `JAVA_TOOL_OPTIONS`.

- [ ] **Step 5: Commit**

```bash
git add .gitignore UniActivity_BE/pom.xml UniActivity_BE/src/main/java/com/example/uniactivity/entity/Activity.java UniActivity_BE/src/test
git commit -m "test: isolate backend tests and fix activity mapping"
```

### Task 2: Finish P0 authorization, check-in, evidence, and score integrity

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/ManagerScopeAuthorizationService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentCheckinService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/EvidenceSubmissionService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/EvidenceReviewService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/TrainingPointService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/PointRequestService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentCheckinController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/student/StudentDataApiController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerActivityController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerDataApiController.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/ManagerScopeAuthorizationServiceTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentCheckinServiceTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/EvidenceSubmissionServiceTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/EvidenceReviewServiceTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/PointRequestServiceValidationTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/TrainingPointServiceTest.java`

**Interfaces:**
- Consumes: the partially implemented services already present in `security/p0-access-checkin-score`.
- Produces: `checkIn(User, Long, Long, String, Double, Double, Double)` with mandatory QR/class/time checks.
- Produces: `submit(User, Long, Long, List<MultipartFile>)`, `approve(User, Long)`, and `reject(User, Long, String)` with scoped, terminal evidence state transitions.
- Produces: `addScoreOnce(..., sourceType, referenceId, ...)` keyed by registration ID for `AUTO_ACTIVITY`.

- [ ] **Step 1: Complete failing tests for every exploit path**

Required test methods:

```java
rejectsMissingClassId();
rejectsMissingQrToken();
rejectsWrongClassAndInvalidToken();
rejectsClosedBeforeStartAndAfterEndActivity();
rejectsNaNInfiniteAndOutOfRangeGps();
rejectsCrossActivityScoreOption();
rejectsEvidenceReviewWithoutEvidence();
rejectsApproveAfterRejectAndRejectAfterApprove();
secondApprovalDoesNotAddScoreAgain();
studentCannotReadAnotherUsersScores();
managerCannotReadOrGenerateQrOutsideManagedClass();
```

- [ ] **Step 2: Run focused tests and record RED cases**

```bash
cd UniActivity_BE
./mvnw -Dtest=ManagerScopeAuthorizationServiceTest,StudentCheckinServiceTest,EvidenceSubmissionServiceTest,EvidenceReviewServiceTest,PointRequestServiceValidationTest,TrainingPointServiceTest test
```

Expected: at least the still-unimplemented cross-controller authorization and concurrency cases fail before implementation.

- [ ] **Step 3: Complete controller delegation**

Both duplicate check-in endpoints must reduce to the same service call:

```java
ActivityRegistration registration = studentCheckinService.checkIn(
    currentUser, activityId, classId, token, lat, lng, accuracy);
```

Both evidence upload endpoints delegate to `EvidenceSubmissionService`. Manager QR/detail/score endpoints call `ManagerScopeAuthorizationService`; student `/users/{id}/scores` requires `id.equals(currentUser.getId())` or is replaced internally by `/me/scores` while keeping the old route guarded.

- [ ] **Step 4: Enforce locked terminal transitions and stable score identity**

`EvidenceReviewService` must load the registration using `PESSIMISTIC_WRITE`, require `isApproved == null`, and use:

```java
trainingPointService.addScoreOnce(
    registration.getStudent(), criteriaCode, score,
    "AUTO_ACTIVITY", registration.getId(), description);
```

Point requests must validate `claimedScore != null`, `claimedScore >= 0`, and `claimedScore <= scoringRulesService.getMaximumClaimedScore(criteriaCode)`. GPA values must be finite and between `0.0` and `10.0`; GPA submission creates a pending point request rather than writing `AUTO_GPA` directly.

- [ ] **Step 5: Verify and commit**

Run the focused suite from Step 2. Expected: zero failures.

```bash
git add UniActivity_BE/src/main UniActivity_BE/src/test database_schema.sql
git commit -m "fix: enforce scoped checkin and idempotent scoring"
```

### Task 3: Make registration and point approval concurrency-safe

**Files:**
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/ActivityRegistrationTransactionService.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/ActivityRegistrationTransactionServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/ActivityService.java:433-590`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/ActivityRegistrationRepository.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/PointRequestRepository.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/PointRequestService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/ActivityRegistration.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/TrainingPointDetail.java`
- Create: `UniActivity_BE/src/main/resources/db/migration/V2__security_integrity_constraints.sql`
- Modify: `UniActivity_BE/src/main/resources/application.properties`
- Modify: `database_schema.sql`

**Interfaces:**
- Produces: public transactional `registerOnce(User, Long)` and `cancelOnce(User, Long)` methods invoked through a separate Spring bean.
- Produces: locked `findByIdForUpdate(Long)` for point request and evidence transitions.
- Database invariants: unique `(student_id, activity_id)` registration and unique `(student_training_point_id, criteria_code, source_type, reference_id)` automated contribution.

- [ ] **Step 1: Write transaction and uniqueness tests**

```java
@Test void reactivationRechecksVisibilityAndCapacity() { /* cancelled registration cannot bypass current rules */ }
@Test void fullSlotDoesNotIncrementPastCapacity() { /* no save when current == max */ }
@Test void duplicateRegistrationMapsToConflict() { /* DataIntegrityViolationException -> 409 */ }
@Test void pointRequestApprovalLoadsWithWriteLock() { /* repository locked method is used */ }
```

- [ ] **Step 2: Verify RED**

```bash
cd UniActivity_BE
./mvnw -Dtest=ActivityRegistrationTransactionServiceTest,PointRequestServiceValidationTest test
```

Expected: private self-invoked transaction and missing constraints fail the assertions.

- [ ] **Step 3: Move transactional work to the new bean**

```java
@Service
@RequiredArgsConstructor
public class ActivityRegistrationTransactionService {
    @Transactional
    public Map<String, Object> registerOnce(User student, Long activityId) { /* full existing transition */ }

    @Transactional
    public Map<String, Object> cancelOnce(User student, Long activityId) { /* full existing transition */ }
}
```

`ActivityService` keeps bounded optimistic retry but calls this injected bean. Reactivation must run the same OPEN, deadline, visibility, matching-slot, and capacity validations as a new registration.

- [ ] **Step 4: Add Flyway migration and switch production schema handling**

Migration must first detect/report duplicate registrations and automated score references, then add named unique indexes. Add Flyway, set `spring.jpa.hibernate.ddl-auto=validate`, and enable `spring.flyway.baseline-on-migrate=true` with baseline version `1` for existing installations.

- [ ] **Step 5: Verify and commit**

```bash
cd UniActivity_BE
./mvnw -Dtest=ActivityRegistrationTransactionServiceTest,PointRequestServiceValidationTest,TrainingPointServiceTest test
./mvnw -DskipTests package
git add src pom.xml ../database_schema.sql
git commit -m "fix: make registration and approval atomic"
```

Expected: tests and package exit 0.

### Task 4: Centralize and harden evidence uploads

**Files:**
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/EvidenceStorageService.java`
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/config/UploadProperties.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/EvidenceStorageServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/EvidenceSubmissionService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/FileUploadService.java`
- Modify: `UniActivity_BE/src/main/resources/application.properties`

**Interfaces:**
- Produces: `List<String> storeEvidence(Long registrationId, List<MultipartFile> files)`.
- Accepts only decoded JPEG, PNG, and WebP files; maximum 3 files and 5 MiB per file.
- Storage paths are generated from UUIDs beneath `${app.upload.root}/evidence/<registrationId>` and never from a client filename.

- [ ] **Step 1: Write malicious upload tests**

```java
rejectsSpoofedContentType();
rejectsSvgAndHtmlPayloads();
rejectsOversizedAndEmptyFiles();
ignoresOriginalTraversalFilename();
generatedPathAlwaysStaysUnderUploadRoot();
storesValidPngWithServerChosenExtension();
```

- [ ] **Step 2: Verify RED**

```bash
cd UniActivity_BE
./mvnw -Dtest=EvidenceStorageServiceTest test
```

Expected: current extension-based storage accepts at least one malicious fixture.

- [ ] **Step 3: Implement a single storage boundary**

Resolve and normalize the target path, then enforce containment:

```java
Path root = uploadProperties.root().toAbsolutePath().normalize();
Path target = root.resolve("evidence").resolve(registrationId.toString())
    .resolve(UUID.randomUUID() + detectedExtension).normalize();
if (!target.startsWith(root)) throw new ValidationException("Đường dẫn tệp không hợp lệ");
```

Decode the image to validate bytes, reject unsupported formats, write with `CREATE_NEW`, and make delete operations apply the same normalized containment check. Controllers and `EvidenceSubmissionService` must not call `Files.copy` directly.

- [ ] **Step 4: Verify and commit**

```bash
cd UniActivity_BE
./mvnw -Dtest=EvidenceStorageServiceTest,EvidenceSubmissionServiceTest test
git add src
git commit -m "fix: validate and isolate evidence uploads"
```

### Task 5: Harden OTP verification and password reset

**Files:**
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/OtpService.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/OtpServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/auth/PasswordResetController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/PasswordResetToken.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/PasswordResetTokenRepository.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/UserRepository.java`
- Create: `UniActivity_BE/src/main/resources/db/migration/V3__harden_otp_tokens.sql`

**Interfaces:**
- Produces: `issue(email, type)`, `verify(email, type, otp)`, and `consumeForPasswordReset(email, otp, newPassword)`.
- OTPs use `SecureRandom`, are stored as one-way hashes, expire after 5 minutes, allow at most 5 failed attempts, and are consumed under a pessimistic write lock.
- Successful password reset atomically increments `users.token_version`.

- [ ] **Step 1: Write OTP lifecycle tests**

```java
storesHashInsteadOfPlainOtp();
rejectsExpiredUsedAndWrongTypeOtp();
locksAfterFiveFailedAttempts();
onlyOneConcurrentConsumeCanSucceed();
passwordResetIncrementsTokenVersion();
forgotPasswordReturnsSameResponseForKnownAndUnknownEmail();
```

- [ ] **Step 2: Verify RED**

```bash
cd UniActivity_BE
./mvnw -Dtest=OtpServiceTest test
```

- [ ] **Step 3: Implement the OTP state machine**

Generate exactly six digits with `SecureRandom.nextInt(1_000_000)`, store a password-encoder hash, increment `failedAttempts` on mismatch, mark used on successful consumption, and lock the latest usable row using `PESSIMISTIC_WRITE`. `forgot-password` always returns the same 200 response regardless of account existence or verification state; email is sent only when eligible.

Reset password and `tokenVersion` within the same transaction:

```java
user.setPasswordHash(passwordEncoder.encode(newPassword));
user.setTokenVersion(user.getTokenVersion() + 1);
token.setUsed(true);
```

- [ ] **Step 4: Verify and commit**

```bash
cd UniActivity_BE
./mvnw -Dtest=OtpServiceTest,JwtAuthenticationFilterTest,JwtAuthControllerTest test
git add src
git commit -m "fix: harden OTP and revoke sessions on reset"
```

### Task 6: Remove frontend XSS and normalize toast calls

**Files:**
- Modify: `UniActivity_FE/src/utils/toast.js`
- Create: `UniActivity_FE/src/utils/toast.test.js`
- Modify: every `UniActivity_FE/src/**/*.jsx` call site returned by `rg -n "showToast\\(" UniActivity_FE/src`

**Interfaces:**
- Produces: `showToast(title, message, type = 'info')` with all dynamic strings assigned through `textContent`.
- Toast type is always one of `info`, `success`, `warning`, or `error`.

- [ ] **Step 1: Write a DOM-safety regression test**

Extract a pure helper:

```javascript
export function normalizeToast({ title, message, type }) {
  return {
    title: String(title ?? ''),
    message: String(message ?? ''),
    type: ['info', 'success', 'warning', 'error'].includes(type) ? type : 'info',
  }
}
```

Test that `<img src=x onerror=alert(1)>` remains text and is never returned as markup.

- [ ] **Step 2: Verify RED**

```bash
cd UniActivity_FE
node --test src/utils/toast.test.js
```

- [ ] **Step 3: Replace `innerHTML` with DOM creation**

Build fixed wrapper elements with `document.createElement`, assign `titleNode.textContent` and `messageNode.textContent`, and append them. No user/API/SSE-controlled value may be interpolated into HTML strings.

Normalize all incorrect two-argument calls such as:

```javascript
showToast('Thành công', data.message, 'success')
showToast('Có lỗi xảy ra', error.message, 'error')
```

- [ ] **Step 4: Verify and commit**

```bash
cd UniActivity_FE
node --test src/utils/*.test.js
npm run build
git add src
git commit -m "fix: render toast content without innerHTML"
```

### Task 7: Correct exception handling and remove insecure admin provisioning

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/exception/GlobalExceptionHandler.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/exception/GlobalExceptionHandlerTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/util/AdminAccountCreator.java`

**Interfaces:**
- `AsyncRequestNotUsableException` has a dedicated no-response handler.
- Ordinary `IOException` and wrapped I/O errors remain 500 and are logged once.
- Authorization maps to 403, state conflicts and duplicate keys map to 409, validation maps to 400.
- Admin provisioning never contains or prints a default plaintext password.

- [ ] **Step 1: Write exception mapping tests**

```java
authorizationReturns403();
conflictReturns409();
validationReturns400();
wrappedIOExceptionReturns500();
asyncDisconnectDoesNotBuildAnotherResponse();
```

- [ ] **Step 2: Verify RED**

```bash
cd UniActivity_BE
./mvnw -Dtest=GlobalExceptionHandlerTest test
```

- [ ] **Step 3: Narrow the SSE handler and sanitize controller errors**

Use a dedicated handler:

```java
@ExceptionHandler(AsyncRequestNotUsableException.class)
public void handleAsyncDisconnect(AsyncRequestNotUsableException ex) {
    logger.debug("SSE client disconnected: {}", ex.getMessage());
}
```

Remove the `IOException` cause special case from `handleGeneral`. Changed controllers must throw typed domain exceptions instead of returning `e.getMessage()` to clients.

- [ ] **Step 4: Make admin creation opt-in and secret-free**

Replace `admin123` with a required command-line/environment input, print only the generated SQL hash, and exit non-zero when username, email, or password is missing. Never print the plaintext password. If the utility is not needed, remove it only after explicit confirmation because it is currently an untracked user file.

- [ ] **Step 5: Verify and commit**

```bash
cd UniActivity_BE
./mvnw -Dtest=GlobalExceptionHandlerTest test
rg -n 'admin123|Password:|body\(Map\.of\("message", e\.getMessage\(\)\)\)' src/main
git add src
git commit -m "fix: standardize errors and secure admin provisioning"
```

Expected: tests pass; the scan finds no default password or raw exception response in changed endpoints.

### Task 8: Repair dependencies, lint failures, and the oversized frontend bundle

**Files:**
- Modify: `UniActivity_FE/package.json`
- Modify: `UniActivity_FE/package-lock.json`
- Modify: frontend files reported by `npm run lint`
- Modify: `UniActivity_FE/src/App.jsx`

**Interfaces:**
- Runtime dependency audit reports zero known vulnerabilities.
- ESLint exits with zero errors; warnings are either resolved or documented with a narrow justification.
- Route pages are loaded through `React.lazy` and `Suspense` so the main production chunk is below 500 KiB when practical.

- [ ] **Step 1: Capture the dependency and lint baseline**

```bash
cd UniActivity_FE
npm audit --omit=dev
npm run lint
npm run build
```

Expected baseline: 5 runtime advisories, 36 lint errors/15 warnings, and a main JS chunk around 1.47 MB.

- [ ] **Step 2: Apply the smallest safe dependency updates**

Run `npm audit fix`, inspect `package.json` and lockfile, and reject any major framework migration not required by an advisory. Confirm `axios`, `follow-redirects`, `form-data`, `react-router`, and `react-router-dom` resolve to non-vulnerable versions.

- [ ] **Step 3: Fix lint violations by rule, not by disabling ESLint**

Remove unused variables/imports; replace synchronous derived-state effects with initializers or memoized values; add complete effect dependency arrays or stabilize callbacks with `useCallback`; move non-component exports out of refresh-sensitive component files; replace empty catches with an explicit comment plus logging or handling. Do not add file-wide `eslint-disable` comments.

- [ ] **Step 4: Split routes**

Convert page imports in `App.jsx` to:

```javascript
const StudentDashboard = lazy(() => import('./pages/student/Dashboard'))
const ManagerDashboard = lazy(() => import('./pages/manager/Dashboard'))
const AdminDashboard = lazy(() => import('./pages/admin/Dashboard'))
```

Apply the same pattern to the remaining page-level routes and wrap the route tree in one accessible `Suspense` fallback.

- [ ] **Step 5: Verify and commit**

```bash
cd UniActivity_FE
npm audit --omit=dev
npm run lint
node --test src/utils/*.test.js
npm run build
git add package.json package-lock.json src
git commit -m "fix: clear frontend audit and lint failures"
```

Expected: all commands exit 0. Record remaining chunk warnings rather than claiming they are fixed if any chunk still exceeds 500 KiB.

### Task 9: Make Karate integration tests self-contained

**Files:**
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/karate/ApiTestRunner.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/karate/api-test.feature`
- Create: `UniActivity_BE/src/test/resources/data-test.sql`

**Interfaces:**
- Karate starts the Spring application on a random port under the `test` profile.
- Test login credentials exist only in the H2 fixture and are not hard-coded production/demo credentials.

- [ ] **Step 1: Change the runner to own application lifecycle**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `@ActiveProfiles("test")`. Pass the injected local port to Karate as `baseUrl`; the feature consumes that value instead of `http://localhost:8080`.

- [ ] **Step 2: Create deterministic test users**

`data-test.sql` creates one ACTIVE, email-verified STUDENT with a BCrypt hash for a test-only password. Rename the feature credentials to clearly non-production values such as `karate-student` and `KarateTest123!`.

- [ ] **Step 3: Verify all eight API scenarios**

```bash
cd UniActivity_BE
./mvnw -Dtest=ApiTestRunner test
```

Expected: 8 tests, 0 failures, 0 errors, with no separately running server or MySQL.

- [ ] **Step 4: Commit**

```bash
git add src/test
git commit -m "test: make Karate API suite self-contained"
```

### Task 10: Full regression gate, documentation, and integration handoff

**Files:**
- Modify: `docs/security-improvement-progress.md`
- Create: `docs/security-audit-2026-08-13-follow-up.md`

**Interfaces:**
- Produces: reproducible evidence for every completed remediation and a precise list of residual risks.
- Produces: a clean feature branch that can be reviewed before merging into dirty `main`.

- [ ] **Step 1: Run the complete backend gate**

```bash
cd UniActivity_BE
./mvnw clean test
./mvnw -DskipTests package
```

Expected: zero test failures/errors and BUILD SUCCESS for both commands.

- [ ] **Step 2: Run the complete frontend gate**

```bash
cd UniActivity_FE
node --test src/utils/*.test.js
npm run lint
npm run build
npm audit --omit=dev
```

Expected: every command exits 0.

- [ ] **Step 3: Run repository/security hygiene checks**

```bash
git diff --check
git status --short
git ls-files | rg '(^|/)\.env$|\.idea/|target/|dist/'
rg -n 'admin123|new Random\(\)|\.innerHTML|created_by_id|return null;.*SSE|AUTO_GPA' UniActivity_BE/src UniActivity_FE/src
```

Expected: no whitespace errors, no tracked local artifacts/secrets, and no remaining instance of the vulnerable patterns. Review any intentional match manually before accepting it.

- [ ] **Step 4: Update the security evidence documents**

Record exact commands, test counts, audit result, migration names, and any unresolved issue. Mark checklist items complete only when the corresponding command from Steps 1-3 passed in the same execution session.

- [ ] **Step 5: Review the feature branch before integration**

```bash
git log --oneline --decorate origin/main..HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
```

Do not merge automatically into the dirty `main` worktree. Present the commit list and diff summary, then choose between a reviewed merge, cherry-picking milestones, or first preserving the user's uncommitted main changes.

## Milestone Order and Release Gates

1. **Gate A — Bootable and testable:** Task 1.
2. **Gate B — P0 authorization and score integrity:** Tasks 2-3. Do not deploy before this gate passes.
3. **Gate C — Input and account security:** Tasks 4-7.
4. **Gate D — Maintainability and regression safety:** Tasks 8-10.

Each gate is independently reviewable. If time is constrained, stop only at a gate boundary and keep the application undeployed until Gate B is complete.
