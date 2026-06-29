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

1. Inspect the most recent run detail and hold reason.
2. If the run is blocked by source accessibility or corroboration, correct the source set or rerun later.
3. If content published but cache propagation failed, replay pending outbox events from the administrator page or `POST /api/v1/admin/automation/outbox/process`.
4. If the worker failed, inspect the run audit trail, restart the worker, and retry the run with a new manual trigger.

## Metrics

Prometheus scraping is exposed at `/actuator/prometheus`.

Current custom metric families:

- `gtublog_auth_events_total`
- `gtublog_automation_generation_jobs_total`
- `gtublog_automation_source_snapshots_total`
- `gtublog_automation_publication_decisions_total`
- `gtublog_automation_publication_duration_seconds`
- `gtublog_automation_outbox_deliveries_total`

Recommended dashboard cuts:

- auth event rate by `action`
- publication decisions by `outcome` and `reason`
- source snapshot result distribution by `source_type`
- outbox retry/delivery trend
- publication duration histogram
