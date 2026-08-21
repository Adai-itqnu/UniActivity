# Unified Six-Character Codes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize class join codes and manual activity check-in codes as six-character uppercase alphanumeric values generated and validated by one shared backend policy.

**Architecture:** Add a Spring `UnifiedCodePolicy` that owns the six-character alphabet, random generation, deterministic HMAC derivation, normalization, and validation. Route Admin and Manager class-code mutations through `StudentClassService`, rotate existing class codes with Flyway V6, and update student inputs to use a matching frontend normalizer while preserving the existing dynamic QR token and 60-second check-in window.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway Java migrations, MySQL 8.0.16+, H2 tests, JUnit 5, Mockito, React 19, Vite, Node test runner.

## Global Constraints

- User-facing class join codes and manual check-in codes are exactly 6 characters.
- The allowed alphabet is exactly `ABCDEFGHJKMNPQRSTUVWXYZ23456789`.
- Every generated code contains at least one letter and at least one digit.
- `I`, `L`, `O`, `0`, and `1` are never generated or accepted.
- Class join codes persist until regeneration; check-in codes remain activity/class scoped and rotate every 60 seconds.
- All existing class join codes are rotated during V6 migration.
- Eight-digit account usernames and long dynamic QR HMAC tokens are unchanged.
- Preserve all unrelated dirty-worktree changes, especially `UniActivity_FE/package-lock.json`.

---

### Task 1: Shared Backend Code Policy

**Files:**
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/UnifiedCodePolicy.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/UnifiedCodePolicyTest.java`

**Interfaces:**
- Produces: `String generateRandomCode()` for class join codes.
- Produces: `String deriveCode(byte[] source)` for deterministic check-in codes.
- Produces: `String normalize(String value)` and `boolean isValid(String value)` for all backend consumers.

- [ ] **Step 1: Write failing policy tests**

```java
@ExtendWith(MockitoExtension.class)
class UnifiedCodePolicyTest {
    @Mock RandomGenerator random;

    @Test
    void randomCodeUsesSixApprovedCharactersWithLetterAndDigit() {
        when(random.nextInt(anyInt())).thenReturn(0, 0, 1, 2, 3, 4);
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);

        String code = policy.generateRandomCode();

