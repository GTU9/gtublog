# Cross-process contracts

Spring owns contract publication. Producers, consumers, fixtures, and contract tests must change together.

- `openapi/`: public, administrator, and worker HTTP APIs (Story 4 onward).
- `automation/`: versioned generation job JSON schemas (Story 8 onward).

Do not place database entities, credentials, or provider-specific payloads in these contracts.

The Phase 0 provider decision is recorded in `docs/adr/0001-generation-provider.md`. Story 8 must introduce the first executable schema and change producers, consumers, fixtures, and contract tests together.
