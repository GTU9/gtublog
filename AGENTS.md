# AGENTS.md

## Mission and source of truth

Build and maintain a public automated-content blog with one administrator. The current approved implementation plan is `.omx/plans/prd-automated-content-blog.md`; the release test contract is `.omx/plans/test-spec-automated-content-blog.md`; product requirements are `.omx/specs/deep-interview-automated-content-blog.md`.

When these documents disagree, preserve explicit product requirements first, then the approved PRD/ADR, then this operational summary. Update the affected planning documents when an approved decision changes.

## Fixed and selected stack

- Backend: Java 25 LTS and Spring Boot 4.1.x with Gradle Kotlin DSL. Java 21 LTS is allowed only when the Phase 0 compatibility matrix records a concrete Java 25 build/test incompatibility.
- Frontend: Next.js App Router using React 19.2.x and TypeScript. Spring remains the only business/domain API.
- Database: MySQL 8.4 LTS. Flyway is the only schema authority; Hibernate must use schema validation, never automatic production DDL.
- Authentication: Spring Security OAuth2 Resource Server/Jose with Nimbus JWT encoder/decoder, short-lived access JWTs, and rotated opaque refresh tokens.
- Browser boundary: explicit Spring CORS configuration. Production uses a same-origin reverse proxy; development/staging use exact origin allowlists.
- Scheduling: Quartz JDBC JobStore backed by a domain-owned schedule table and reconciliation outbox.
- Generation: provider-neutral worker contract. Codex SDK is preferred only after the release-blocking compatibility/authentication spike approves it; Codex App automation is optional and is never the scheduler of record.

Do not replace fixed stack elements or add infrastructure such as Kafka, Elasticsearch, Kubernetes, or microservices without an approved plan change.

## Planned repository layout

- `backend/`: Spring modular monolith, migrations, backend tests.
- `frontend/`: Next public and administrator application.
- `generation-worker/`: isolated provider adapter and job-contract client.
- `contracts/`: OpenAPI and versioned automation JSON schemas.
- `e2e/`: Playwright cross-service tests.
- `docs/`: architecture, security, automation, and operational runbooks.

Use package-by-feature in the backend: `auth`, `post`, `taxonomy`, `source`, `automation`, `audit`, `analytics`, and `shared`. Keep `shared` small and technical; domain behavior belongs to its feature.

## Autonomous delivery and native subagents

Execute non-trivial delivery through the approved Autopilot sequence: `deep-interview -> ralplan -> ultragoal -> native subagents when useful -> code-review -> ultraqa`. Reuse approved artifacts and resume durable state rather than restarting completed phases.

The operational verification shape of that loop is documented in [docs/omx-validation-loop.md](./docs/omx-validation-loop.md). Use it as the concrete pass/fail procedure for story completion, review re-entry, and docs-only exceptions.

Story progression policy: when one implementation story is fully verified and its required Git/GitHub workflow is complete, the main agent must automatically open the next planned story and continue implementation without pausing for user confirmation. Do not stop at story boundaries merely to ask whether to proceed. Only pause for destructive, irreversible, credential-gated, external-production, or materially scope-changing decisions, or when missing authority blocks progress.

For each Ultragoal story that benefits from parallel work, use three Codex native subagents. Do not substitute the tmux-based OMX `$team` runtime unless the user explicitly changes this project policy.

- Subagent 1, development: implement the story, fix implementation defects, and report changed files and behavior.
- Subagent 2, testing: inspect existing coverage, add required regression and edge-case tests, run the applicable test/build/lint/typecheck gates, and report failures with evidence.
- Subagent 3, verification: independently check requirement coverage, regression risk, security, exception handling, and UX; issue a Change Request when needed; finish with an explicit approval or rejection.
- Main agent: own Ultragoal state and checkpoints, divide work, prevent shared-file conflicts, integrate results, run final verification, and exclusively own Git/GitHub operations.

Subagents must not mutate Ultragoal ledgers, create or merge pull requests, manage GitHub issues, or perform hidden goal-state transitions. They return implementation, test, and review evidence to the main agent. The main agent remains accountable for the integrated result.

If code review or UltraQA is not clean, return to the existing Ultragoal story with the findings preserved. Re-run native subagents for implementation-only defects. When findings require changed requirements or planning, write a Change Request and update the approved plan before implementation. Do not apply destructive, irreversible, production-facing, credential-gated, or materially risky changes without user approval.

