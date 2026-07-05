# Story 18 - Deployment and Operations Readiness

## Goal

Turn the current implementation into an operator-ready baseline by documenting deployment topology, environment contracts, startup order, validation steps, and failure recovery guidance without changing the core product scope.

## Scope

- Define the production deployment topology for Next.js, Spring Boot, MySQL, and the generation worker.
- Document required environment variables, secret ownership, and which values differ between local and production.
- Document reverse-proxy and same-origin routing expectations for browser/API traffic.
- Add operator-facing startup, smoke-check, restart, backup/restore, and rollback guidance.
- Repair the corrupted README so the repository entry point accurately reflects the current system and commands.
- Capture the next automation quality/UX hardening candidates so Story 19 can start without rediscovering open gaps.

## Explicit exclusions

- Do not introduce production infrastructure provisioning code such as Terraform, Kubernetes manifests, or cloud-vendor resources in this story.
- Do not change authentication, publication, or scheduling behavior unless documentation reveals a concrete correctness bug.
- Do not loosen Codex production-adapter freeze constraints or unattended-credential boundaries.

## Acceptance

- A new deployment/operations document explains component topology, startup order, secret boundaries, smoke checks, and rollback expectations.
- README is valid UTF-8 Korean text and reflects the actual implemented architecture, commands, and documents.
- Development and runbook documents cross-reference deployment and operational verification guidance consistently.
- Story 19 quality/UX hardening candidates are documented with explicit scope and priority hints.
- Documentation-only validation completes without unresolved contradictions against `AGENTS.md`, the PRD, or the existing runbook/security guidance.
