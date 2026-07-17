# UltraQA Report — Story 20

## Goal and success criteria

- Goal: validate a production-like external-MySQL Compose topology and provide repeatable, fail-closed deployment smoke automation.
- Stop condition: base Compose builds and boots; public, admin, diagnostics and post-detail smoke paths pass; fail-closed worker and malformed inputs are demonstrated; cleanup completes.
- Safety bounds: no live credentials, cloud deployment or persistent-data deletion. A dedicated local rehearsal database was created and retained; only Compose containers/network and local ignored credentials were removed.

## Scenario matrix

| ID | Scenario | Expected signal | Actual result | Status | Cleanup |
| --- | --- | --- | --- | --- | --- |
| QA-01 | Rehearsal preflight with real isolated values | no container or database connection; Compose config valid | passed | passed | local env file removed |
| QA-02 | Base external-MySQL Compose image build and boot | proxy, frontend and backend healthy | passed | passed | stack removed |
| QA-03 | Same-origin public/admin smoke | rendered home, public API, login/session, diagnostics and outbox pass | passed | passed | stack removed |
| QA-04 | Public post detail smoke | API and `/posts/compose-rehearsal-post` render successfully | passed | passed | fixture remains only in isolated database |
| QA-05 | Unapproved worker profile | worker is absent from base stack; explicitly started worker rejects missing Codex credential | passed | passed | stopped and removed with stack |
| QA-06 | Approval bypass attempt | preflight rejects `GENERATION_CODEX_ENV_ISOLATION_APPROVED=true` | passed | passed | temporary env removed |
| QA-07 | Wrong worker expectation | smoke fails when generation worker is required but profile is absent | passed (expected failure) | passed | none |
| QA-08 | Malformed smoke endpoint | `ftp://` base URL is rejected before network work | passed (expected failure) | passed | none |
| QA-09 | Existing backend port collision | WireMock test must not bind HTTP 8080 | fixed with dynamic HTTP port; full backend check passed | passed | none |

## Commands run

- `[0] docker compose --env-file .env.rehearsal -f compose.prod.yaml build` — production images built.
- `[0] docker compose --env-file .env.rehearsal -f compose.prod.yaml up -d --wait` — base topology healthy.
- `[0] pnpm smoke:compose` — full public/admin/automation/post-detail smoke passed against `http://127.0.0.1:13002`.
- `[0] pnpm rehearsal:compose:preflight` — isolated configuration gate passed.
- `[0] pnpm lint`, `[0] pnpm typecheck`, `[0] frontend/worker tests`, `[0] frontend/worker builds` — Node quality gates passed.
- `[0] backend\\gradlew.bat check` with the installed Java 25 path — unit and MySQL compatibility suites passed after the WireMock isolation fix.
- `[0] docker compose --env-file .env.production.example -f compose.prod.yaml config --quiet` — documented example remains syntactically valid.

## Failure found and fixed

- `PinnedSourceHttpClientTests` used a dynamic HTTPS port but WireMock's default HTTP port 8080. A pre-existing local backend therefore caused a bind failure. The test now also uses `dynamicPort()`, preserving the TLS hostname assertion while removing the ambient-port dependency.
- The first empty rehearsal database had a half-applied initial migration after the first boot attempt. It was preserved; a new isolated database was used for the successful clean-migration rehearsal. This confirms Flyway correctly fails closed after a partial migration.

## Residual risks

- The Codex canary attestation is still an operational digest gate, not a cryptographically signed credential/provenance mechanism. That belongs to the separate unattended-Codex approval story.
- The smoke checks bearer-token session access, not a browser refresh-cookie/CSRF round trip. Existing browser E2E remains the coverage for that boundary.
