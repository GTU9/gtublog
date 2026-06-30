# Generation worker runtime context

## Task statement

Continue the automated blog toward real-world use by closing the highest-priority gap: a runnable, restartable generation worker that polls Spring generation jobs and invokes the pinned Codex SDK without depending on a Codex App session.

## Desired outcome

- A production-shaped worker process can be started from `generation-worker/` with external configuration.
- It continuously claims, heartbeats, generates, and submits jobs without direct database or publication credentials.
- Shutdown, timeout, lease expiry, retry, and secret-handling behavior are deterministic and tested.
- A live unattended credential probe is documented and executable, but is not falsely claimed when no credential is available.

## Known facts and evidence

- `generation-worker/package.json:7-12` has build/lint/test/spike scripts but no runnable `start` process.
- `generation-worker/src/index.ts:1-19` exports library factories only.
- `generation-worker/src/runtime.ts` supports one `runOnce()` call and performs only one heartbeat before generation.
- `generation-worker/src/codex-provider.ts:34-57` constructs `new Codex()` without an externally supplied API key and does not pass an abort signal.
- Installed `@openai/codex-sdk` 0.142.3 types expose `new Codex({ apiKey, env, config })`, thread controls, structured output, and turn `AbortSignal`.
- Official Codex documentation and Context7 confirm `CODEX_API_KEY` for non-interactive automation and structured-output/abort support.
- `docs/adr/0001-generation-provider.md:15-34` keeps Codex SDK conditional until unattended credential and restartability evidence exists.
- Spring already owns job leases, worker authentication, publication decisions, and recovery.

## Constraints

- Spring/MySQL remain the only authority for schedules, evidence, run state, and publication.
- The worker receives no MySQL, administrator, JWT-signing, or direct-publication credential.
- Secrets come only from external configuration and must not appear in logs, committed files, child environments beyond what Codex requires, or error payloads.
- Do not use Codex App automation as the scheduler of record.
- No production deployment or live credential use without explicit authority.
- Preserve the versioned `automation-job-v1` contract.

## Unknowns / external gates

- No externally supplied rotatable Codex credential is currently available to prove a live unattended run.
- The final production host/container platform is not selected.
- Live provider quota, network policy, and billing are external to this repository.

## Likely touchpoints

- `generation-worker/src/runtime.ts`
- `generation-worker/src/codex-provider.ts`
- new worker configuration and CLI entrypoint under `generation-worker/src/`
- `generation-worker/package.json` and `tsconfig.build.json`
- worker unit/integration tests
- `.env.example`, `docs/automation.md`, `docs/development.md`, provider ADR/compatibility evidence
- optional worker container packaging kept independent from Spring/MySQL authority
