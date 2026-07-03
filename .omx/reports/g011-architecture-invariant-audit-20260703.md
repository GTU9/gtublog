# G011 Architecture Invariant Audit (2026-07-03)

## Scope

Post-Story-16 head: `089aaea` on `prototype`, inspected from `codex/story/17-held-run-admin-actions`.

## Checked invariants and current evidence

- Spring remains the authority for authentication and authorization.
  - `backend/src/main/java/com/gtublog/auth/SecurityConfiguration.java` uses Spring Security configuration, `NimbusJwtEncoder`, `NimbusJwtDecoder`, `CorsConfigurationSource`, and role-based request matchers.
  - No custom bearer `OncePerRequestFilter` is used for auth. The only `OncePerRequestFilter` in main code is `backend/src/main/java/com/gtublog/automation/WorkerRequestSizeFilter.java`, which enforces worker request body limits.

- Browser credentials are not persisted in `localStorage` or `sessionStorage`.
  - Repo search found no `localStorage` or `sessionStorage` usage in `frontend/`.
  - `frontend/src/admin-auth.tsx` uses an in-memory access token and reads cookies only for CSRF coordination.

- MySQL schema authority remains Flyway with Hibernate validation.
  - `backend/src/main/resources/application.yml` configures MySQL, Flyway migration locations, and `ddl-auto: validate`.
  - `backend/src/main/java/com/gtublog/shared/persistence/JpaInitializationConfiguration.java` keeps JPA initialization behind Flyway.

- Automation diagnostics and replay remain Spring-owned.
  - `backend/src/main/java/com/gtublog/automation/AdminAutomationController.java` exposes diagnostics and outbox replay endpoints.
  - Frontend automation admin screens call those Spring endpoints through typed client code in `frontend/src/admin-api.ts`.

- Schedule authority remains domain-owned with visible Quartz synchronization state.
  - `backend/src/main/resources/db/migration/mysql/V9__automation_schedule_sync_state.sql` persists synchronization fields.
  - `backend/src/main/java/com/gtublog/automation/AutomationAdminService.java` and `AutomationScheduleSynchronizer.java` coordinate synchronization.
  - `frontend/src/automation-admin-ui.tsx` surfaces synchronization status and message to the administrator.

- Held-run state exists, but override semantics remain intentionally incomplete.
  - `backend/src/main/java/com/gtublog/automation/AutomationRunStatus.java` and related services support `HELD`.
  - No approved `retryOfRunId`, safe cancel semantics, or manual publish override flow is present in current contracts.
  - `.omx/plans/change-requests/story-17-held-run-admin-actions.md` is the controlling blocker for those transitions.

## Conclusion

The current head still respects the major architectural invariants for auth, persistence, Spring-owned automation control, and Quartz/domain boundaries.

The remaining blocker is not an invariant break in current code. It is the absence of approved semantics and closure evidence for:

- held-run retry, cancel, and manual publish transitions
- aggregate release-hardening closure artifacts
- executed backup/restore and restart drills on the current head
