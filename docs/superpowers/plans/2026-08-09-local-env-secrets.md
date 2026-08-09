# Local Environment Secrets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent cryptographically random JWT and dynamic-QR secrets to the ignored local backend environment file and verify that the application accepts them without exposing their values.

**Architecture:** Keep database, OAuth, and mail credentials untouched. Generate two independent 32-byte values from the operating system CSPRNG, encode each as 64 hexadecimal characters, append them only to `UniActivity_BE/.env`, and validate properties rather than printing values.

**Tech Stack:** Spring Boot 3.5, Java 17, Maven Wrapper, POSIX shell, OpenSSL.

## Global Constraints

- Never print, commit, or copy the generated secret values into documentation.
- Preserve all existing `DB_*`, `GOOGLE_*`, and `MAIL_*` entries byte-for-byte.
- `JWT_SECRET` and `QR_SECRET` must each contain at least 32 UTF-8 bytes and must differ.
- `UniActivity_BE/.env` must remain ignored and untracked by Git.
- This configuration is for local development; production must inject secrets through its deployment secret manager.

---

### Task 1: Validate the pre-change state

**Files:**
- Inspect: `UniActivity_BE/.env`
- Inspect: `UniActivity_BE/.gitignore`

**Interfaces:**
- Consumes: Spring property names `JWT_SECRET` and `QR_SECRET`.
- Produces: A confirmed failing precondition proving the local secret configuration is absent or invalid.

- [ ] **Step 1: Run a non-disclosing failing validation**

```bash
bash -c 'set -a; source UniActivity_BE/.env; set +a; test -n "${JWT_SECRET:-}" && test -n "${QR_SECRET:-}" && test "${#JWT_SECRET}" -ge 32 && test "${#QR_SECRET}" -ge 32 && test "$JWT_SECRET" != "$QR_SECRET"'
```

Expected: non-zero exit because the two local variables are currently missing.

- [ ] **Step 2: Confirm the target file is ignored and untracked**

```bash
git check-ignore -q UniActivity_BE/.env
git ls-files --error-unmatch UniActivity_BE/.env
```

Expected: `git check-ignore` exits 0; `git ls-files` exits non-zero.

### Task 2: Generate and install local secrets

**Files:**
- Modify: `UniActivity_BE/.env`

**Interfaces:**
- Consumes: 32 random bytes per secret from `openssl rand`.
- Produces: `JWT_SECRET=<64 lowercase hexadecimal characters>` and `QR_SECRET=<64 lowercase hexadecimal characters>`.

- [ ] **Step 1: Generate two independent secret values without echoing them to user-facing output**

```bash
openssl rand -hex 32
openssl rand -hex 32
```

Expected: each command exits 0 and produces a distinct 64-character hexadecimal value used only in the subsequent patch.

- [ ] **Step 2: Append the two assignments with `apply_patch`**

Append exactly one `JWT_SECRET=` assignment and one `QR_SECRET=` assignment to `UniActivity_BE/.env`, using the generated values. Do not modify any existing assignment.

- [ ] **Step 3: Run the non-disclosing validation again**

```bash
bash -c 'set -a; source UniActivity_BE/.env; set +a; test -n "${JWT_SECRET:-}" && test -n "${QR_SECRET:-}" && test "${#JWT_SECRET}" -ge 32 && test "${#QR_SECRET}" -ge 32 && test "$JWT_SECRET" != "$QR_SECRET"'
```

Expected: exit 0 with no output.

### Task 3: Verify application integration and secret containment

**Files:**
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/config/SecretSeparationValidatorTest.java`
- Test: `UniActivity_BE/src/test/java/com/example/uniactivity/security/JwtTokenProviderTest.java`
- Inspect: tracked files in the Git index.

**Interfaces:**
- Consumes: the two local environment values loaded through `spring.config.import=optional:file:.env[.properties]`.
- Produces: test evidence that JWT and QR validation accepts distinct strong secrets and that no generated value is tracked.

- [ ] **Step 1: Run focused secret and token tests**

```bash
cd UniActivity_BE && bash ./mvnw -Dtest=SecretSeparationValidatorTest,JwtTokenProviderTest test
```

Expected: Maven exits 0 and both test classes pass.

- [ ] **Step 2: Run the complete backend test suite**

```bash
cd UniActivity_BE && bash ./mvnw test
```

Expected: Maven exits 0 with zero test failures. If an external dependency or local database blocks the suite, record the exact blocker without weakening the focused verification.

- [ ] **Step 3: Confirm secrets remain outside Git**

```bash
git check-ignore -q UniActivity_BE/.env
git status --short -- UniActivity_BE/.env
git diff --cached -- UniActivity_BE/.env
```

Expected: ignore check exits 0; status and cached diff print nothing.

- [ ] **Step 4: Commit only the implementation plan**

```bash
git add docs/superpowers/plans/2026-08-09-local-env-secrets.md
git commit -m "docs: plan local environment secret setup"
```

Expected: commit contains the plan file only; `.env` remains local and untracked.