## Git and GitHub story workflow

- `prototype` is the integration branch. Create every implementation story from the current approved `prototype` head using a descriptive story branch name.
- One-time bootstrap: when the repository has no commits, treat the approved planning baseline as Story 0. Create its GitHub issue first, complete documentation review and the applicable docs-only QA gate, then commit and push it directly as the initial `prototype` head. All implementation stories must use the normal story-branch and pull-request flow afterward.
- Do not commit a story until implementation, tests, applicable build/lint/typecheck gates, independent verification, code review, and UltraQA are clean for that story.
- Before the first story commit, create a GitHub issue describing the scope, acceptance criteria, test plan, and risks. Link the issue in the commit and pull request.
- GitHub issue titles/bodies, commit subjects/bodies, and pull request titles/bodies must be written in Korean. Commit messages must describe the behavior, tests, and important technical decisions in detail.
- On Windows, all Git/GitHub text that contains Korean must be authored and submitted through an explicit UTF-8 path. Do not pipe Korean text directly through PowerShell standard input to `git commit`, `gh issue`, or `gh pr`; use a UTF-8 file or a UTF-8 API/client path, and verify the saved remote text before considering the step complete.
- After verifier approval, the main agent summarizes changed files, creates the Korean GitHub issue, commits on the story branch, pushes it, opens a pull request targeting `prototype`, verifies required checks, and merges only when the pull request is clean.
- After a story is merged into `prototype`, immediately sync local state, create the next planned story branch from the updated `prototype` head, and continue the delivery loop unless a true blocker from the story progression policy applies.
- Never bypass the issue-before-commit rule, force-push shared branches, rewrite approved history, or merge a rejected story.
- If a story-scoped commit is accidentally applied directly to `prototype` and already propagated to the shared remote, do not attempt ad-hoc history rewrites to hide the mistake. Record the policy deviation in a Korean GitHub issue, preserve the shared history, move subsequent work back to a proper story branch from the latest `prototype` head, and resume the normal issue -> story branch -> verification -> PR -> merge flow.
- If GitHub authentication, repository remote, branch protection, or required external authority is unavailable, preserve the verified local story state and report the exact blocker. Do not create an untracked-policy exception or claim the GitHub workflow completed.

## Architectural invariants

- Spring/MySQL own authentication, authorization, source evidence, post state, publication decisions, revisions, schedules, jobs, audit history, and idempotency.
- All administrator and manual-run actions pass through authenticated Spring endpoints. Next server rendering may call public Spring endpoints only.
- Scheduled and manual automation invoke the same Spring application service.
- A generation worker is untrusted: it has no MySQL, administrator, JWT-signing, or direct-publication credential.
- A post becomes published when its MySQL transaction commits. Next cache revalidation is an idempotent post-commit side effect and must never republish or roll back content.
- Cross-process contracts are versioned in `contracts/`; change producers, consumers, fixtures, and contract tests together.

## Security invariants

- Do not implement a custom bearer `OncePerRequestFilter`. Use Spring Security `oauth2ResourceServer().jwt()` and framework authorization rules.
- Access JWTs live in browser memory only. Never store access or refresh credentials in `localStorage`, source files, logs, frontend bundles, or images.
- Refresh tokens are high-entropy opaque values; store only hashes and family/rotation metadata. Rotation uses transactional compare-and-swap: one concurrent success, then deterministic family revocation on reuse.
- Canonical refresh cookie: host-only, `Secure`, `HttpOnly`, `SameSite=Strict`, and `Path=/api/v1/auth`.
- Cookie-based refresh/logout validates exact `Origin` and a refresh-family-bound double-submit CSRF cookie plus `X-CSRF-Token` header.
- Configure one `CorsConfigurationSource` and `http.cors()`. Never combine wildcard origins with credentials. Test allowed and rejected preflights.
- Keep signing keys, database credentials, provider credentials, and administrator bootstrap secrets in external configuration. Commit only documented placeholders.
- Rate-limit login, refresh, and manual-run endpoints; return generic authentication errors and audit security events without secrets.

## Content and automation invariants

