# Story 19 - Automation Quality and UX Hardening

## Goal

Improve operator trust and day-to-day usability around automated collection, held-run review, and generated-draft quality without broadening the V1 feature scope.

## Candidate scope

- P0: Refine automation administrator terminology and mixed-language labels so held-run and diagnostics flows are readable without implementation knowledge.
- P0: Improve automation run-detail readability, action hierarchy, and resolution-state explanations in the admin UI.
- P1: Add clearer guidance for held reasons, retry outcomes, cancel semantics, and manual override consequences.
- P1: Expose stronger generated-draft review context such as citation summary, source diversity, and duplicate-risk hints.
- P2: Tighten public/admin visual consistency and Korean-first content presentation where current text still feels implementation-oriented.
- P1: Add targeted regression coverage for the refined automation UX paths, including browser checks for the held-run control flow.

## Current evidence motivating the story

- `frontend/src/automation-admin-ui.tsx` still exposes several operator-facing terms (`hold reason`, `resolution status`, `outbox`) too close to implementation language for everyday use.
- `frontend/app/admin/automation/page.tsx` uses functionally correct toast/error strings, but the wording can be made more operator-oriented and decision-supportive.
- The current automation detail panel shows raw status and hold values, but operator guidance remains too close to internal implementation wording.
- Existing E2E coverage proves the main flow works, but it does not yet assert the refined Korean-first operator messaging and decision guidance.

## Deferred until approved

- Public multi-admin workflows
- Rich collaborative editing
- AI quality scoring that changes publication gates automatically
- New infrastructure or provider changes outside the existing worker contract