        assertEquals(6, code.length());
        assertTrue(policy.isValid(code));
        assertTrue(code.chars().anyMatch(Character::isLetter));
        assertTrue(code.chars().anyMatch(Character::isDigit));
    }

    @Test
    void derivedCodeIsDeterministicValidAndMixed() {
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);
        byte[] source = { 1, 2, 3, 4, 5, 6 };

        String first = policy.deriveCode(source);
        String second = policy.deriveCode(source);

        assertEquals(first, second);
        assertTrue(policy.isValid(first));
    }

    @Test
    void normalizationUppercasesAndValidationRejectsAmbiguousCharacters() {
        UnifiedCodePolicy policy = new UnifiedCodePolicy(random);

        assertEquals("A7K9P2", policy.normalize(" a7k9p2 "));
        assertTrue(policy.isValid("A7K9P2"));
        assertFalse(policy.isValid("A7O9P2"));
        assertFalse(policy.isValid("ABCDEF"));
        assertFalse(policy.isValid("234567"));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd UniActivity_BE && mvn -Dtest=UnifiedCodePolicyTest test
```

Expected: test compilation fails because `UnifiedCodePolicy` does not exist.

- [ ] **Step 3: Implement the minimal policy**

```java
@Component
public class UnifiedCodePolicy {
    public static final int CODE_LENGTH = 6;
    public static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final String DIGITS = "23456789";
    public static final String ALPHABET = LETTERS + DIGITS;

    private final RandomGenerator random;

    public UnifiedCodePolicy() {
        this(new SecureRandom());
    }

    UnifiedCodePolicy(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public String generateRandomCode() {
        char[] result = new char[CODE_LENGTH];
        result[0] = pick(LETTERS, random.nextInt(LETTERS.length()));
        result[1] = pick(DIGITS, random.nextInt(DIGITS.length()));
        for (int index = 2; index < CODE_LENGTH; index++) {
            result[index] = pick(ALPHABET, random.nextInt(ALPHABET.length()));
        }
        return new String(result);
    }

    public String deriveCode(byte[] source) {
        if (source == null || source.length < CODE_LENGTH) {
            throw new IllegalArgumentException("Code derivation requires at least 6 bytes");
        }
        char[] result = new char[CODE_LENGTH];
        result[0] = pick(LETTERS, Byte.toUnsignedInt(source[0]) % LETTERS.length());
        result[1] = pick(DIGITS, Byte.toUnsignedInt(source[1]) % DIGITS.length());
        for (int index = 2; index < CODE_LENGTH; index++) {
            result[index] = pick(ALPHABET, Byte.toUnsignedInt(source[index]) % ALPHABET.length());
        }
        return new String(result);
    }

    public String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isValid(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() != CODE_LENGTH) return false;
        boolean letter = false;
        boolean digit = false;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (ALPHABET.indexOf(character) < 0) return false;
            letter |= LETTERS.indexOf(character) >= 0;
            digit |= DIGITS.indexOf(character) >= 0;
        }
        return letter && digit;
    }

    private char pick(String alphabet, int index) {
        return alphabet.charAt(index);
    }
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run `cd UniActivity_BE && mvn -Dtest=UnifiedCodePolicyTest test`.

Expected: all `UnifiedCodePolicyTest` tests pass.

- [ ] **Step 5: Commit the policy task**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/UnifiedCodePolicy.java UniActivity_BE/src/test/java/com/example/uniactivity/service/UnifiedCodePolicyTest.java
git commit -m "feat: add unified six-character code policy"
```

---

### Task 2: Route All Class-Code Mutations Through StudentClassService

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentClassService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerMemberController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/StudentClass.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/ClassJoinRequestService.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentClassServiceTest.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/ClassJoinRequestServiceTest.java`
- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/controller/manager/ManagerMemberControllerTest.java`

**Interfaces:**
- Consumes: `UnifiedCodePolicy.generateRandomCode()`, `normalize()`, and `isValid()` from Task 1.
- Produces: `StudentClassResponseDto regenerateJoinCode(Long id)` as the only Admin/Manager class-code mutation path.

- [ ] **Step 1: Write failing service and controller tests**

Add service tests that stub `UnifiedCodePolicy.generateRandomCode()` to return a collision followed by `A7K9P2`, then verify `createClass` and `regenerateJoinCode` persist `A7K9P2`. Add a controller test with:

```java
when(managerScopeAuthorizationService.requireManagedClass(manager)).thenReturn(studentClass);
when(studentClassService.regenerateJoinCode(10L)).thenReturn(responseWith("A7K9P2"));

ResponseEntity<?> response = controller.regenerateJoinCode(userDetails);

verify(studentClassService).regenerateJoinCode(10L);
verifyNoInteractions(studentClassRepository);
assertEquals("A7K9P2", ((Map<?, ?>) response.getBody()).get("joinCode"));
```

Also add a `ClassJoinRequestService` test proving `" a7k9p2 "` is normalized to `A7K9P2` before repository lookup and malformed input is rejected.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd UniActivity_BE && mvn -Dtest='UnifiedCodePolicyTest,StudentClassServiceTest,ManagerMemberControllerTest,ClassJoinRequestServiceTest' test
```

Expected: failures because the services/controllers do not yet consume `UnifiedCodePolicy` and Manager still generates a UUID substring directly.

- [ ] **Step 3: Integrate the shared policy**

In `StudentClassService`, inject `UnifiedCodePolicy`, remove `UUID`, and replace `generateJoinCode` with bounded collision handling:

```java
private static final int MAX_JOIN_CODE_ATTEMPTS = 1_000;

private String generateJoinCode() {
    for (int attempt = 0; attempt < MAX_JOIN_CODE_ATTEMPTS; attempt++) {
        String candidate = codePolicy.generateRandomCode();
        if (!studentClassRepository.existsByJoinCode(candidate)) {
            return candidate;
        }
    }
    throw new IllegalStateException("Không thể tạo mã tham gia lớp duy nhất");
}
```

In `ManagerMemberController`, remove `StudentClassRepository`, inject `StudentClassService`, retain `requireManagedClass`, and delegate:

```java
StudentClass studentClass = managerScopeAuthorizationService.requireManagedClass(userDetails.getUser());
String newCode = studentClassService.regenerateJoinCode(studentClass.getId()).getJoinCode();
return ResponseEntity.ok(Map.of("message", "Đã tạo mã tham gia mới", "joinCode", newCode));
```

In `StudentClass`, enforce the runtime schema contract:

```java
@Column(nullable = false, unique = true, length = 6)
private String joinCode;
```

In `ClassJoinRequestService`, normalize and validate before lookup:

```java
String normalizedCode = codePolicy.normalize(joinCode);
if (!codePolicy.isValid(normalizedCode)) {
    throw new NotFoundException("Mã tham gia không hợp lệ");
}
StudentClass studentClass = studentClassRepository.findByJoinCode(normalizedCode)
        .orElseThrow(() -> new NotFoundException("Mã tham gia không hợp lệ"));
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the focused Maven command from Step 2.

Expected: all selected tests pass with Admin and Manager sharing the service path.

- [ ] **Step 5: Commit class-code integration**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentClassService.java UniActivity_BE/src/main/java/com/example/uniactivity/controller/manager/ManagerMemberController.java UniActivity_BE/src/main/java/com/example/uniactivity/entity/StudentClass.java UniActivity_BE/src/main/java/com/example/uniactivity/service/ClassJoinRequestService.java UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentClassServiceTest.java UniActivity_BE/src/test/java/com/example/uniactivity/controller/manager/ManagerMemberControllerTest.java UniActivity_BE/src/test/java/com/example/uniactivity/service/ClassJoinRequestServiceTest.java
git commit -m "feat: unify admin and manager class codes"
```

---

### Task 3: Rotate Existing Class Codes With Flyway V6

**Files:**
- Create: `UniActivity_BE/src/main/java/db/migration/V6__normalize_class_join_codes.java`
- Create: `UniActivity_BE/src/test/java/db/migration/V6__normalize_class_join_codesTest.java`

**Interfaces:**
- Consumes: the immutable migration alphabet snapshot `ABCDEFGHJKMNPQRSTUVWXYZ23456789`.
- Produces: every `classes.join_code` normalized, non-null, six characters, mixed letter/digit, and unique.

- [ ] **Step 1: Write the failing H2 migration test**

Create a MySQL-mode H2 `classes` table containing null, 8-character, and already-6-character legacy values. Run V6 with a mocked `SecureRandom`, then assert:

```java
assertEquals(3, countRows());
assertEquals(3, countDistinctJoinCodes());
assertTrue(allCodesMatch("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$"));
assertTrue(allCodesContainLettersAndDigits());
assertThrows(SQLException.class, () -> insertClassWithDuplicateJoinCode(existingCode()));
assertThrows(SQLException.class, () -> insertClassWithNullJoinCode());
```

The test must also prove every legacy value was rotated by comparing before/after maps by class ID.

- [ ] **Step 2: Run the migration test and verify RED**

Run:

```bash
cd UniActivity_BE && mvn -Dtest='db.migration.V6__normalize_class_join_codesTest' test
```

Expected: test compilation fails because V6 does not exist.

- [ ] **Step 3: Implement V6 normalization and constraints**

Implement a self-contained `BaseJavaMigration` with these fixed constants and sequence:

```java
private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
private static final String DIGITS = "23456789";
private static final String ALPHABET = LETTERS + DIGITS;
private static final int CODE_LENGTH = 6;
private static final int MAX_CODE_ATTEMPTS = 1_000;
private static final String UNIQUE_NAME = "uk_classes_join_code";
private static final String CHECK_NAME = "chk_classes_join_code_format";
```

`migrate(Context)` must execute in this order:

```java
Connection connection = context.getConnection();
Map<Long, String> replacements = generateReplacements(connection);
updateAllRows(connection, replacements);
verifyNormalizedData(connection);
makeColumnRequired(connection);
installUniqueConstraint(connection);
installFormatConstraint(connection);
verifyInstalledConstraints(connection);
```

`generateReplacements` selects `id FROM classes FOR UPDATE`, keeps an in-memory `Set<String>`, and generates a fresh mixed code for every row. `updateAllRows` uses one JDBC batch. Use database metadata to avoid installing a duplicate unique index. Use MySQL `MODIFY COLUMN`/`REGEXP` and H2 `ALTER COLUMN`/`REGEXP_LIKE` branches. Validate MySQL 8.0.16+ before adding an enforced check constraint. Fail with `SQLException` before DDL when any data invariant is false.

- [ ] **Step 4: Run V4–V6 migration tests and verify GREEN**

Run:

```bash
cd UniActivity_BE && mvn -Dtest='db.migration.V4__normalize_non_admin_account_codesTest,db.migration.V5__enforce_non_admin_account_codesTest,db.migration.V6__normalize_class_join_codesTest' test
```

Expected: all migration tests pass.

- [ ] **Step 5: Commit the migration**

```bash
git add UniActivity_BE/src/main/java/db/migration/V6__normalize_class_join_codes.java UniActivity_BE/src/test/java/db/migration/V6__normalize_class_join_codesTest.java
git commit -m "feat: migrate class join codes to six characters"
```

---

### Task 4: Convert Manual Check-in Codes to the Shared Policy

**Files:**
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/DynamicQrTokenService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentCheckinService.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/service/DynamicQrTokenServiceTest.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentCheckinServiceTest.java`

**Interfaces:**
- Consumes: `UnifiedCodePolicy.deriveCode`, `normalize`, and `isValid`.
- Preserves: `generateToken`, `validateToken`, 60-second interval, and previous-window tolerance.
- Changes: `generateCheckinCode` returns a valid six-character alphanumeric code.

- [ ] **Step 1: Add failing check-in tests**

Extend `DynamicQrTokenServiceTest`:

```java
String code = issuer.generateCheckinCode(101L, 202L);
assertTrue(policy.isValid(code));
assertTrue(issuer.validateCheckinCode(code.toLowerCase(Locale.ROOT), 101L, 202L));
assertFalse(issuer.validateCheckinCode(code, 999L, 202L));
assertFalse(issuer.validateCheckinCode(code, 101L, 999L));
assertFalse(issuer.validateCheckinCode("ABCDEF", 101L, 202L));
```

Extend `StudentCheckinServiceTest` so `A7K9P2` routes to `validateCheckinCode`, while the long QR token routes to `validateToken`.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd UniActivity_BE && mvn -Dtest='DynamicQrTokenServiceTest,StudentCheckinServiceTest' test
```

Expected: alphanumeric manual code tests fail because the current service accepts only `^\\d{6}$`.

- [ ] **Step 3: Derive and validate through UnifiedCodePolicy**

Inject `UnifiedCodePolicy` into both services. In `DynamicQrTokenService`, extract HMAC bytes and reuse them:

```java
private byte[] computeHmacBytes(String data) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
        throw new IllegalStateException("Failed to compute HMAC for QR token", exception);
    }
}

public String generateCheckinCode(Long activityId, Long classId) {
    return codePolicy.deriveCode(computeHmacBytes(
            "CODE:" + activityId + ":" + classId + ":" + getCurrentWindow()));
}

public boolean validateCheckinCode(String code, Long activityId, Long classId) {
    String normalized = codePolicy.normalize(code);
    if (!codePolicy.isValid(normalized)) return false;
    long currentWindow = getCurrentWindow();
    for (int offset = -TOLERANCE_WINDOWS; offset <= 0; offset++) {
        String expected = codePolicy.deriveCode(computeHmacBytes(
                "CODE:" + activityId + ":" + classId + ":" + (currentWindow + offset)));
        if (MessageDigest.isEqual(normalized.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) return true;
    }
    return false;
}
```

In `StudentCheckinService`, replace the numeric regex branch:

```java
String normalizedToken = codePolicy.normalize(token);
boolean isValidToken = codePolicy.isValid(normalizedToken)
        ? qrTokenService.validateCheckinCode(normalizedToken, activityId, targetClassId)
        : qrTokenService.validateToken(token, activityId, targetClassId);
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the focused Maven command from Step 2.

Expected: all selected tests pass, with QR token behavior unchanged.

- [ ] **Step 5: Commit check-in integration**

```bash
git add UniActivity_BE/src/main/java/com/example/uniactivity/service/DynamicQrTokenService.java UniActivity_BE/src/main/java/com/example/uniactivity/service/StudentCheckinService.java UniActivity_BE/src/test/java/com/example/uniactivity/service/DynamicQrTokenServiceTest.java UniActivity_BE/src/test/java/com/example/uniactivity/service/StudentCheckinServiceTest.java
git commit -m "feat: use alphanumeric manual checkin codes"
```

---

### Task 5: Align Student Inputs and Complete Verification

**Files:**
- Create: `UniActivity_FE/src/utils/userCode.js`
- Create: `UniActivity_FE/src/utils/userCode.test.js`
- Modify: `UniActivity_FE/src/pages/student/Dashboard.jsx`
- Modify: `UniActivity_FE/src/pages/student/Checkin.jsx`

**Interfaces:**
- Produces: `normalizeUserCode(value)` and `isCompleteUserCode(value)`.
- Consumes: the same alphabet and length as backend `UnifiedCodePolicy`.

- [ ] **Step 1: Write failing frontend normalizer tests**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { isCompleteUserCode, normalizeUserCode } from './userCode.js'

test('normalizes class and check-in codes to six approved characters', () => {
    assert.equal(normalizeUserCode(' a7-o1k9p2 '), 'A7K9P2')
    assert.equal(normalizeUserCode('A7K9P2ZZ'), 'A7K9P2')
    assert.equal(isCompleteUserCode('A7K9P2'), true)
    assert.equal(isCompleteUserCode('ABCDEF'), false)
    assert.equal(isCompleteUserCode('234567'), false)
})
```

- [ ] **Step 2: Run frontend test and verify RED**

Run:

```bash
cd UniActivity_FE && node --test src/utils/userCode.test.js
```

Expected: test fails because `userCode.js` does not exist.

- [ ] **Step 3: Implement frontend normalization and update inputs**

Create:

```javascript
const LETTERS = 'ABCDEFGHJKMNPQRSTUVWXYZ'
const DIGITS = '23456789'
const ALLOWED = new Set((LETTERS + DIGITS).split(''))

export function normalizeUserCode(value) {
    return String(value ?? '')
        .trim()
        .toUpperCase()
        .split('')
        .filter(character => ALLOWED.has(character))
        .join('')
        .slice(0, 6)
}

export function isCompleteUserCode(value) {
    const normalized = normalizeUserCode(value)
    return normalized.length === 6
        && [...normalized].some(character => LETTERS.includes(character))
        && [...normalized].some(character => DIGITS.includes(character))
}
```

In `Dashboard.jsx`, normalize typed and scanned join codes, set `maxLength={6}`, and disable submission until `isCompleteUserCode(joinCode)`.

In `Checkin.jsx`, replace numeric validation and input filtering with the shared frontend helpers:

```jsx
const cleanCode = normalizeUserCode(manualCode)
if (!isCompleteUserCode(cleanCode)) {
    setResult({ type: 'error', message: 'Vui lòng nhập đủ 6 ký tự chữ và số của mã check-in' })
    return
}

<input
    type="text"
    inputMode="text"
    autoCapitalize="characters"
    maxLength={6}
    value={manualCode}
    onChange={event => setManualCode(normalizeUserCode(event.target.value))}
    placeholder="A7K9P2"
/>
```

Replace all “6 số” and “6 chữ số” check-in labels with “6 ký tự”. Do not change password-reset OTP copy.

- [ ] **Step 4: Run all frontend tests, lint, and build**

Run:

```bash
cd UniActivity_FE && node --test src/utils/*.test.js src/pages/admin/*.test.js
cd UniActivity_FE && npm run lint
cd UniActivity_FE && npm run build
```

Expected: tests and build exit 0; lint has 0 errors. Existing unrelated hook warnings may remain.

- [ ] **Step 5: Run full backend verification**

Run:

```bash
cd UniActivity_BE && mvn clean test
```

Expected: Maven exits 0 with no failed or errored tests.

- [ ] **Step 6: Audit final diff and invariants**

Run:

```bash
git diff --check
rg -n "\\^\\\\d\\{6\\}\\$|6 chữ số|6 số|substring\\(0, 8\\)|substring\\(0, 6\\)" UniActivity_BE/src UniActivity_FE/src
git status --short
```

Expected: no obsolete class/check-in generator or numeric-only check-in copy remains; password-reset OTP may still use “6 chữ số”; unrelated pre-existing changes remain unstaged.

- [ ] **Step 7: Commit frontend and final integration**

```bash
git add UniActivity_FE/src/utils/userCode.js UniActivity_FE/src/utils/userCode.test.js UniActivity_FE/src/pages/student/Dashboard.jsx UniActivity_FE/src/pages/student/Checkin.jsx
git commit -m "feat: align six-character code inputs"
```
