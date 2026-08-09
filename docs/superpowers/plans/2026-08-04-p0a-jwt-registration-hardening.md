# P0-A JWT and Registration Hardening Implementation Plan

> **Execution mode:** implement each task in order with strict RED -> GREEN -> REFACTOR. Do not commit automatically; the workspace owner will decide when to commit.

**Goal:** Prevent privilege escalation through public registration, distinguish access/refresh JWTs, revoke all existing tokens when an account is invalidated, and stop locked accounts or stale tokens from authenticating.

**Architecture:** Add a monotonically increasing `tokenVersion` to each user and copy it into both JWT types. Every bearer authentication and refresh request reloads the user and requires `ACTIVE` status plus an exact token-version match. Public registration always creates `STUDENT`; privileged users remain an administrator-managed operation.

**Tech stack:** Java 17, Spring Boot 3.5.8, Spring Security 6, JJWT 0.12.6, JPA/MySQL, JUnit 5, Mockito.

---

## Task 1: Make JWT configuration and claims explicit

**Files:**

- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/security/JwtTokenProviderTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/JwtTokenProvider.java`
- Modify: `UniActivity_BE/src/main/resources/application.properties`
- Modify: `UniActivity_BE/.env.example`

**RED:** Add unit tests which construct `JwtTokenProvider` with `ReflectionTestUtils` and prove that:

1. `init()` rejects a missing, blank, or UTF-8 secret shorter than 32 bytes.
2. An access token carries `type=access` and `tokenVersion`.
3. A refresh token carries `type=refresh` and `tokenVersion`.
4. Access and refresh classification are mutually exclusive.

Run:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=JwtTokenProviderTest test
```

Expected RED: compilation fails because token-version-aware methods do not exist.

**GREEN:**

- Remove the default secret from `@Value` and from `application.properties`; startup must require `${JWT_SECRET}`.
- Validate that the configured secret is nonblank and at least 32 UTF-8 bytes before calling JJWT.
- Change token methods to:

```java
String generateAccessToken(Long userId, String username, String role, long tokenVersion)
String generateRefreshToken(Long userId, long tokenVersion)
String getTokenType(String token)
long getTokenVersion(String token)
boolean isAccessToken(String token)
boolean isRefreshToken(String token)
```

- Put `type` and `tokenVersion` in both token types.
- Document `JWT_SECRET` in `.env.example` as a developer-supplied value of at least 32 random bytes; never add a working secret to source control.
- Re-run the focused test and require PASS.

## Task 2: Remove public-registration privilege escalation

**Files:**

- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/service/UserServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/UserService.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/User.java`
- Modify: `database_schema.sql`

**RED:** Add a mocked repository test where `userRepository.count()` would be zero and assert that public registration still saves `Role.STUDENT`. Also assert the new account starts with `tokenVersion=0`.

Run:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=UserServiceTest test
```

Expected RED: the current first account is saved as `ADMIN` and `tokenVersion` does not exist.

**GREEN:**

- Add non-null `token_version BIGINT NOT NULL DEFAULT 0` in the entity and schema.
- Always assign `Role.STUDENT` in `registerUser`; remove the repository-count privilege branch.
- Re-run the focused test and require PASS.

## Task 3: Reject locked users, wrong token types, and revoked JWTs

**Files:**

- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/security/JwtAuthenticationFilterTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/JwtAuthenticationFilter.java`

**RED:** With Mockito-backed request/filter tests, prove that the filter does not authenticate when:

1. a refresh token is supplied as bearer authorization;
2. the stored user is `LOCKED`;
3. JWT `tokenVersion` differs from the stored version.

Also prove a valid access token for an active, version-matching user sets the security context. Clear `SecurityContextHolder` after every test.

Run:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=JwtAuthenticationFilterTest test
```

Expected RED: the current filter authenticates locked and version-mismatched users.

**GREEN:**

- Require `isAccessToken(jwt)`.
- Require `user.status == ACTIVE` and `user.tokenVersion == jwt.tokenVersion` before creating authentication.
- Stop accepting a `token` query parameter globally. As a temporary compatibility boundary until the dedicated SSE-ticket P0 task, accept it only when the servlet path is exactly `/sse/subscribe`.
- Re-run the focused test and require PASS.

## Task 4: Harden login, refresh, OAuth token generation, and logout

**Files:**

- Create: `UniActivity_BE/src/test/java/com/example/uniactivity/controller/auth/JwtAuthControllerTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/auth/JwtAuthController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/CustomAuthenticationSuccessHandler.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/config/SecurityConfig.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/UserRepository.java`

**RED:** Add controller unit tests proving:

1. refresh rejects a locked account;
2. refresh rejects a token-version mismatch;
3. refresh creates an access token using the current token version;
4. authenticated logout atomically increments the stored token version.

Run:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=JwtAuthControllerTest test
```

Expected RED: refresh ignores status/version and logout is a no-op.

**GREEN:**

- Generate local-login and OAuth tokens with the current user token version.
- Require refresh type, active account, and matching version.
- Make `/api/auth/logout-jwt` authenticated while login/register/refresh remain public.
- Implement logout as an atomic repository update (`token_version = token_version + 1`) for the authenticated user, so all earlier access and refresh tokens are invalid immediately.
- Keep OAuth behavior otherwise unchanged in this task; removing tokens from OAuth redirect URLs is tracked as the next P0 package.
- Re-run the focused test and require PASS.

## Task 5: Verify the P0-A slice and record progress

**Files:**

- Create or modify: `docs/security-improvement-progress.md`

Run focused tests together:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=JwtTokenProviderTest,UserServiceTest,JwtAuthenticationFilterTest,JwtAuthControllerTest test
```

Then compile the complete backend without executing the pre-existing external-server Karate suite:

```powershell
C:\apache-maven-3.9.12\bin\mvn.cmd -B -DskipTests package
```

Inspect `git diff --check` and the scoped diff. Mark completed checklist entries only when their focused tests pass. Record remaining P0 items explicitly: OAuth one-time exchange code, SSE purpose ticket, object-level authorization, QR/time enforcement, idempotent evidence decisions, atomic registration/cancellation, XSS, OTP, and safe uploads.
