# P0 Security and Data-Integrity Design

**Status:** Approved direction; awaiting written-spec review before implementation planning.

**Goal:** Remove the confirmed critical paths that allow forged authentication, cross-class access, unauthorised check-in, repeated score awards, partial registrations, stored XSS, OTP brute force, and unsafe evidence uploads while preserving local JWT login and Google OAuth.

## Scope

P0 covers eight independently testable areas:

1. JWT and Google OAuth hardening.
2. Object-level authorisation for student and manager APIs.
3. Mandatory QR and activity-window validation for check-in.
4. Idempotent evidence approval and rejection.
5. Atomic activity registration and cancellation.
6. Stored-XSS removal in notifications.
7. OTP generation, attempt limits, and atomic consumption.
8. Centralised safe image upload.

P0 does not redesign the complete scoring ledger, introduce historical rule versions, migrate every legacy table, add distributed messaging, or optimise all queries. Those belong to P1 and P2. P0 may introduce narrow schema changes required to enforce security or idempotency.

## Global Constraints

- Preserve Google OAuth and username/password login.
- Keep Spring Boot 3.5, Java 17, React 19, MySQL, and the existing role names.
- Preserve the user's uncommitted changes and do not commit automatically.
- Public registration always creates `STUDENT`; administrators are provisioned separately.
- APIs use bearer JWT authentication and do not fall back to an authenticated HTTP session.
- Every changed behaviour is implemented test-first.
- Backend tests use isolated configuration and must not connect to the database from `.env`.

## Authentication Architecture

### Local login

`POST /api/auth/login` authenticates username/password and returns an access token and refresh token. Access tokens carry `type=access`; refresh tokens carry `type=refresh`. Both carry the user's current `tokenVersion`. The JWT filter accepts only access tokens, reloads the user, requires `ACTIVE`, and requires the token version to match the database.

JWT signing has no source-code fallback. Startup fails when `JWT_SECRET` is absent or shorter than 32 bytes. Dynamic QR uses a separate required `QR_SECRET` so disclosure or rotation of one key does not compromise the other.

Logout, password change, password reset, and administrator account locking increment `tokenVersion`. This invalidates all previously issued access and refresh tokens for the account. Refresh also requires an active account and a matching token version.

### Google OAuth

Google OAuth remains enabled. The callback must not place access or refresh tokens in the redirect URL. After successful OAuth authentication, the backend creates a cryptographically random, single-use exchange code with a short expiry and redirects the browser with only that code. The frontend exchanges it once at `POST /api/auth/oauth2/exchange` and receives the normal JWT response.

The exchange code is stored hashed and is marked consumed atomically. Google account linking requires a verified email claim and retains the existing user's role and status.

### SSE

The general JWT filter no longer accepts `?token=`. An authenticated API issues a purpose-bound SSE ticket that expires after at most 60 seconds. Only `/sse/subscribe` accepts this ticket. The ticket cannot authorise ordinary APIs.

## Object-Level Authorisation

Role checks remain in Spring Security, but resource ownership is enforced in application services and scoped repository queries:

- A student can read only their own full score/profile data.
- A manager can read and mutate registrations, evidence, point requests, and members only when the resource belongs to the manager's class.
- Out-of-scope resources return `404` to avoid confirming their existence.
- Controllers never load an unrestricted entity by path ID and then mutate it directly.

The manager activity registration list is filtered by manager class. Approval, rejection, and manual check-in use queries scoped by both registration ID and class ID.

## Check-In Invariants

All student and manager-as-student check-in routes delegate to one `CheckinService`. A successful check-in requires:

- an authenticated active user;
- an existing registration in `REGISTERED` state;
- an activity in `OPEN` state;
- server time between activity start and end;
- a non-empty dynamic QR token valid for the activity and the registration's class;
- location inside the configured radius when GPS enforcement is configured.

There is no backward-compatible tokenless path. Repeated check-in returns a domain conflict without changing data. The service sets `status=ATTENDED`, `attendanceConfirmed=true`, and `confirmedAt` together in one transaction.

