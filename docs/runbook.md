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
- Confirm the GitHub Actions workflow at [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) passed on the story branch or pull request before merge.
