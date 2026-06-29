# Development baseline

## Prerequisites

- Java 25 LTS
- Node.js 24 and pnpm 11.7
- Docker Desktop or Docker Engine with Compose v2 or newer

Copy `.env.example` to the ignored `.env` file and replace every placeholder locally. Never commit the resulting file.

Source collection rejects loopback, private, link-local, reserved, and metadata-network destinations by default, including redirect targets. Keep `AUTOMATION_COLLECTION_ALLOWED_PRIVATE_HOSTS` empty outside isolated development and tests. If a local feed is required, list only the exact trusted host names separated by commas; do not use broad network ranges.

## Start infrastructure

```powershell
docker compose up -d mysql
docker compose ps
```

## Run applications

```powershell
backend\gradlew.bat bootRun
pnpm --dir frontend dev
```

Story 8 adds the shared generation-job contract, internal worker claim/submit API, and provider-neutral worker runtime. Long-running polling/hosting remains an application choice; the repository currently validates the worker through library/runtime tests and backend integration coverage.

## Quality gates

`pnpm quality` runs the backend clean/check/package gate, Node lint/typecheck/tests/builds, Playwright, and Compose configuration validation. A running Docker daemon is required; without one, the mandatory MySQL Testcontainers task fails the command.

The remote baseline lives in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). It runs the backend check, frontend and generation-worker quality jobs, Playwright E2E, and `docker compose config` on GitHub Actions for `prototype`, `story/**`, and pull requests targeting `prototype`.

Use the local gate before pushing when possible; use the remote workflow as the merge guard and Linux runner parity check.
