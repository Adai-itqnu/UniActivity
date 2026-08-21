# Final Fix Report: Unified Six-Character Codes

**Date:** 2026-08-21

**Branch:** `feature/unified-six-character-codes`

**Scope:** Final review fix wave for V6 migration safety, MySQL check parsing, bounded retries, validation messaging, Mockito strictness, and frontend code-input accessibility.

## Outcome

The release-blocking V6 design was replaced with a non-transactional, restartable migration protocol suitable for MySQL's implicit-commit DDL model:

- The original `classes.join_code` remains authoritative throughout preparation.
- Every preexisting class receives a newly generated code, including rows already using a valid six-character code.
- MySQL shadow population and cutover are protected by an explicit `LOCK TABLES ... WRITE` quiescence window, because Flyway runs this Java migration outside a transaction with autocommit semantics.
- A global, database-collation-aware comparison proves that no prepared code matches any current legacy code while the write lock is held.
- One InnoDB `ALTER TABLE` atomically renames the old column, promotes the shadow column as `VARCHAR(6) NOT NULL`, and installs the unique/format/rotation constraints.
- A durable InnoDB marker and physical schema shape distinguish pre-cutover, post-cutover, and complete states, so retry after Flyway repair does not rotate an already-cut-over set again.
- Foreign keys, primary-key use, incompatible indexes, prefix indexes, check definitions, check-name ownership, MySQL version, table engines, physical columns, and marker shape are rejected before class-data mutation.

The independent final code review found no remaining Critical defects. Its one remaining Important condition is a real-MySQL rehearsal before deployment; no live MySQL server or Docker runtime was available here.

## Root Cause and Restart Protocol

The former V6 updated authoritative codes and then issued multiple MySQL DDL statements. MySQL implicitly commits DDL, so a later compatibility, constraint, or DDL failure could leave Flyway failed while authoritative codes and schema were already partially changed. Merely moving `validateDatabaseSupport()` earlier could not make this sequence rollback-safe.

V6 now returns `false` from `canExecuteInTransaction()` and owns the following persistent state machine:

| State | Marker | `classes` shape | Authority | Retry action |
|---|---|---|---|---|
| PRE_CUTOVER | absent/`PREPARING` | `join_code`, optional `join_code_v6` | original `join_code` | discard/rebuild shadow, repopulate, cut over |
| POST_CUTOVER | `PREPARING` or `CUTOVER` | `join_code` + `join_code_v6_legacy` | promoted `join_code` | verify, write `CUTOVER`, remove legacy without rerotation |
| COMPLETE | `CUTOVER` | final `join_code` only | final `join_code` | verify only; never rerotate |
| H2_CUTOVER_IN_PROGRESS | marker exists | legacy + shadow, no `join_code` | H2 test-only intermediate | finish H2's multi-DDL emulation |

The H2-only state exists because H2 cannot execute the MySQL combined cutover statement. It tests classifier/recovery behavior; it is not evidence that MySQL accepts or atomically executes the production DDL.

### MySQL sequence

1. Read-only preflight validates MySQL 8.0.16+, `classes` as InnoDB, all managed columns/indexes/checks/names/dependencies, marker shape if present, and the ability to acquire the required table write lock.
2. Create the marker explicitly with `ENGINE=InnoDB` when absent and write `PREPARING`.
3. Disable autocommit for the lock window and acquire write locks for unaliased `classes`, the `prepared` and `legacy` self-join aliases, and the marker table.
4. Repeat preflight under the lock, then clean/recreate `join_code_v6`.
5. Generate a unique mixed six-character replacement for every row. Candidates are checked against all original codes using the database's active comparison semantics.
6. Populate and verify the shadow column, including a global `prepared.join_code_v6 = legacy.join_code` self-join under the explicit aliases.
7. Execute one atomic InnoDB `ALTER TABLE` cutover.
8. Commit, `UNLOCK TABLES`, and restore the original autocommit mode in `finally`; failures roll back staging DML and still unlock.
9. Persist `CUTOVER`, then atomically drop the temporary rotation check and legacy column.
10. Verify final data, `VARCHAR(6) NOT NULL`, uniqueness, enforced format check, absence of staging columns, and the durable marker.

