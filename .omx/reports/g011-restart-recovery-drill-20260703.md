# G011 Restart/Recovery Drill Evidence (2026-07-03)

## Commands

- `pnpm drill:restart-recovery`
- `backend\gradlew.bat mysqlCompatibilityTest --no-configuration-cache --tests com.gtublog.automation.AutomationPipelineIntegrationTests`

## What the drill executed

1. Built a fresh Spring Boot executable jar and started the backend against disposable MySQL.
2. Created an automation topic with publication enabled and two distinct source origins (`localhost`, `127.0.0.1`).
3. Triggered a real automation run, claimed a generation job with the fake worker provider contract, and submitted a terminal payload with canonical digest generation.
4. Verified that automatic publication produced a public post and a pending outbox record.
5. Restarted the backend and confirmed the outbox record still existed and could be replayed after restart.
6. Created a second run, forced lease expiry using `UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE`, and exercised recovery against database time rather than app-server clock drift.
7. Verified recovered state, generation-job cancellation, and recovery audit evidence.

## Result

Pass.

Fresh evidence on 2026-07-03:

- `pnpm drill:restart-recovery` passed end to end on the current working head.
- `AutomationPipelineIntegrationTests.administratorCanForceRecoveryOfAnExpiredRunUsingDatabaseClock` passed in MySQL-backed integration testing.

## Code changes that made the drill reliable

- Added administrator recovery endpoints:
  - `POST /api/v1/admin/automation/recovery/process`
  - `POST /api/v1/admin/automation/runs/{runId}/recovery`
- Switched recovery-candidate time evaluation to `SELECT UTC_TIMESTAMP(6)` from MySQL so restart recovery uses the authoritative database clock.
- Made the full-stack backend launcher preserve caller-provided environment overrides instead of overwriting them.
- Made Quartz recovery trigger registration self-heal on restart by replacing stale trigger/job registrations before scheduling the fresh recovery trigger.

## Operational significance

- Confirms restart recovery is not only unit-tested but executable from the release operator path.
- Confirms recovery semantics remain safe after the Story 16 automation-configuration administration additions.
- Confirms run recovery no longer depends on wall-clock agreement between the Java process and MySQL container.