## Evidence Decision State Machine

Evidence decision logic moves from the controller to one transactional service. The state machine is:

`ATTENDED + evidence submitted + isApproved=null -> APPROVED or REJECTED`

Approval additionally verifies that the selected score option belongs to the registration's activity. A decision can be made only once. A repeated HTTP request returns conflict and never awards points again. Concurrent decisions use an entity version or conditional update so only one wins.

P0 keeps the existing score storage but guarantees one award call per registration decision. P1 will replace the aggregate detail model with an immutable per-source ledger and explicit reversal workflow.

## Atomic Registration

Retry coordination and transaction work are separated:

- An outer coordinator performs at most three retries for optimistic-lock conflicts.
- Each attempt calls a public method on a separate Spring bean with `REQUIRES_NEW` transaction semantics.
- Creating/reactivating a registration and incrementing its selected slot commit together.
- Cancelling a registration and decrementing the slot commit together.

Re-registration executes the same visibility, academic-year, class, activity status, deadline, and capacity checks as a new registration. It may reselect a slot if the student's class has changed.

## XSS and Notification Rendering

Notification title and message are treated as plain text. The toast renderer constructs fixed DOM elements and assigns untrusted values through `textContent`; it never interpolates them into `innerHTML`. User-controlled profile fields receive length constraints, while output encoding remains the primary defence. A basic Content Security Policy is added without breaking the current frontend assets.

## OTP Security

OTP values use `SecureRandom`. The database stores an OTP hash, not the plaintext value. A token records failed attempts and becomes unusable after five failures, expiry, or successful consumption. Verification and password reset use constant-time hash comparison where applicable.

Password reset consumes the OTP and changes the password in one transaction, then increments `tokenVersion`. Forgot-password responses do not reveal whether an email exists. Send limits and verification-attempt limits are enforced separately.

## Safe Image Upload

All banner and evidence uploads delegate to one service. It enforces:

- a fixed maximum number of files per request;
- a five-megabyte maximum per file and an aggregate request limit;
- an allowlist of decodable JPEG, PNG, and GIF images;
- verification by decoding image bytes rather than trusting client MIME or extension;
- server-generated full UUID filenames and server-selected extensions;
- a configured storage directory outside `src/main/resources`;
- cleanup of files when the owning database operation fails.

Files are served with `X-Content-Type-Options: nosniff` and a safe content disposition. SVG, HTML, and undecodable/polyglot files are rejected.

## Error Handling

Services throw typed domain exceptions for unauthenticated, forbidden/not-found, conflict, and validation outcomes. Controllers do not catch generic `Exception` and convert infrastructure failures into HTTP 400. The global exception handler produces one JSON error shape without returning internal exception messages.

## Testing Strategy

Tests are added before each production change:

- JWT startup configuration, token type, token version, locked users, and refresh rejection.
- Public registration never creates an administrator.
- OAuth exchange code is short-lived and single-use; tokens do not appear in callback URLs.
- Cross-class manager and arbitrary-user score access return 404/403 as designed.
- Missing, expired, wrong-class, early, late, and repeated QR check-in fail.
- Concurrent/repeated approval awards once.
- Slot-last-seat registration and cancellation remain consistent after optimistic conflicts.
- Toast renders hostile strings as text.
- OTP wrong-attempt limit, expiry, one-time consumption, and password-reset revocation.
- HTML/SVG/fake-image/oversized uploads are rejected.

Backend tests use an `application-test.properties` profile with no `.env` import and no production DDL update. Unit and MVC tests do not require a running server. Database-specific concurrency tests use an isolated MySQL/Testcontainers profile when available and skip explicitly when Docker is unavailable.

## Delivery and Progress Tracking

Implementation is delivered in small checkpoints. Each task is marked complete only after its focused test and the relevant regression suite pass. At the end of P0, the report lists completed, partially completed, blocked, and deferred items. P1 starts only after P0 verification or an explicit user decision to defer a documented blocker.
