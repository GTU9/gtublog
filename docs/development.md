# Development baseline

## Prerequisites

- Java 25 LTS
- Node.js 24 and pnpm 11.7
- Docker Desktop or Docker Engine with Compose v2 or newer

Copy `.env.example` to the ignored `.env` file and replace every placeholder locally. Never commit the resulting file.

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

The generation worker only exposes a provider-neutral library in Story 1. Durable job polling begins in Story 8.

## Quality gates

`pnpm quality` runs the backend clean/check/package gate, Node lint/typecheck/tests/builds, Playwright, and Compose configuration validation. A running Docker daemon is required; without one, the mandatory MySQL Testcontainers task fails the command.
