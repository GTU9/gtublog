# Test Specification: Automated Content Blog

## Purpose

This test plan proves the twelve product and security acceptance criteria under `## Testable Acceptance Criteria` in `.omx/specs/deep-interview-automated-content-blog.md:96-107` and the architecture in `.omx/plans/prd-automated-content-blog.md`.

## Release-blocking suites

### Security

- Login succeeds only for the configured administrator and is rate limited.
- Access JWT: valid, expired, not-yet-valid, wrong issuer, wrong audience, wrong signature, missing role, and malformed cases.
- Refresh: valid rotation, old-token reuse, revoked family, expired token, logout, concurrent refresh with exactly one CAS success and deterministic family revocation on reuse, exact Origin, and double-submit CSRF cookie/header binding.
- Authorization: public read endpoints, admin CRUD, automation control, audit read, and worker-only claim/submit endpoints.
- CORS: allowed origin preflight, rejected origin, allowed headers/methods, Authorization header, credentialed refresh, and no wildcard-with-credentials.
- Secrets and JWT signing material are absent from source, logs, browser storage, images, and generated frontend bundles.

### Database and migrations

- Empty MySQL 8.4 starts with all Flyway migrations applied.
- Upgrade path from every released migration baseline passes.
- Hibernate validation succeeds and cannot create/alter tables.
- Required unique constraints prevent slug, canonical URL, active refresh token, job lease, and publication idempotency conflicts.

### Post and revision lifecycle

- Draft/create/edit/publish/archive/soft-delete transitions enforce rules.
- Every edit creates a revision; restore creates a new revision without losing history.
- Source citations, taxonomy, slug, audit record, and post transition commit atomically.
- Search, category, tag, archive, pagination, related posts, and basic view aggregation return bounded deterministic results.

### Automation pipeline

- Scheduled and manual entry points call the same application service.
- Source unreachable/missing holds the item.
- Single uncorroborated source holds the item.
- Canonical URL or fingerprint duplicate does not create a second published post.
- Passing content auto-publishes with sources and first revision.
- Simultaneous scheduled/manual runs publish at most once.
- The domain schedule table remains authoritative when Quartz rescheduling fails; reconciliation is retryable, time zones are stable, and misfires coalesce according to policy.
- Retry after timeout does not duplicate state; cancellation and restart leave a recoverable run.
- RSS/HTML malformed input, redirects, 429, 5xx, timeouts, ETag/304, and rate limit behavior are covered.
- Two syndicated URLs derived from one upstream report count as one origin and cannot satisfy corroboration.

### Generation worker contract

- Worker can claim only leased jobs and cannot submit another worker's or expired job.
- Output must match versioned JSON schema and size/time limits.
- Prompt injection content in sources cannot expand worker permissions or alter the output contract.
- Invalid HTML, unsafe URLs, scripts, and unsupported fields are rejected/sanitized.
- Worker service credential cannot access admin/publication or database surfaces.
- The provider adapter can be replaced by a fake/alternate provider without changing the versioned job schema or Spring publication decision tests.
- production-adapter gate must prove agent-phase credential separation, unattended authentication with externally supplied rotatable credentials, restartable deployment without a Codex App session, deterministic timeout/cancellation/lease/retry behavior, and two independent clean-container restart probes. An artifact digest JSON alone is insufficient: an independent verifier must validate fresh raw evidence and issue a signed attestation. Until this exists, the Codex SDK production adapter remains frozen under CR-002.

### Publication outbox and cache recovery

- A post, first revision, verified citations, audit row, and unique revalidation outbox event commit atomically.
- Next revalidation failure leaves the post published, retries idempotently by event ID, and eventually dead-letters without creating another post.
- The signed revalidation endpoint rejects invalid signatures, replay outside its window, unknown tags, and unauthorized routes.
- Post/list/category/tag/RSS/sitemap caches converge after recovery, while bounded TTL prevents indefinite staleness.

### Frontend and SEO

- Anonymous public routes render useful HTML without client JavaScript.
- Each post has unique title, description, canonical, OG metadata, structured data where appropriate, and source links.
- RSS/sitemap include a newly published post after cache propagation.
- Admin routes redirect unauthenticated users and recover correctly after access-token refresh.
- Admin flows: create/edit/preview/publish/delete/restore, source/topic/schedule update, manual run, held review/retry.
- Keyboard navigation, landmarks, labels, contrast, and axe critical violations are checked.

## Quality budgets

- No critical/high dependency or secret-scan findings at release.
- Backend API p95 target under local representative load: public cached reads <300 ms, admin CRUD <500 ms excluding external collection.
- Public page Core Web Vitals/Lighthouse targets are recorded after the first production-like build; accessibility score must be at least 90 with no critical axe violation.
- Automation run timing is separately measured; every external request has a timeout and no run may remain `RUNNING` after its lease/timeout window.

## Evidence matrix

| Criterion | Primary evidence |
| --- | --- |
| Public read/admin isolation | MockMvc security matrix + Playwright |
| Manual editing and restore | service integration + Playwright |
| Daily and manual automation | Quartz/Testcontainers integration + UI E2E |
| Automatic publish/three blocks | decision unit tests + MySQL integration + E2E |
| No duplicates under concurrency | parallel integration test + unique constraint proof |
| JWT and CORS | negative/positive MockMvc suites + deployed browser fixture |
| Worker least privilege/provider substitution | contract tests + network/config inspection |
| SEO/blog navigation | Next build + SSR assertions + Playwright |
| Audit/recovery | transaction failure injection + restart drill |
| Outbox/cache recovery | Next outage injection + idempotent replay + RSS/sitemap assertions |

## Final verification order

1. Static formatting, lint, typecheck, architecture rules, dependency and secret scans.
2. Backend unit/security tests.
3. Frontend and worker unit/contract tests.
4. MySQL/Flyway/WireMock integration tests.
5. Production builds and Compose smoke test.
6. Playwright public/admin/automation E2E.
7. Restart, missed-worker, backup/restore, and observability drills.
8. Verifier maps fresh evidence to all PRD acceptance criteria; any gap blocks completion.
