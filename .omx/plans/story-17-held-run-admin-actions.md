# Story 17 - Held Run Administrative Actions

## Goal

Add safe administrator controls for retrying held automation runs, cancelling active runs, and manually overriding publication for a narrowly approved subset of held outcomes.

## Scope

- Add administrator retry controls for held runs that create a new run lineage with `retryOfRunId`.
- Add administrator cancellation for active `RUNNING` runs, including generation-job cancellation and explicit audit evidence.
- Add manual override publication for held runs only when the hold reason is:
  - automatic publication disabled for the topic
  - insufficient independent origin corroboration
- Preserve the original held run as historical evidence even after override publication succeeds.
- Surface generated draft context, available run actions, and resolution state in the administrator automation UI.

## Explicit exclusions

- Do not allow override publication for blocked sources or duplicate-publication holds.
- Do not mutate the original held run into `PUBLISHED` or erase its hold reason.
- Do not introduce physical deletion semantics for historical automation records.
- Do not broaden override publication to arbitrary hold reasons without a new approved change request.

## Acceptance

- Held runs expose backend-approved available actions and generated draft context in run detail responses.
- Retrying a held run creates a new run with `retryOfRunId` linked to the original run and marks the original run as retried.
- Cancelling an active run cancels any pending or claimed generation job and leaves one explicit administrator cancellation audit event.
- Manual override publication succeeds only for approved hold reasons, creates a published post, records an explicit override audit action, and preserves the original held run state.
- Backend integration tests cover positive and negative retry/cancel/override paths.
- Frontend mock tests and Playwright administration coverage prove the new held-run controls.
