# G011 Backup/Restore Drill Evidence (2026-07-03)

## Command

`pnpm drill:backup-restore`

## What the drill executed

1. Started a disposable MySQL 8.4 source container.
2. Ran the real-service Playwright publish flow against live Spring Boot and production Next.js.
3. Exported the resulting `gtublog_e2e` database with `mysqldump`.
4. Restored that dump into a second disposable MySQL 8.4 container.
5. Re-ran live Spring Boot and production Next.js against the restored database.
6. Verified:
   - administrator login still works after restore
   - the previously published public post still renders
   - the automation diagnostics page still loads after restore

## Result

Pass.

Both full-stack phases succeeded on 2026-07-03:

- `real Spring, MySQL, and Next publish an administrator-authored post`
- `restored MySQL backup preserves admin login, public post reads, and automation diagnostics`

## Notes

- The drill uses disposable containers and leaves no persistent database changes behind.
- The restore verification respects the project's in-memory access-token rule by re-authenticating before navigating the administrator shell after a public-page round trip.
