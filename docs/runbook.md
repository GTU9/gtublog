# Runbook

## Daily checks

1. Check `/actuator/health` and `/actuator/prometheus`.
2. Open `/admin/automation` and review:
   - held runs
   - failed runs
   - pending outbox events
   - recent hold reasons
3. Confirm the latest published post appears in public pages, RSS, and sitemap after automation succeeds.

## Backup and restore drill

Recommended local verification steps:

1. Export MySQL schema and data with a timestamped dump.
2. Restore into a fresh MySQL 8.4 instance.
3. Run Flyway validation and application startup with `ddl-auto=validate`.
4. Confirm administrator login, public post reads, and automation diagnostics after restore.

## Restart drill

1. Trigger a manual automation run.
2. Restart the backend during or after outbox creation.
3. Verify the run remains recoverable and pending outbox events can be replayed safely.
4. Restart the generation worker and confirm the next compatible job can still be claimed.
5. Force a test run lease into the past, execute the recovery sweep, and confirm the run becomes `FAILED`, its job becomes `CANCELLED`, and exactly one `AUTOMATION_RUN_RECOVERED_AS_FAILED` audit event exists.

## Migration preflight

Before applying `V6__automation_run_recovery.sql`, verify that historical generation jobs contain at most one row per run:

```sql
SELECT run_id, COUNT(*) AS job_count
FROM generation_job
GROUP BY run_id
HAVING COUNT(*) > 1;
```

Resolve any returned rows through an audited operational decision before migration. The migration intentionally fails instead of deleting ambiguous job history.

## Release gate checklist

- `backend\\gradlew.bat check --no-configuration-cache`
- `pnpm --dir frontend lint`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend test`
- `pnpm --dir frontend build`
- `pnpm --dir generation-worker lint`
- `pnpm --dir generation-worker typecheck`
- `pnpm --dir generation-worker test`
- `pnpm --dir generation-worker build`
- `pnpm exec playwright test`
- `docker compose config`
- `pnpm audit --prod --audit-level moderate`
- Confirm the `Security` workflow passed CodeQL, secret, dependency, and worker image scans.
- Confirm the GitHub Actions workflow at [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) passed on the story branch or pull request before merge.