An interruption before cutover leaves original codes authoritative. An interruption after atomic cutover is recognized from the legacy-column shape even if the `CUTOVER` marker write never occurred. An interruption after cleanup is recognized by `CUTOVER` plus the final schema.

## RED/GREEN Evidence

Focused tests were introduced before each behavioral correction. Commands below were run from `UniActivity_BE` unless noted.

| Behavior | RED evidence | GREEN evidence |
|---|---|---|
| Failure immediately before cutover must preserve original authority | `mvn -Dtest='db.migration.V6__normalize_class_join_codesTest#failureImmediatelyBeforeCutoverLeavesOriginalCodesAuthoritativeAndRerunCompletes' test` failed because the old map `{1=null, 2=ABCDEFGH, 3=ABC234}` had already become rotated codes | Same focused test passed after the shadow/atomic-cutover state machine was implemented |
| Atomic MySQL promotion must make the final column non-null | The SQL contract expected `CHANGE COLUMN join_code_v6 join_code VARCHAR(6) NOT NULL` and failed while the builder only renamed the nullable shadow | Contract passed after the atomic builder used `CHANGE COLUMN ... NOT NULL` |
| Raw MySQL `REGEXP` parsing must preserve the identifier underscore | `recognizesRawMysqlRegexpOperatorWithAndWithoutCharsetIntroducers` returned false for raw `join_code REGEXP '...'` | Passed after charset introducers were recognized lexically at the quote boundary |
| Malformed backend input must give six-character guidance | `ClassJoinRequestServiceTest` expected `Mã tham gia phải gồm đúng 6 ký tự chữ và số` but received `Mã tham gia không hợp lệ` | Focused service test passed after the message change |
| Student code inputs must expose accessible/code-entry attributes | `codeInputs.test.js` failed its source contract for missing input association/name and code-entry attributes | Passed after `id`/`htmlFor` or `aria-label`, meaningful `name`, `spellCheck={false}`, and `autoComplete="off"` were added |
| Existing FK dependencies must fail before any migration mutation | `rejectsJoinCodeForeignKeyDependencyBeforeAnyMigrationMutation` failed with “Expected SQLException ... but nothing was thrown” | Inbound and outbound FK tests passed after JDBC metadata preflight was added |
| Prepared codes must be disjoint from every legacy row, not only the same row | `preparedCodesMustNotMatchAnyLegacyRowUnderDatabaseComparisonSemantics` failed with “Expected SQLException ... but nothing was thrown” | Passed after the database-collation-aware global self-join check was added |
| MySQL preparation/cutover must have a migration-wide write lock | `mysqlPreparationAndCutoverRunInsideAnExplicitWriteLock` failed compilation because the lock guard did not exist | Success and failure-path ordering tests passed: autocommit off, aliased write locks, work, commit/rollback, unlock, autocommit restoration |
| Recovery marker must have a real primary key | `rejectsMarkerWithoutItsRequiredPrimaryKeyBeforeClassMutation` failed with “Expected SQLException ... but nothing was thrown” | Passed after exact marker columns/nullability/lengths/PK and engine validation were added |
| `join_code` cannot be part of the class primary key | `rejectsJoinCodePrimaryKeyBeforeAnyMigrationMutation` initially completed instead of rejecting | Passed after managed primary-key dependency preflight was added |

Additional coverage-only tests passed when added because the applicable production behavior was already correct:

- Exactly 1,000 runtime code-generation collisions throw without saving a class.
- A simulated crash after cutover but before the `CUTOVER` marker preserves promoted codes on retry and finishes marker/legacy cleanup.
- A cleanup failure after `CUTOVER` resumes without rerotation.
- A completed V6 invocation verifies without rerotating.
- MySQL marker DDL explicitly selects InnoDB; H2 retains portable DDL.
- Marker wrong-length schema, non-InnoDB class engine, conflicting checks, prefix indexes, raw rendered checks, and unsupported MySQL versions are rejected.

## Other Fixes

