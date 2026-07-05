# Implementation Stories: Automated Content Blog

Source contracts: `.omx/specs/deep-interview-automated-content-blog.md`, `.omx/plans/prd-automated-content-blog.md`, `.omx/plans/test-spec-automated-content-blog.md`, and `AGENTS.md`.

## Stories

- Story 0 — Establish the reviewed repository baseline on `prototype`.
  - Preserve approved requirements, PRD, test specification, Change Request, review evidence, `AGENTS.md`, README, and safe ignore rules.
  - Complete docs review and docs-only QA, then close GitHub issue #1 with the first Korean commit and push `prototype`.
- Story 1 — Prove Phase 0 compatibility and scaffold the multi-runtime workspace.
  - Verify Java 25/Spring Boot 4.1 and selected dependencies, Node/pnpm/Next.js, MySQL 8.4, Docker, and provider integration feasibility; document fallbacks.
  - Create root workspace, backend, frontend, generation-worker, contracts, e2e, compose, environment examples, and CI-quality commands.
- Story 2 — Build the Spring/MySQL domain foundation and migrations.
  - Add package-by-feature modules, Flyway schema, JPA validation, Problem Details, audit primitives, posts/revisions/taxonomy/sources/schedules/runs/jobs/outbox tables, indexes, and uniqueness constraints.
  - Prove clean MySQL migration and application-context startup with Testcontainers.
- Story 3 — Implement single-admin JWT, refresh rotation, CSRF, CORS, authorization, and security auditing.
  - Use Spring Security resource-server JWT primitives, memory-only access tokens, opaque rotating refresh families with CAS/reuse revocation, exact Origin and CORS allowlists, double-submit CSRF, rate limits, secure cookies, and generic errors.
  - Pass the complete positive and negative security integration matrix.
- Story 4 — Implement post, revision, taxonomy, search, archive, analytics, and public APIs.
  - Provide draft/publish/archive/delete/restore transitions, atomic revision history, citations, slugging, bounded search/pagination, categories, tags, related posts, and privacy-minimal view aggregation.
  - Generate and verify OpenAPI contracts and lifecycle regression tests.
- Story 5 — Deliver the public Next.js blog experience.
  - Build responsive SSR/revalidated feed, post detail, category, tag, archive, search, RSS, sitemap, metadata, canonical and OG behavior with accessible navigation and safe content rendering.
  - Pass unit, production build, SSR metadata, accessibility, and public Playwright checks.
- Story 6 — Deliver the authenticated content-administration UI.
  - Build login, guarded shell, dashboard, editor/preview, revision diff/restore, taxonomy management, post state controls, and audit views against the completed content APIs.
  - Centralize typed API access and refresh single-flight; pass content-administration Playwright flows without browser credential persistence.
- Story 7 — Implement durable scheduling, collection, source evidence, and manual/scheduled convergence.
  - Add automation topic, source, and schedule persistence/APIs; the domain schedule authority; Quartz JDBC reconciliation; IANA time zones; bounded misfires; MySQL run keys/leases; conditional source fetches; immutable snapshots; normalization; trust policy; and observability.
  - Prove simultaneous manual/scheduled execution creates at most one logical run and survives restart/retry boundaries.
- Story 8 — Implement the provider-neutral generation job contract and worker.
  - Add versioned claim/lease/heartbeat/submit schemas, scoped service authentication, sanitization, prompt/schema/provider versioning, fake provider, and the Phase 0-approved Codex/provider adapter.
  - Prove least privilege, invalid/late/duplicate rejection, lease expiry recovery, prompt-injection containment, and provider substitution.
- Story 9 — Implement deterministic publication, recovery, and automation-administration integration.
  - Enforce source accessibility, independent-origin corroboration, and duplicate gates before automatic publication; atomically store post/revision/citations/audit/outbox evidence.
  - Add signed idempotent Next revalidation, retries, dead letters, bounded TTL, held reasons, and no-duplicate recovery across backend/worker/Next outages.
  - Complete source/topic/schedule management, run history, held-item review, retry/cancel/publish controls, and their administrator Playwright flows after Stories 7 and 8 provide the required APIs.
- Story 10 — Complete release hardening and full-system verification.
  - Add metrics, health checks, runbooks, backup/restore and restart drills, dependency/secret/static analysis, security headers, CI workflows, and production configuration documentation.
  - Pass backend checks, frontend and worker lint/typecheck/test/build, MySQL integration, contract tests, Playwright, compose validation, code review, architecture invariant audit, and adversarial UltraQA.

## Current expanded execution order

- Story 13 ??Generation worker runtime hardening
- Story 14 ??Release security closure
- Story 15 ??Full-stack E2E validation
- Story 16 ??Automation configuration administration
- Story 17 ??Held-run administrative actions
- Story 18 ??Deployment and operations readiness
- Story 19 ??Automation quality and UX hardening

## Constraints

- Every implementation story starts from `prototype`, uses a dedicated story branch and Korean GitHub issue, and merges through a clean pull request.
- Use bounded native subagents for development, testing, and verification when parallel work materially helps.
- Never weaken security, publication, evidence, migration, or release gates to make a story pass.
