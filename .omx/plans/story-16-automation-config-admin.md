# Story 16 - Automation Configuration Administration

## Goal

Complete the safe administration surface for automation topics, sources, and schedules without introducing underdefined held-item override behavior.

## Scope

- Add administrator CRUD flows for automation topics.
- Add administrator create, update, delete, and disable-oriented conflict handling for automation sources.
- Add administrator create, update, delete, and disable-oriented conflict handling for automation schedules.
- Surface schedule synchronization state so Quartz failures are visible instead of looking like silent success.
- Preserve existing run history, diagnostics, manual run, and outbox controls.

## Explicit exclusions

- Do not implement held-item retry, cancel, or manual publish state transitions in this story.
- Do not physically delete topics with historical data semantics that are not yet approved.
- Do not weaken SSRF, duplicate, or publication gate behavior to simplify the UI.

## Acceptance

- Admin can create and edit automation topics from the UI.
- Admin can create and edit sources and schedules for the selected topic from the UI.
- Referenced sources and schedules return `409` with disable guidance instead of being silently deleted.
- Schedule responses expose synchronization state and any out-of-sync message.
- Backend integration tests cover sync-state response and conflict deletion behavior.
- Frontend tests cover new mock admin flows.
