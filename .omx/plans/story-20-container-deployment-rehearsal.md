# Story 20 - Container Deployment Rehearsal and Smoke Automation

## Goal

Validate that the production-like container topology can actually boot and serve the implemented blog/admin/automation surfaces, then turn that validation into a repeatable operator smoke path.

## Scope

- Bring up the production-like stack from `compose.prod.yaml` with a safe rehearsal environment.
- Validate reverse-proxy same-origin routing for public pages and administrator API flows.
- Confirm backend startup, MySQL connectivity, frontend rendering, and generation-worker readiness sequencing.
- Document or automate the minimum smoke checks needed after deployment.
- Repair any deployment-asset gaps revealed by the rehearsal, as long as they do not change the approved product scope.

## Explicit exclusions

- Do not provision real cloud infrastructure or production DNS/TLS in this story.
- Do not add new end-user product scope such as multi-admin, comments, or new automation policies.
- Do not weaken security, secret handling, or same-origin boundaries to make the rehearsal easier.

## Acceptance

- A repeatable rehearsal path exists for `compose.prod.yaml` using documented environment variables.
- Public home, post detail, administrator login/session, and automation diagnostics can be smoke-tested against the production-like stack.
- Known deployment blockers and environment assumptions are documented with exact remediation steps.
- Rehearsal validation is reflected in the runbook and/or helper scripts without contradicting `AGENTS.md`, the PRD, or deployment/security docs.
