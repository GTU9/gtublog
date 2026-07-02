# Change Request - Held Run Administrative Actions

## Reason

Held-run retry, cancel, and manual publish are materially riskier than CRUD administration because their semantics can alter publication history, idempotency, and audit guarantees.

## Decisions still required

- Which held reasons are eligible for manual override publication.
- Whether override publication creates a distinct audit action rather than mutating the original held run.
- Whether retry creates a new run lineage with `retryOfRunId` semantics.
- What cancellation means for active, pending, and already-terminal runs.
- Whether any delete operation should mean retire/disable rather than physical removal once history exists.

## Current policy

- Story 16 proceeds with safe configuration administration only.
- Story 17 may implement held-run actions after this Change Request is approved and reflected in the PRD or accepted plan artifacts.
