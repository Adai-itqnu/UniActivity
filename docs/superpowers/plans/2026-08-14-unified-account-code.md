# Unified 8-Digit Account Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every STUDENT and MANAGER a unique random 8-digit account code across local registration, Google OAuth, admin creation, role changes, and existing data while leaving ADMIN usernames unchanged.

**Architecture:** Centralize code generation and validation in an injectable `AccountCodeGenerator`. All account creation and role-transition services consume it; a Flyway Java migration repairs legacy rows and installs the same role-aware database invariant. The React UI continues sending the `username` API field but presents it as an account code or email to users.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security OAuth2, Flyway/MySQL, JUnit 5/Mockito/H2, React/Vite, Vitest.

## Global Constraints

- STUDENT and MANAGER usernames must match exactly `^[0-9]{8}$`.
- ADMIN usernames remain unchanged and may be non-numeric.
- Codes are random in the inclusive range `10000000` through `99999999` and unique across all users.
- Existing valid 8-digit usernames remain unchanged.
- Repairing a legacy non-admin username increments `token_version` to invalidate old JWTs.
- Google accounts are linked only by Google-verified email and retain the existing role and status.
- Login continues accepting either email or username/account code.

---

## File Structure

- `UniActivity_BE/src/main/java/com/example/uniactivity/service/AccountCodeGenerator.java`: the single code format and uniqueness policy.
- `UniActivity_BE/src/main/java/com/example/uniactivity/service/UserService.java`: public STUDENT registration.
- `UniActivity_BE/src/main/java/com/example/uniactivity/security/CustomOAuth2UserService.java`: Google account creation and legacy self-healing.
- `UniActivity_BE/src/main/java/com/example/uniactivity/service/UserManagementService.java`: admin-created users and role transitions.
- `UniActivity_BE/src/main/java/com/example/uniactivity/dto/admin/UserDto.java`: permits an omitted username for generated-code roles.
- `UniActivity_BE/src/main/java/db/migration/V4__normalize_non_admin_account_codes.java`: existing-data repair and database constraint.
- `UniActivity_FE/src/pages/AuthPage.jsx`, `UniActivity_FE/src/pages/admin/UserList.jsx`, `UniActivity_FE/src/pages/student/Profile.jsx`: user-facing copy and conditional admin form behavior.
- `UniActivity_FE/src/components/{admin,manager,student}/*Header.jsx`, `UniActivity_FE/src/components/common/UserProfileModal.jsx`, and member/class pages: replace account-code labels and remove username-style `@` decoration.

### Task 1: Central account-code generator

**Files:**
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/AccountCodeGenerator.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/AccountCodeGeneratorTest.java`

**Interfaces:**
- Consumes: `UserRepository.existsByUsername(String)`.
- Produces: `String generateUniqueCode()` and `boolean isValidCode(String)`.

- [ ] **Step 1: Write the failing generator tests**

```java
@ExtendWith(MockitoExtension.class)
class AccountCodeGeneratorTest {
    @Mock UserRepository userRepository;

