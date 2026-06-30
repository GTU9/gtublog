# Automation Operations

## Runtime surfaces

- Spring Boot remains the scheduler of record.
- Quartz drives scheduled execution from the domain `automation_schedule` rows.
- The generation worker only claims and submits jobs; it cannot publish directly.
- Publication becomes true when MySQL commits the post, revision, citations, audit row, and outbox event together.

## Administrator diagnostics

Use the administrator automation page to inspect:

- recent run counts by status;
- pending generation jobs;
- pending and delivered publication outbox events;
- recent hold reasons;
- held source snapshot count.

The same summary is available from `GET /api/v1/admin/automation/diagnostics`.

## Recovery flow

Expired `RUNNING` executions are swept by a Quartz maintenance job. A run uses the shorter pipeline lease until its generation job is committed, then an absolute maximum duration. Every competing transition locks the run before its generation job; recovery then cancels a pending or claimed job, marks the run `FAILED`, clears its lease, and records one system audit event. A late worker submission cannot publish after this transition.

1. Inspect the most recent run detail and hold or failure reason.
2. If the run is blocked by source accessibility or corroboration, correct the source set or rerun later.
3. If content published but cache propagation failed, replay pending outbox events from the administrator page or `POST /api/v1/admin/automation/outbox/process`.
4. If the worker failed, inspect the run audit trail, restart the worker, and retry the run with a new manual trigger.
5. For `RUN_DEADLINE_EXPIRED`, verify worker health and source latency, then retry with a new idempotency key. Do not reuse the expired run.

Runtime controls:

- `AUTOMATION_RUN_PIPELINE_LEASE_DURATION`: maximum time before generation-job handoff.
- `AUTOMATION_RUN_MAX_DURATION`: absolute run deadline after handoff.
- `AUTOMATION_RUN_RECOVERY_INTERVAL`: expired-run sweep interval.

## Metrics

Prometheus scraping is exposed at `/actuator/prometheus`.

Current custom metric families:

- `gtublog_auth_events_total`
- `gtublog_automation_generation_jobs_total`
- `gtublog_automation_source_snapshots_total`
- `gtublog_automation_publication_decisions_total`
- `gtublog_automation_publication_duration_seconds`
- `gtublog_automation_outbox_deliveries_total`
- `gtublog_automation_run_recoveries_total`

Recommended dashboard cuts:

- auth event rate by `action`
- publication decisions by `outcome` and `reason`
- source snapshot result distribution by `source_type`
- outbox retry/delivery trend
- publication duration histogram
# Generation worker runtime

The generation worker is a separately deployed, non-root Node process. Build it with
`pnpm --dir generation-worker build` and run `pnpm --dir generation-worker start` or
`start:once`. It talks only to `/api/v2/internal/generation-jobs` with `X-Worker-Token`.

The v2 terminal request carries a UUID plus a SHA-256 digest of canonical UTF-8 JSON.
Spring recomputes the digest and commits the terminal identity with publication side
effects. A retry with the same identity and digest returns the committed result; any
terminal transition or digest mismatch returns `409`.

Codex production execution is intentionally frozen unless
an external canary attestation is mounted at `GENERATION_CODEX_CANARY_ATTESTATION_PATH`
and its `artifactDigest` matches the runtime digest baked read-only into the image.
That digest covers the emitted JavaScript, deployed production package and dependencies,
lockfile, TypeScript build settings, Dockerfile, and the pinned base-image reference
at `/app/ARTIFACT_DIGEST`. The legacy boolean is
accepted only by the test process. Approval requires the documented
clean-container canary to prove that model-invoked tools cannot read the worker token,
API key, repository, host home, Docker socket, or another process environment. Merely
having a working Codex API key is not approval. The container must run non-root with a
read-only root filesystem and private tmpfs mounts for `/tmp` and `/home/worker`.
Compose also drops all Linux capabilities, enables `no-new-privileges`, limits PIDs,
and uses `node dist/healthcheck.js` to require a live process plus a recent successful
backend contact. Inspect it with `docker compose --profile generation ps`.

Shutdown stops claims, aborts the active turn, stops heartbeat, and attempts only the
already-selected terminal payload within the grace period. Success-submit ambiguity is
never converted into a failure submission.
