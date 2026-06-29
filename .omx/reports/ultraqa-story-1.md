# UltraQA Report — Story 1 Phase 0 scaffold

## Goal and success criteria

- Goal: prove the multi-runtime scaffold, pinned toolchains, provider boundary, and local infrastructure baseline behave correctly under normal and hostile inputs.
- Stop condition: backend/Node/build gates pass, MySQL 8.4/Flyway executes without skip, the provider decision is documented consistently with the approved Story 8 deferred production-freeze gate, and no generated debris remains.
- Safety bounds: no secret values read or printed, no production writes, no persistent-data deletion, and all commands bounded to 120 seconds.

## Scenario matrix

| ID | User/attacker model | Scenario | Command/harness | Expected signal | Actual result | Status | Evidence | Cleanup |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | Developer | Clean unit/package baseline | `backend\gradlew.bat clean test bootJar` | exit 0 | exit 0 | PASS | Java 25 build and context test passed | No temporary artifacts |
| S2 | Release gate | Pinned MySQL and Flyway probe | `backend\gradlew.bat mysqlCompatibilityTest --rerun-tasks` | container starts, MySQL 8.4 and Flyway assertions pass | tests=1, skipped=0, failures=0 | PASS | MySQL 8.4 detected and Flyway migration v1 applied | Testcontainers cleaned containers automatically |
| S3 | Hostile source text | Unicode, NUL and prompt-injection-like text crosses only the provider boundary | worker Vitest | exact request reaches fake provider once | 3/3 tests passed | PASS | `provider.test.ts` hostile-input test | Test intentionally tracked |
| S4 | Failed provider | Provider rejects while output text could look successful | worker Vitest | rejection propagates | rejection identity preserved | PASS | misleading-success regression test | Test intentionally tracked |
| S5 | Misconfigured operator | Invalid Compose port | `MYSQL_PORT=not-a-port docker-compose config --quiet` | non-zero and explicit parse error | exit 1, `invalid hostPort` | PASS | static Compose validation | Environment restored |
| S6 | Flaky runner | Repeat worker suite | worker Vitest twice | identical green runs | 3/3 twice | PASS | two consecutive runs | No debris |
| S7 | Dirty worktree | QA must preserve existing Story changes | status snapshot before/after QA | snapshots identical | identical | PASS | `DIRTY_WORKTREE_PRESERVED=PASS` | No rollback needed |
| S8 | Secret seeker | Scan tracked source for committed private keys/non-empty Codex credentials | bounded `git grep` patterns | no match | no match | PASS | `SECRET_SCAN=PASS` | No secret content printed |
| S9 | Hung provider | Codex SDK run has a hard abort boundary | `pnpm --dir generation-worker spike:codex` | structured response or abort within 90 seconds | initial run passed in 17.1 seconds; later independent rerun hit account usage limit promptly | PARTIAL | spike script uses `AbortController` and 90-second timer | No child process remained |
| S10 | Workflow interruption/stale state | Nested UltraQA state while Autopilot is active | `omx state write ...` | no incompatible state overlap | OMX rejected overlap and preserved Autopilot state | PASS | explicit runtime rejection | temporary input file removed |

## Commands run

- `[0] pnpm install --frozen-lockfile` — lockfile reproducibility.
- `[0] backend\gradlew.bat clean test bootJar` — non-container Java 25 baseline.
- `[0] backend\gradlew.bat mysqlCompatibilityTest --rerun-tasks` — pinned MySQL 8.4.10 started and Flyway migration passed.
- `[0] pnpm quality` — full backend, Node, Playwright, build, and Compose configuration gate passed in 77 seconds.
- `[0] MYSQL_PORT=3307 docker compose up -d mysql` — Compose service reached `healthy`; container/network were removed afterward.
- `[0] pnpm --dir generation-worker test` twice — three tests passed on both runs.
- `[0] worker lint/typecheck/build` — static and package gates passed.
- `[1] malformed Compose configuration` — correctly rejected invalid host port.
- `[0] Codex SDK initial feasibility spike` — structured output passed in 17.1 seconds; a later independent rerun was quota-blocked.

## Failures found

- S9: the current local Codex login is interactive and quota-bound; it does not prove externally supplied unattended authentication or restartable deployment.
- Root quality previously allowed a skipped Testcontainers test. The Docker test is now tagged, run by a dedicated task, and attached to `check`, so missing Docker is a hard failure.

## Fixes applied

- Added a pinned MySQL 8.4.10 Testcontainers/Flyway migration probe and dedicated mandatory Gradle task.
- Added hostile prompt and provider-failure regression tests.
- Added local-only Prometheus endpoint exposure and aligned provider/quality documentation.
- Added a single root quality runner covering backend, Node workspaces, Playwright, and Compose configuration.

## Cleanup and rollback

- UltraQA temporary state-input file was removed.
- No container or background service was started.
- Existing dirty Story work was preserved; generated build directories remain ignored.

## Residual risks

- Story 8 still needs an external unattended Codex credential and restartable worker deployment probe before Codex SDK can be frozen for production use.
- Prometheus/Grafana cross-service scrape remains Story 1 residual evidence; their Compose configuration and local-only endpoint exposure are present, but the backend was not kept running for a scrape probe.

## Verdict

`ULTRAQA PASS WITH DEFERRED PROVIDER FREEZE`. Code-level hostile scenarios, Docker/MySQL/Flyway gates, and the approved CR-001 documentation updates all pass. Story 1 may proceed with provider-neutral scaffolding while Story 8 retains the remaining production-adapter gate.