    @Test
    void retriesCollisionAndReturnsEightDigits() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(90_000_000)).thenReturn(1, 2);
        when(userRepository.existsByUsername("10000001")).thenReturn(true);
        when(userRepository.existsByUsername("10000002")).thenReturn(false);

        AccountCodeGenerator generator = new AccountCodeGenerator(userRepository, random);

        assertEquals("10000002", generator.generateUniqueCode());
    }

    @Test
    void validatesExactlyEightDigits() {
        AccountCodeGenerator generator = new AccountCodeGenerator(userRepository, mock(SecureRandom.class));
        assertTrue(generator.isValidCode("12345678"));
        assertFalse(generator.isValidCode("manager1"));
        assertFalse(generator.isValidCode("1234567"));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `cd UniActivity_BE && mvn -Dtest=AccountCodeGeneratorTest test`

Expected: FAIL because `AccountCodeGenerator` does not exist.

- [ ] **Step 3: Implement the focused generator**

```java
@Service
public class AccountCodeGenerator {
    static final int MAX_ATTEMPTS = 10;
    private final UserRepository userRepository;
    private final SecureRandom random;

    public AccountCodeGenerator(UserRepository userRepository) {
        this(userRepository, new SecureRandom());
    }

    AccountCodeGenerator(UserRepository userRepository, SecureRandom random) {
        this.userRepository = userRepository;
        this.random = random;
    }

    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = Integer.toString(10_000_000 + random.nextInt(90_000_000));
            if (!userRepository.existsByUsername(code)) return code;
        }
        throw new IllegalStateException("Không thể tạo mã tài khoản. Vui lòng thử lại.");
    }

    public boolean isValidCode(String value) {
        return value != null && value.matches("^[0-9]{8}$");
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd UniActivity_BE && mvn -Dtest=AccountCodeGeneratorTest test`

Expected: PASS.

- [ ] **Step 5: Commit the generator**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/AccountCodeGenerator.java UniActivity_BE/src/test/java/com/example/uniactivity/service/AccountCodeGeneratorTest.java
git commit -m "feat: centralize account code generation"
```

### Task 2: Public registration and Google OAuth

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/UserService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/CustomOAuth2UserService.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/service/UserServiceTest.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/security/CustomOAuth2UserServiceTest.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/security/CustomUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `AccountCodeGenerator.generateUniqueCode()` and `isValidCode(String)`.
- Produces: generated codes for new local/Google users and self-healed codes for legacy Google-linked STUDENT/MANAGER users.

- [ ] **Step 1: Add failing flow tests**

Add assertions that public registration calls the generator and saves its result; a new verified Google user gets the generated code; an existing MANAGER named `manager51` gets a generated code and `tokenVersion + 1`; an ADMIN named `admin` retains both username and token version.
Also assert `CustomUserDetailsService.loadUserByUsername` resolves the same account by either its email or its 8-digit username through `findByUsernameOrEmail`.

```java
when(accountCodeGenerator.generateUniqueCode()).thenReturn("12345678");
assertEquals("12345678", savedUser.getUsername());

when(accountCodeGenerator.isValidCode("manager51")).thenReturn(false);
when(accountCodeGenerator.generateUniqueCode()).thenReturn("87654321");
assertEquals("87654321", existing.getUsername());
assertEquals(1L, existing.getTokenVersion());
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `cd UniActivity_BE && mvn -Dtest=UserServiceTest,CustomOAuth2UserServiceTest,CustomUserDetailsServiceTest test`

Expected: FAIL because the services do not consume `AccountCodeGenerator`.

- [ ] **Step 3: Inject and use the generator**

Remove `UserService`'s private random method and call `accountCodeGenerator.generateUniqueCode()`. In OAuth processing, use the generator for new users; for an existing non-admin user whose username fails `isValidCode`, replace it and increment `tokenVersion` before saving. Never alter an ADMIN username.

```java
if (user.getRole() != Role.ADMIN && !accountCodeGenerator.isValidCode(user.getUsername())) {
    user.setUsername(accountCodeGenerator.generateUniqueCode());
    user.setTokenVersion(user.getTokenVersion() + 1);
}
```

- [ ] **Step 4: Run focused tests**

Run: `cd UniActivity_BE && mvn -Dtest=UserServiceTest,CustomOAuth2UserServiceTest,CustomUserDetailsServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit registration/OAuth behavior**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/UserService.java UniActivity_BE/src/main/java/com/example/uniactivity/security/CustomOAuth2UserService.java UniActivity_BE/src/test/java/com/example/uniactivity/service/UserServiceTest.java UniActivity_BE/src/test/java/com/example/uniactivity/security/CustomOAuth2UserServiceTest.java UniActivity_BE/src/test/java/com/example/uniactivity/security/CustomUserDetailsServiceTest.java
git commit -m "feat: assign account codes to local and Google users"
```

### Task 3: Admin user creation and role transitions

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/UserManagementService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/dto/admin/UserDto.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/UserManagementServiceTest.java`

**Interfaces:**
- Consumes: `AccountCodeGenerator`.
- Produces: role-aware username policy for admin CRUD.

- [ ] **Step 1: Write failing service tests**

Test these cases with repository and mapper mocks: creating STUDENT or MANAGER ignores a supplied username and uses `12345678`; creating ADMIN retains `root-admin` and checks username uniqueness; updating ADMIN to MANAGER generates a code; updating STUDENT to ADMIN retains its existing 8-digit username; updating an already-valid MANAGER does not regenerate its code.

```java
when(accountCodeGenerator.generateUniqueCode()).thenReturn("12345678");
service.createUser(dtoWithRole("MANAGER", "legacy-input"));
assertEquals("12345678", saved.getUsername());
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `cd UniActivity_BE && mvn -Dtest=UserManagementServiceTest test`

Expected: FAIL because role-aware generation is absent.

- [ ] **Step 3: Implement role-aware creation and transition logic**

Remove `@NotBlank` from `UserDto.username`. Parse the requested role before creating. For ADMIN, require a nonblank username and validate its uniqueness. For other roles, overwrite the mapped username with a generated code. After `userMapper.updateEntity`, generate a code only when the resulting role is non-admin and the current username is invalid; increment token version when it changes.

```java
private void applyUsernamePolicy(User entity, Role role, boolean create) {
    if (role == Role.ADMIN) {
        if (create && (entity.getUsername() == null || entity.getUsername().isBlank())) {
            throw new IllegalArgumentException("Username ADMIN không được để trống");
        }
        return;
    }
    if (create || !accountCodeGenerator.isValidCode(entity.getUsername())) {
        entity.setUsername(accountCodeGenerator.generateUniqueCode());
        if (!create) entity.setTokenVersion(entity.getTokenVersion() + 1);
    }
}
```

- [ ] **Step 4: Run focused service tests**

Run: `cd UniActivity_BE && mvn -Dtest=UserManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit admin behavior**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/UserManagementService.java UniActivity_BE/src/main/java/com/example/uniactivity/dto/admin/UserDto.java UniActivity_BE/src/test/java/com/example/uniactivity/service/UserManagementServiceTest.java
git commit -m "feat: enforce role-aware account codes in admin flows"
```

### Task 4: Existing-data Flyway migration

**Files:**
- Create: `UniActivity_BE/src/main/java/db/migration/V4__normalize_non_admin_account_codes.java`
- Create: `UniActivity_BE/src/test/java/db/migration/V4__normalize_non_admin_account_codesTest.java`

**Interfaces:**
- Consumes: JDBC `users(id, username, role, token_version)`.
- Produces: repaired non-admin usernames and `chk_users_non_admin_account_code`.

- [ ] **Step 1: Write failing H2 migration tests**

Create a MySQL-mode H2 `users` table containing an ADMIN `root-admin`, a legacy MANAGER `manager51`, a legacy STUDENT `google_abc`, and a valid STUDENT `12345678`. Invoke the migration's package-private normalization method with deterministic random values. Assert ADMIN and valid code are unchanged, invalid rows become distinct 8-digit values, and only changed rows increment token version.

- [ ] **Step 2: Run the focused migration test and verify it fails**

Run: `cd UniActivity_BE && mvn -Dtest=V4__normalize_non_admin_account_codesTest test`

Expected: FAIL because V4 does not exist.

- [ ] **Step 3: Implement the Java migration**

Extend `BaseJavaMigration`. Load every existing username into a set, select invalid STUDENT/MANAGER rows, generate unused codes, update both `username` and `token_version`, verify no invalid non-admin rows remain, and add this MySQL constraint when absent:

```sql
CONSTRAINT chk_users_non_admin_account_code
CHECK (role = 'ADMIN' OR username REGEXP '^[0-9]{8}$')
```

Use prepared statements for row updates and the information schema to avoid adding a duplicate constraint.

- [ ] **Step 4: Run migration and backend tests**

Run: `cd UniActivity_BE && mvn -Dtest=V4__normalize_non_admin_account_codesTest test`

Expected: PASS.

Run: `cd UniActivity_BE && mvn test`

Expected: BUILD SUCCESS with no test failures.

- [ ] **Step 5: Commit migration**

```bash
git add UniActivity_BE/src/main/java/db/migration/V4__normalize_non_admin_account_codes.java UniActivity_BE/src/test/java/db/migration/V4__normalize_non_admin_account_codesTest.java
git commit -m "feat: migrate non-admin users to account codes"
```

### Task 5: React labels and role-aware admin form

**Files:**
- Modify: `UniActivity_FE/src/pages/AuthPage.jsx`
- Modify: `UniActivity_FE/src/pages/admin/UserList.jsx`
- Modify: `UniActivity_FE/src/pages/student/Profile.jsx`
- Modify: `UniActivity_FE/src/components/admin/Header.jsx`
- Modify: `UniActivity_FE/src/components/manager/ManagerHeader.jsx`
- Modify: `UniActivity_FE/src/components/student/StudentHeader.jsx`
- Modify: `UniActivity_FE/src/components/common/UserProfileModal.jsx`
- Modify: `UniActivity_FE/src/pages/manager/JoinRequests.jsx`
- Modify: `UniActivity_FE/src/pages/manager/Dashboard.jsx`
- Modify: `UniActivity_FE/src/pages/manager/Members.jsx`
- Modify: `UniActivity_FE/src/pages/student/MyClass.jsx`
- Create: `UniActivity_FE/src/pages/admin/UserList.test.jsx` only if the existing frontend harness supports component mocks without adding dependencies.

**Interfaces:**
- Consumes: unchanged API field `username`.
- Produces: clear email/code login copy and conditional ADMIN username input.

- [ ] **Step 1: Add or update frontend assertions**

Assert login shows `Email hoặc mã tài khoản`; profile shows `Mã tài khoản`; admin create requires username only for ADMIN and explains automatic generation for STUDENT/MANAGER.

- [ ] **Step 2: Run relevant tests and capture the expected failure**

Run: `cd UniActivity_FE && npm test -- --run`

Expected: new copy/conditional-form assertions FAIL, or existing suite establishes the baseline if no suitable component harness exists.

- [ ] **Step 3: Update UI behavior**

Keep the state and request field named `username` for API compatibility. Change the login label and error to mention email/account code. In `UserList`, validate username only when `role === 'ADMIN'`, show an editable required username input for ADMIN, and show read-only account code on edit or an automatic-generation note for non-admin creation. Rename all visible `Username`, `Tên đăng nhập`, and `Mã sinh viên` labels for this field to `Mã tài khoản`, including headers, profile modal, member lists, join requests, dashboard, and class page; render codes without an email-style `@` prefix.

- [ ] **Step 4: Verify frontend**

Run: `cd UniActivity_FE && npm test -- --run`

Run: `cd UniActivity_FE && npm run lint`

Run: `cd UniActivity_FE && npm run build`

Expected: all commands exit 0.

- [ ] **Step 5: Run final full verification and commit**

Run: `cd UniActivity_BE && mvn test`

Run: `cd UniActivity_FE && npm test -- --run && npm run lint && npm run build`

Expected: all commands exit 0.

```bash
git add UniActivity_FE/src/pages/AuthPage.jsx UniActivity_FE/src/pages/admin/UserList.jsx UniActivity_FE/src/pages/student/Profile.jsx UniActivity_FE/src/components/admin/Header.jsx UniActivity_FE/src/components/manager/ManagerHeader.jsx UniActivity_FE/src/components/student/StudentHeader.jsx UniActivity_FE/src/components/common/UserProfileModal.jsx UniActivity_FE/src/pages/manager/JoinRequests.jsx UniActivity_FE/src/pages/manager/Dashboard.jsx UniActivity_FE/src/pages/manager/Members.jsx UniActivity_FE/src/pages/student/MyClass.jsx UniActivity_FE/src/pages/admin/UserList.test.jsx
git commit -m "feat: present unified account codes in the UI"
```

## Release Procedure

- Back up the production database and rehearse V4 against a restored copy.
- Confirm Flyway reports V2, V3, and V4 successful before accepting traffic.
- Sample ADMIN rows to confirm usernames are unchanged.
- Confirm all STUDENT/MANAGER usernames match eight digits and are unique.
- Confirm a legacy JWT no longer works for a migrated user and email login still works.
- Confirm new local, Google, and admin-created non-admin users receive codes.