- Spring fetches allowed sources and stores immutable snapshot metadata, hashes, origin lineage, retrieval time, and policy result before generation.
- Corroborate material claims across independent origin domains. Syndicated copies, mirrors, and pages citing the same upstream report count as one origin.
- Required automatic-publication blocks are: inaccessible/missing source, uncorroborated material claim, and existing canonical/fingerprint duplicate.
- The worker may propose citations, but Spring must fetch and verify them before they satisfy a publication gate.
- Store source citations, run ID, provider/prompt/schema version, fingerprint, initial revision, audit entry, and a unique cache-outbox event atomically with automatic publication.
- Every run has an idempotency key, bounded lease/timeouts, explicit terminal state, retry history, and human-readable failure/hold reasons.
- `automation_schedule` is authoritative. Quartz tables are derived state. Store IANA time zones, bound misfires, reconcile trigger changes idempotently, and enforce manual/scheduled races with MySQL uniqueness/leases.

## Backend rules

- Controllers validate and map transport concerns; application services own use cases; domain code owns invariants; repositories own persistence queries.
- Do not expose JPA entities in API contracts. Use explicit DTOs and RFC 9457 Problem Details.
- State-changing use cases define transaction boundaries. External network calls must not hold database transactions open.
- Use Flyway migrations under `backend/src/main/resources/db/migration/mysql/`. Never edit an applied migration; add a new one.
- Add indexes and uniqueness constraints with the feature that needs them. Prove concurrency correctness in MySQL Testcontainers, not H2.
- External collection uses explicit connect/read timeouts, rate limits, retry boundaries, user-agent identification, and source policy checks.

## Frontend rules

- Public routes use Next server rendering/revalidation for indexable HTML and metadata. Interactive administrator features may use Client Components and TanStack Query.
- Keep domain and publication logic out of Next Route Handlers and Server Actions. They may proxy or perform presentation/cache duties only when the PRD permits it.
- Centralize typed API access and authentication refresh single-flight. Browser retry logic is not a substitute for backend CAS correctness.
- Preserve keyboard access, semantic landmarks, labels, error/loading states, and responsive layouts. New public pages require metadata/canonical coverage.
- Sanitize rendered generated content at the trusted backend boundary and use safe rendering on the frontend; never enable arbitrary script execution.

## Testing and verification

Write regression tests with each behavior change. Security, migrations, publication gates, concurrency, and contract changes require integration evidence, not unit tests alone.

Release-blocking coverage includes:

- JWT validity/claims, refresh rotation/replay/concurrency, logout, authorization, CSRF, and positive/negative CORS.
- Empty MySQL migration, Hibernate validation, uniqueness and transactional concurrency.
- Revision restoration, scheduled/manual convergence, three publication blocks, syndicated-source rejection, and restart/retry recovery.
- Worker schema, lease, prompt-injection containment, least privilege, and provider substitution.
- Transactional outbox plus Next outage/signature/replay/cache/RSS/sitemap recovery.
- Public SSR/SEO/accessibility and administrator Playwright flows.

After scaffolding, use the repository-provided wrappers and root pnpm workspace. Required gates are:

```text
backend/gradlew check                         # Windows: backend\gradlew.bat check
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
pnpm --dir generation-worker lint
pnpm --dir generation-worker typecheck
pnpm --dir generation-worker test
pnpm --dir generation-worker build
pnpm exec playwright test
docker compose config
```

If a command is unavailable because its phase has not been scaffolded, report the validation gap; do not silently claim success or substitute an unrelated check.

## Dependency and change discipline

- Prefer Spring Boot BOM and package-manager lockfiles. Pin compatible patches and use automated dependency PRs after the baseline builds.
- New production dependencies require a concrete capability, license/maintenance check, and tests. Prefer platform/framework facilities first.
- Preserve V1 non-goals: no public accounts, multiple authors, comments/likes, newsletter/push, monetization, or social auto-sharing.
- Do not perform production deployment, create paid resources, use live credentials, or delete persistent data without explicit user authorization.
- Keep diffs phase-scoped. Update OpenAPI/schema snapshots, migrations, documentation, and tests in the same change when their contract changes.

## Completion standard

Completion requires fresh test output mapped to the PRD acceptance criteria, no unresolved critical/high security finding, documented migration/config changes, and verifier evidence for cross-service behavior. A green lane-local test is insufficient when the change affects shared security, API, schema, scheduling, publication, or cache contracts.
