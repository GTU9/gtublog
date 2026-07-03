# G011 Release Gap Audit (2026-07-03)

## Goal under audit

`G011-story-10-complete-release-hardening`

Story 10 requires:

- metrics, health checks, runbooks, backup/restore and restart drills
- dependency, secret, and static analysis
- security headers and CI workflows
- production configuration documentation
- passing backend checks, frontend and worker lint/typecheck/test/build
- MySQL integration, contract tests, Playwright, compose validation
- code review, architecture invariant audit, and adversarial UltraQA

## Fresh evidence confirmed in the current repository

- Metrics and health endpoints are configured in `backend/src/main/resources/application.properties`.
- Prometheus and security-header coverage are asserted in `backend/src/test/java/com/gtublog/auth/AuthIntegrationTests.java`.
- Release runbook and security guidance exist in `docs/runbook.md`, `docs/security.md`, `docs/automation.md`, and `docs/development.md`.
- CI and security workflows exist in `.github/workflows/ci.yml` and `.github/workflows/security.yml`.
- Fresh command evidence on 2026-07-03:
  - `pnpm quality` passed after Docker Desktop Linux engine was started and Testcontainers became available.
  - `pnpm e2e:fullstack` passed with `e2e/publishing.fullstack.spec.ts`.
  - `pnpm audit --prod --audit-level moderate` reported no known vulnerabilities.
  - `docker compose -f compose.yaml config --quiet` passed.
  - `pnpm drill:backup-restore` passed and is captured in `.omx/reports/g011-backup-restore-drill-20260703.md`.
- Story 14 security closure is merged on `prototype` via issue `#32`, PR `#33`, merge commit `f24a928`, with adversarial evidence in `.omx/reports/ultraqa-story-14.md`.
- Story 15 real-service full-stack E2E foundation is merged on `prototype` via issue `#34`, PR `#35`, merge commit `d90b21f`, with adversarial evidence in `.omx/reports/ultraqa-story-15.md`.
- Story 16 automation configuration administration is merged on `prototype` via issue `#36`, PR `#37`, merge commit `089aaea`, with verified local quality gates recorded in branch history and plan artifacts.

## Still not proven enough to close G011

- No current audit artifact proves a fresh restart drill was executed end to end on the present `prototype` head after the Story 16 automation-admin additions.
- `pnpm quality` initially failed in this continuation because Docker Desktop was not running; the underlying code passed once the Testcontainers environment was restored, so that first failure is environmental evidence rather than a product regression.
- Story 16 completed safe configuration administration, but held-run retry, cancel, and manual publish remain intentionally deferred by `.omx/plans/change-requests/story-17-held-run-admin-actions.md`; those semantics must not be invented inside G011 without an approved plan change.
- The repository has UltraQA reports for Stories 14 and 15, but not yet a final aggregate G011 closure report that ties all required release-hardening evidence together.
- The repository has no dedicated architecture-invariant audit artifact for the post-Story-16 head.
- Fresh command evidence still does not cover every item in the `docs/runbook.md` release checklist because the aggregate closure report and restart drill remain outstanding.

## Safe next actions

1. Re-run the executable release gates on the current head and capture fresh evidence.
2. Add an explicit G011 closure report that maps each requirement to current proof.
3. Implement only safe release-hardening gaps that do not require held-run override semantics.
4. Keep Story 17 held-run actions blocked behind the approved change-request path.
