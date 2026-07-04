# Change Request - Held Run Administrative Actions

## Reason

Held-run retry, cancel, and manual publish are materially riskier than CRUD administration because their semantics can alter publication history, idempotency, and audit guarantees.

## Approved decisions

- Manual override publication is allowed only for held runs whose hold reason is one of:
  - automatic publication is disabled for the topic
  - corroboration failed because the run has fewer than two independent allowed origins
- Manual override publication is never allowed for:
  - inaccessible or blocked source snapshots
  - duplicate canonical source or duplicate fingerprint holds
  - runs with no stored generated draft result
- Override publication creates a distinct administrative resolution on the existing held run and a distinct audit action. It must not rewrite the historical fact that the automatic pipeline held the run.
- Retry creates a new run lineage with `retryOfRunId` semantics. The original run remains terminal and gains a retry resolution marker.
- Cancellation applies only to active `RUNNING` runs. It cancels any pending/claimed generation job, marks the run failed with an administrator-cancelled reason, and records an explicit audit action.
- Already-terminal runs (`HELD`, `SUCCEEDED`, `FAILED`) cannot be cancelled through this administrative endpoint.
- Historical automation sources and schedules remain disable-first resources; Story 17 does not change Story 16 deletion safety rules.

## Current policy

- Story 16 proceeds with safe configuration administration only.
- Story 17 may implement held-run actions using the approved policy above.
