# Cross-process contracts

Spring owns contract publication. Producers, consumers, fixtures, and contract tests must change together.

- `openapi/`: public, administrator, and worker HTTP APIs (Story 4 onward).
- `automation/`: versioned generation job JSON schemas (Story 8 onward).

Current executable automation contract baseline:

- `automation/v1/claim-request.schema.json`
- `automation/v1/claim-response.schema.json`
- `automation/v1/submit-request.schema.json`
- `automation/v1/fixtures/*.json`

The runnable worker uses the breaking v2 contract:

- `automation/v2/claim-request.schema.json`
- `automation/v2/claim-response.schema.json`
- `automation/v2/heartbeat-response.schema.json`
- `automation/v2/submit-request.schema.json`

V2 requires UTC/offset timestamps and an idempotent terminal UUID plus canonical
SHA-256 payload digest. Active v1 jobs must be drained or audit-cancelled and
re-enqueued before migration V8 can run.

Do not place database entities, credentials, or provider-specific payloads in these contracts.

The Phase 0 provider decision is recorded in `docs/adr/0001-generation-provider.md`. Story 8 must introduce the first executable schema and change producers, consumers, fixtures, and contract tests together.
