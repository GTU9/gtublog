# ADR 0001: Keep a provider-neutral generation boundary

- Status: Accepted; Codex SDK production adapter decision is superseded by CR-002
- Date: 2026-06-29
- Decision owner: backend publication boundary

## Context

The product prefers Codex-backed generation, but Spring and MySQL must remain the authority for source evidence, run state, publication decisions, revisions, audit history, and idempotency. A generation worker must not receive database, administrator, JWT-signing, or direct-publication credentials.

The official TypeScript package `@openai/codex-sdk` supports programmatic server-side execution on Node.js 18 or newer. The selected Node.js 24 baseline meets that requirement. A Phase 0 executable spike also proved that the pinned SDK can start a non-interactive run, enforce read-only sandboxing and an approval policy of `never`, disable tool network access and web search, honor an abort signal, and validate structured JSON output.

## Decision

Keep `@openai/codex-sdk` only as a compatibility candidate behind the injected `GenerationProvider` boundary. The local authenticated-session spike approves SDK/runtime feasibility only. CR-002 blocks the Codex SDK production adapter because the current single-worker process does not establish agent-phase credential separation and the legacy v1 attestation is self-issued.

The runnable-worker gate must implement and verify all of the following before production use:

1. unattended authentication uses an externally supplied, rotatable credential;
2. the worker runs in an isolated workspace with bounded time and resources;
3. output is constrained to the versioned job/result schema and validated by Spring;
4. cancellation, lease expiry, duplicate submission, and retry behavior are deterministic;
5. prompts, source snapshots, logs, and errors do not expose secrets;
6. the deployed worker requires no Codex App session and can restart independently;
7. the worker has no MySQL, administrator, JWT-signing, or publication credential.

Phase 0 is not blocked by the missing unattended authentication and deployment probe. A future production adapter must pass the stronger CR-002 gate: agent-phase credential separation, two fresh clean-container restart probes, and an independent verifier's signed attestation. Any substitute changes only the adapter behind the same contract; it must not weaken Spring publication gates or move scheduling authority into Codex App automation.

CR-003 adds an explicitly selected, tool-free OpenAI Responses adapter as that substitute. It directly calls the Responses API with tools disabled and strict structured output; it does not start a Codex CLI process. The backend `AUTOMATION_WORKER_PROVIDER` and worker `GENERATION_PROVIDER` must both select `openai-responses`, otherwise the worker cannot claim a compatible job. This preserves the CR-002 freeze for the Codex SDK path and does not itself authorize production deployment or automatic publication.

## Consequences

- Codex App automation may be an optional operator convenience, never the scheduler of record.
- Provider substitution does not change Spring APIs, evidence storage, or publication rules.
- Local SDK feasibility is reproducible with `pnpm --dir generation-worker spike:codex` while an authenticated Codex session is available.
- The v2 contract, credential, timeout, lease, and deployment boundaries are implemented. Codex SDK production readiness remains blocked until CR-002 is superseded by a credential-boundary design and independently signed probe evidence.

## References

- <https://developers.openai.com/codex/sdk>
- <https://developers.openai.com/codex/noninteractive>