- Removed unnecessary `lenient()` calls from the requested Task 2 Mockito tests.
- Added exact 1,000-attempt exhaustion verification and `never().save(...)`.
- Changed malformed join-code text to actionable six-character letter-and-digit guidance while retaining the existing non-sensitive not-found text for a well-formed unknown code.
- Added accessible and browser-safe attributes to the Dashboard class-join and Checkin manual-code inputs.
- Added `codeInputs.test.js` to cover the frontend input contract.
- Preserved the existing long dynamic QR token flow; no dynamic-token production file was changed in this fix wave.

## Final Verification

All commands below were run after the final lock, marker, FK/PK, and recovery edits.

| Check | Result |
|---|---|
| `mvn -Dtest='db.migration.V4__normalize_non_admin_account_codesTest,db.migration.V5__enforce_non_admin_account_codesTest,db.migration.V6__normalize_class_join_codesTest,com.example.uniactivity.service.StudentClassServiceTest,com.example.uniactivity.service.ClassJoinRequestServiceTest,com.example.uniactivity.controller.manager.ManagerMemberControllerTest' test` | **43 passed**, 0 failures/errors |
| `mvn clean test` | **189 passed**, 0 failures/errors |
| `node --test src/utils/*.test.js src/pages/admin/*.test.js src/pages/student/*.test.js` | **7 passed**, 0 failed |
| `npm run lint` | Exit 0, **0 errors**, 4 preexisting hook-dependency warnings |
| `npm run build` | Exit 0, **804 modules transformed** |
| `git diff --check` | Passed |
| Requested Mockito audit | No `lenient()` remains in the three scoped tests |
| Obsolete numeric-code audit | Only password-reset/profile OTP copy still says “6 chữ số”; OTP is explicitly outside this feature's scope |
| User-file audit | No `.env` or `UniActivity_FE/package-lock.json` changes |
| Independent final diff review | No Critical findings; code approved conditional on refreshed verification (now complete) and real-MySQL deployment rehearsal |

The full backend suite was run outside the filesystem/network sandbox because `ApiTest` binds a local ephemeral Tomcat port. The earlier sandbox-only baseline produced `java.net.SocketException: Operation not permitted`; the unrestricted final run passed all 189 tests.

## MySQL Evidence Boundary and Deployment Gate

**No live MySQL migration was executed. Docker is unavailable in this environment.** H2 execution tests cover migration invariants and restart classification only. MySQL-specific coverage here consists of SQL construction contracts, JDBC metadata mocks, explicit lock/rollback/unlock ordering, version/engine checks, and review against primary documentation. It does not prove:

- parsing of the combined `ALTER TABLE` by a real MySQL server;
- actual alias-lock behavior and required grants;
- production `INFORMATION_SCHEMA` check-clause rendering;
- implicit-commit behavior under the deployed connector/server minor version;
- Flyway failed-history/repair behavior against MySQL.

Primary references used:

- [MySQL 8.0 Atomic DDL](https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html)
- [MySQL 8.0 LOCK TABLES and UNLOCK TABLES](https://dev.mysql.com/doc/refman/8.0/en/lock-tables.html)
- [MySQL 8.0 ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html)
- [MySQL statements that cause implicit commits](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)
- [Flyway migration transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html)

Before production deployment, rehearse on both MySQL 8.0.16 (the minimum) and the exact production minor version:

1. Restore a production-shaped backup into a disposable database, including legacy check/index variants.
2. Confirm the Flyway user has `LOCK TABLES` plus the existing migration DDL/DML privileges.
3. Run a clean V1–V6 migration and validate final data, constraints, column definitions, and marker state.
4. Inject a failure before cutover, verify old `join_code` values remain authoritative, repair Flyway history, and rerun.
5. Inject a failure after cutover but before marker/cleanup, verify retry preserves promoted codes, repair, and rerun.
6. Exercise concurrent class-code regeneration while V6 holds the write lock and confirm the application request blocks until unlock rather than changing authority mid-preparation.
7. Record backup/restore and `flyway repair` commands in the deployment runbook before authorizing release.

This rehearsal is a deployment gate. The current H2/mock suite must not be represented as proof of MySQL execution.
