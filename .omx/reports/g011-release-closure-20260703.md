# G011 Release Closure Report (2026-07-03)

## Goal

Close `G011-story-10-complete-release-hardening` with current, end-to-end evidence on the working head.

## Requirement-to-evidence map

| Requirement | Current proof |
|---|---|
| Metrics and health checks | Spring actuator and metrics configuration already present; current release drills target readiness endpoints before exercising the system |
| Runbooks and production guidance | `docs/runbook.md`, `docs/security.md`, `docs/automation.md`, `docs/development.md` |
| Backup/restore drill | `.omx/reports/g011-backup-restore-drill-20260703.md`, `pnpm drill:backup-restore` passed |
| Restart/recovery drill | `.omx/reports/g011-restart-recovery-drill-20260703.md`, `pnpm drill:restart-recovery` passed |
| Dependency / static / secret analysis | `.github/workflows/security.yml`, Story 14 security closure history, and fresh `pnpm audit --prod --audit-level moderate` pass |
| Security headers | Next security headers in `frontend/next.config.ts`; backend/browser security coverage in existing tests; current `pnpm quality` passed |
| CI workflows | `.github/workflows/ci.yml`, `.github/workflows/security.yml` |
| Production configuration documentation | operator documentation in `docs/runbook.md` and supporting docs |
| Backend checks | `pnpm quality` runs `backend\gradlew.bat clean check bootJar` successfully |
| Frontend lint/typecheck/test/build | `pnpm quality` passed |
| Worker lint/typecheck/test/build | `pnpm quality` passed |
| MySQL integration and recovery correctness | `mysqlCompatibilityTest` inside `pnpm quality` plus focused recovery regression coverage |
| Contract / API path verification | existing contract-bearing backend/frontend integration coverage plus full-stack publish flow pass |
| Playwright | browser-mode `pnpm quality` Playwright suite passed; production full-stack `pnpm e2e:fullstack` passed |
| Compose validation | `pnpm quality` includes `docker compose -f compose.yaml config --quiet` |
| Code review | `.omx/reports/g011-code-review-20260703.md` |
| Architecture invariant audit | `.omx/reports/g011-architecture-invariant-audit-20260703.md` |
| Adversarial UltraQA | `.omx/reports/g011-ultraqa-20260703.md` |

## Final status

All Story 10 release-hardening requirements now have fresh or explicitly linked current proof on 2026-07-03.

## Notes

- This closure does not expand Story 17 held-run override scope; those actions remain controlled by the approved change request.
- The new recovery endpoints and drill harnesses strengthen release operability without changing the core security model or persistence authority.
