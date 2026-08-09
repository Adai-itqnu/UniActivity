# P0-B URL Token and QR Secret Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not commit automatically.

**Goal:** Remove reusable bearer credentials from OAuth/SSE URLs and ensure dynamic QR tokens use a separate required secret.

**Architecture:** Google OAuth redirects with a random 256-bit one-time exchange code whose SHA-256 hash is stored under a pessimistic database lock and expires after 60 seconds. SSE uses a JWT-shaped `type=sse` purpose ticket valid for at most 60 seconds; only the SSE controller validates it, while the general JWT filter accepts header access tokens only. Dynamic QR HMAC uses its own fail-fast `QR_SECRET`.

**Tech Stack:** Java 17, Spring Boot 3.5.8, Spring Security 6, JJWT 0.12.6, JPA/MySQL, React 19, JUnit 5, Mockito.

## Global Constraints

- Preserve Google OAuth and username/password login.
- Keep Spring Boot 3.5, Java 17, React 19, MySQL, and the existing role names.
- Preserve the user's uncommitted changes and do not commit automatically.
- APIs use bearer JWT authentication and do not fall back to an authenticated HTTP session.
- Every changed behaviour is implemented test-first.
- OAuth exchange and SSE purpose tickets expire after at most 60 seconds.
- OAuth exchange codes are stored only as SHA-256 hashes and consumed under a database write lock.
- `JWT_SECRET` and `QR_SECRET` are separate required values of at least 32 UTF-8 bytes.

---

### Task 1: One-time Google OAuth exchange code

**Files:**

- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/entity/OAuthExchangeCode.java`
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/repository/OAuthExchangeCodeRepository.java`
- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/OAuthExchangeCodeService.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/OAuthExchangeCodeServiceTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/security/CustomAuthenticationSuccessHandlerTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/CustomAuthenticationSuccessHandler.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/auth/JwtAuthController.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/controller/auth/JwtAuthControllerTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/config/SecurityConfig.java`
- Modify: `UniActivity_FE/src/pages/AuthPage.jsx`
- Modify: `UniActivity_FE/src/utils/fetchInterceptor.js`
- Modify: `database_schema.sql`

**Interfaces:**

- `String OAuthExchangeCodeService.issue(User user)` returns the raw URL-safe code and stores only its SHA-256 hex digest.
- `Optional<User> OAuthExchangeCodeService.consume(String rawCode)` locks by digest, rejects missing/expired/consumed/inactive records, sets `consumedAt`, and returns the user once.
- `POST /api/auth/oauth2/exchange` consumes `{ "code": "..." }` and returns the same JWT/user response shape as local login.

- [ ] Write tests proving raw codes are not stored, expiry is exactly 60 seconds, consumption is single-use, and invalid/expired codes fail.
- [ ] Run `C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=OAuthExchangeCodeServiceTest test`; require RED because the service does not exist.
- [ ] Implement a 32-byte `SecureRandom` URL-safe code, SHA-256 hashing, `@Transactional` consumption, and repository `@Lock(PESSIMISTIC_WRITE)` lookup.
- [ ] Write a handler test proving the redirect contains only `?code=` and contains no `token`, `refreshToken`, or serialized user.
- [ ] Run the handler/controller tests and require RED against the current token-bearing redirect and missing exchange endpoint.
- [ ] Change the OAuth success handler to issue only the code; add the public exchange endpoint and token response; update React to exchange the code before storing JWTs.
- [ ] Add `email_verified=true` enforcement in the existing OAuth user processing test slice so unverified Google claims cannot link accounts.
- [ ] Run focused backend tests and `npm run build`; require PASS.

### Task 2: Purpose-bound SSE ticket

**Files:**

- Create: `UniActivity_BE/src/main/java/com/example/uniactivity/service/SseTicketService.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/SseTicketServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/JwtTokenProvider.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/security/JwtTokenProviderTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/security/JwtAuthenticationFilter.java`
- Modify: `UniActivity_BE/src/test/java/com/example/uniactivity/security/JwtAuthenticationFilterTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/controller/SseController.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/config/SecurityConfig.java`
- Modify: `UniActivity_FE/src/utils/useSse.js`

**Interfaces:**

- `String JwtTokenProvider.generateSseTicket(Long userId, long tokenVersion)` creates `type=sse` with expiry no more than 60 seconds.
- `OptionalLong SseTicketService.resolveUserId(String ticket)` validates signature/type/status/version and returns only the bound user ID.
- Authenticated `POST /sse/ticket` returns `{ "ticket": "...", "expiresIn": 60 }`.
- Public `GET /sse/subscribe?ticket=...` accepts only that purpose ticket.

- [ ] Extend provider/filter tests: SSE ticket cannot authenticate ordinary APIs and no query parameter is read by the JWT filter.
- [ ] Run focused tests and require RED because SSE ticket APIs do not exist and the filter still reads query JWT.
- [ ] Implement `type=sse`, ticket service, authenticated issue endpoint, manually validated subscribe endpoint, and exact SecurityConfig matchers.
- [ ] Change the React hook to fetch a ticket with bearer auth, URL-encode only the ticket, close on errors, and request a fresh ticket before reconnecting.
- [ ] Run backend tests and `npm run build`; require PASS.

### Task 3: Separate required QR secret

**Files:**

- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/service/DynamicQrTokenServiceTest.java`
- Modify: `UniActivity_BE/src/main/java/com/example/uniactivity/service/DynamicQrTokenService.java`
- Modify: `UniActivity_BE/src/main/resources/application.properties`
- Modify: `UniActivity_BE/.env.example`

**Interfaces:**

- `DynamicQrTokenService.init()` rejects missing, blank, or secrets shorter than 32 UTF-8 bytes.
- QR tokens use only `${QR_SECRET}` through `app.qr.secret`; JWT rotation cannot generate valid QR tokens.

- [ ] Add tests for missing/short secret rejection and deterministic validation with a valid independent secret.
- [ ] Run `C:\apache-maven-3.9.12\bin\mvn.cmd -B -Dtest=DynamicQrTokenServiceTest test`; require RED because current service uses the JWT fallback.
- [ ] Add fail-fast initialization, `app.qr.secret=${QR_SECRET}`, update `.env.example`, and use constant-time HMAC comparison.
- [ ] Re-run the focused test and require PASS.

### Task 4: Verification and progress

**Files:**

- Modify: `docs/security-improvement-progress.md`

- [ ] Run all P0-A/P0-B focused backend tests together and require zero failures.
- [ ] Run `C:\apache-maven-3.9.12\bin\mvn.cmd -B -DskipTests package` and require BUILD SUCCESS.
- [ ] Run `npm run build` in `UniActivity_FE` and require success.
- [ ] Run `git diff --check` and inspect the security-scoped diff.
- [ ] Mark OAuth URL, SSE URL, and QR secret entries complete only after their evidence passes; keep local `.env` secret setup explicitly pending.
