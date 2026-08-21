# Unified Six-Character Codes Design

**Date:** 2026-08-21

**Status:** Approved by user on 2026-08-21

**Scope:** Class join codes and manual activity check-in codes

## Goal

Standardize user-facing class join codes and manual activity check-in codes as six-character uppercase alphanumeric codes while preserving their different security lifecycles:

- A class join code remains stable until an Admin or the class Manager regenerates it.
- A manual check-in code remains bound to an activity, a class, and a 60-second time window.
- The long HMAC token embedded in the dynamic QR remains unchanged.

All existing class join codes will be rotated during migration, so previously shared class codes will stop working after deployment.

## Code Policy

Introduce one Spring component, `UnifiedCodePolicy`, as the runtime source of truth.

- Length: exactly 6 characters.
- Character set: `ABCDEFGHJKMNPQRSTUVWXYZ23456789`.
- Ambiguous characters `I`, `L`, `O`, `0`, and `1` are excluded.
- Generated codes contain at least one letter and at least one digit.
- Input is normalized with `trim()` and uppercase before validation.

The component exposes three responsibilities:

1. Generate a cryptographically random class code with `SecureRandom`.
2. Derive a deterministic six-character code from HMAC bytes for check-in.
3. Normalize and validate user-supplied codes.

The class-code generator and the HMAC derivation use the same length, alphabet, and mixed letter/digit invariant. They do not produce the same value because the two codes serve different purposes.

## Class Join Code Flow

`StudentClassService` owns all class-code mutations.

- Creating a class calls the shared policy to generate a code.
- Admin regeneration calls `StudentClassService.regenerateJoinCode(classId)`.
- Manager regeneration authorizes the managed class, then calls the same service method instead of generating a UUID substring in the controller.
- The service checks repository availability before saving.
- A database unique constraint on `classes.join_code` is the final collision guard.

The API response remains unchanged and continues to return `joinCode`.

## Existing-Data Migration

Add a versioned Java Flyway migration after V5.

The migration will:

1. Lock all rows in `classes` for the duration of normalization.
2. Generate a new unique six-character code for every class, including classes whose current code is already six characters.
3. Update all class rows in one migration transaction.
4. Change `join_code` to `VARCHAR(6) NOT NULL`.
5. Add a named unique constraint for `join_code` if it does not already exist.
6. Add or verify a format constraint requiring the approved alphabet, six characters, at least one letter, and at least one digit.
7. Re-read the table and abort migration if any invariant is violated.

Pending class-join requests are unaffected because they reference the class entity rather than storing the submitted code.

The migration keeps its own immutable snapshot of the code alphabet. It will not depend on a mutable application class, because applied Flyway migrations must remain reproducible in future releases.

## Manual Check-in Flow

`DynamicQrTokenService` continues computing HMAC-SHA256 from `activityId`, `classId`, and the current time window.

- `generateCheckinCode` passes the HMAC bytes to `UnifiedCodePolicy.deriveCode`.
- `validateCheckinCode` normalizes the supplied code, validates its format, and compares it against the current and previous accepted time windows.
- `StudentCheckinService` uses the shared policy to distinguish a six-character manual code from the longer QR token.
- Activity, class, registration, time-window, and GPS checks remain unchanged.

The manual code continues rotating every 60 seconds. It is never replaced with the persistent class join code.

## Frontend Changes

Update every user-facing input and label that assumes numeric-only check-in codes.

- Check-in input accepts six alphanumeric characters, automatically uppercases input, removes unsupported characters, and no longer uses a numeric-only keyboard.
- Labels change from “6 chữ số” or “6 số” to “6 ký tự”.
- Class-join input follows the same normalization and six-character limit.
- Admin and Manager displays remain compatible because they already render the returned string by character.
- Existing class QR generation continues encoding the class join code returned by the API.

## Error Handling

- Missing or malformed input returns the existing validation error type with a six-character guidance message.
- Random class-code generation has a bounded retry count and fails explicitly if the code space cannot provide an unused value.
- Database uniqueness prevents concurrent Admin/Manager requests from persisting a duplicate.
- Migration failure rolls back rather than leaving a partially rotated set of class codes.
- Invalid or expired check-in codes continue returning the same non-sensitive error message.

## Testing and Verification

Backend tests will cover:

- Random class codes are six characters, use only the approved alphabet, and contain a letter and digit.
- Collision retry and bounded failure behavior.
- Admin and Manager regeneration both delegate to `StudentClassService`.
- Check-in code generation and validation for the correct activity/class and rejection for the wrong activity/class.
- Manual-code detection no longer depends on `^\\d{6}$`.
- Migration rotates every existing class, produces unique codes, and verifies constraints.
- Existing student check-in service tests remain green.

Frontend tests will cover:

- Input normalization to uppercase approved characters.
- Six-character validation and updated messages.
- Existing Admin class QR regression tests remain green.

Final verification commands:

```bash
cd UniActivity_BE && mvn clean test
cd UniActivity_FE && node --test src/utils/*.test.js src/pages/admin/*.test.js
cd UniActivity_FE && npm run lint
cd UniActivity_FE && npm run build
```

## Non-goals

- The eight-digit user account-code policy is not changed.
- Dynamic QR HMAC tokens are not shortened to six characters.
- Check-in expiration, tolerance windows, activity authorization, and GPS enforcement are not relaxed.
