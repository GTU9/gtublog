# ADR 0001: Keep Codex SDK as the preferred conditional adapter

- Status: Accepted conditionally; production adapter remains frozen pending the clean-container canary
- Date: 2026-06-29
- Decision owner: backend publication boundary

## Context

The product prefers Codex-backed generation, but Spring and MySQL must remain the authority for source evidence, run state, publication decisions, revisions, audit history, and idempotency. A generation worker must not receive database, administrator, JWT-signing, or direct-publication credentials.

The official TypeScript package `@openai/codex-sdk` supports programmatic server-side execution on Node.js 18 or newer. The selected Node.js 24 baseline meets that requirement. A Phase 0 executable spike also proved that the pinned SDK can start a non-interactive run, enforce read-only sandboxing and an approval policy of `never`, disable tool network access and web search, honor an abort signal, and validate structured JSON output.

## Decision

Keep `@openai/codex-sdk` as the preferred conditional adapter candidate behind the injected `GenerationProvider` boundary. The local authenticated-session spike approves SDK/runtime feasibility only. It does not freeze Codex SDK as the production adapter because an externally supplied unattended credential and restartable deployment environment were not available during Phase 0.

The runnable-worker gate must implement and verify all of the following before production use:

1. unattended authentication uses an externally supplied, rotatable credential;
2. the worker runs in an isolated workspace with bounded time and resources;
3. output is constrained to the versioned job/result schema and validated by Spring;
4. cancellation, lease expiry, duplicate submission, and retry behavior are deterministic;
5. prompts, source snapshots, logs, and errors do not expose secrets;
6. the deployed worker requires no Codex App session and can restart independently;
7. the worker has no MySQL, administrator, JWT-signing, or publication credential.

Phase 0 is not blocked by the missing unattended authentication and deployment probe. Instead, Story 8's first gate must pass that probe before Codex SDK is frozen for production use. Any substitute changes only the adapter behind the same contract; it must not weaken Spring publication gates or move scheduling authority into Codex App automation.

## Consequences

- Codex App automation may be an optional operator convenience, never the scheduler of record.
- Provider substitution does not change Spring APIs, evidence storage, or publication rules.
- Local SDK feasibility is reproducible with `pnpm --dir generation-worker spike:codex` while an authenticated Codex session is available.
- The v2 contract, credential, timeout, lease, and deployment boundaries are implemented. Production readiness remains blocked until a clean-container, two-restart canary proves API-key and worker-token non-disclosure from model-invoked tools.

## References

- <https://developers.openai.com/codex/sdk>
- <https://developers.openai.com/codex/noninteractive>
