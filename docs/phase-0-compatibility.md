# Phase 0 compatibility matrix

Verified on 2026-06-29. This document distinguishes published compatibility from checks actually run on this workstation.

| Component | Selected baseline | Evidence and local result | Decision / fallback |
| --- | --- | --- | --- |
| Java | Temurin 25.0.3 LTS | Spring Boot 4.1 supports Java 17 through 26. A checksum-verified workspace-local JDK ran the Gradle wrapper, unit/context test, dependency resolution, and packaging successfully. | Keep Java 25. Java 21 is allowed only if a concrete build or test incompatibility is reproduced and recorded here. |
| Spring Boot | 4.1.0 | The release is available from Spring Initializr. The Gradle build uses the Boot BOM through the dependency-management plugin. | Use 4.1.x patches. Do not use snapshots. |
| Gradle | wrapper 9.5.0 | Spring Boot 4.1 supports Gradle 8.14+ and 9.x. `backend\gradlew.bat check` passed with Java 25, including the mandatory MySQL compatibility task. | The committed wrapper and dependency lockfile are the supported entry points. |
| Node.js | 24.18.0 | `node --version` passed locally. Next.js 16.2.9 requires Node 20.9 or newer. | Pin the project floor to Node 24 for the initial workspace. |
| pnpm | 11.7.0 | `pnpm --version` passed locally and the root manifest pins pnpm 11.7.0. | Use Corepack or an equivalent pinned pnpm install. |
| Next.js / React | 16.2.9 / 19.2.7 | Lint, generated route types, TypeScript checks, Vitest, and the production Turbopack build passed on Node 24. Lint uses ESLint directly because modern Next.js no longer provides `next lint`. | Stay on patch releases within the selected major/minor constraints. |
| MySQL | 8.4.10 LTS | Testcontainers started the pinned official multi-platform digest, confirmed MySQL 8.4, and applied the Flyway compatibility migration with zero skips/failures. The same Compose service reached `healthy` on host port 3307 because the existing local MySQL 8.0 service occupies 3306. | Keep MySQL 8.4.10 and the digest pin. Story 2 adds production domain migrations and concurrency coverage. |
| Docker Compose | Docker Desktop 4.79.0 / Engine 29.5.3 / Compose v5.1.4 | `docker compose config` passed and the MySQL service reached `healthy`; the test container/network were then removed without deleting the named volume. | Docker Desktop is the verified Windows development baseline. Override `MYSQL_PORT=3307` while the separately installed MySQL 8.0 service uses 3306. |
| Generation provider | `@openai/codex-sdk` 0.142.3 preferred candidate behind a provider-neutral boundary | A local authenticated-session spike passed on Node 24 with read-only sandboxing, approval `never`, tool network and web search disabled, a 90-second abort signal, and schema-constrained output. No external unattended production credential or restartable deployment environment was available. | Keep Codex SDK as the preferred conditional candidate only. The production adapter freeze moves to Story 8's first gate, or an approved substitute adapter must be selected without weakening the contract. |

## Backend dependency spike

The Gradle build resolves framework-integrated libraries through the Spring Boot 4.1 BOM: Flyway 12.4, Quartz 2.5, Micrometer 1.17, and Testcontainers 2.0. Source collection and test-only libraries that are not managed by the BOM are pinned explicitly: jsoup 1.21.1, ROME 2.1.0, Resilience4j 2.3.0 core retry/rate-limiter modules, and WireMock standalone 3.13.1. Resilience4j uses core modules rather than its Spring Boot 3 starter so the initial Spring Boot 4 boundary does not depend on a mismatched auto-configuration layer.

`backend\gradlew.bat dependencies` and `backend\gradlew.bat check` are the authoritative resolution and compatibility checks once Java 25 and Docker are available. Story 1 includes a release-blocking MySQL 8.4/Flyway compatibility probe; Story 2 adds the production domain migrations and their schema/concurrency integration coverage.

## Codex feasibility decision

The official Codex SDK documentation publishes `@openai/codex-sdk` for server-side Node.js 18+ applications and supports programmatic runs. The selected Node 24 runtime meets its runtime floor. The pinned 0.142.3 SDK passed a local executable feasibility spike, and the production adapter freeze is intentionally deferred to Story 8:

- the SDK dependency remains isolated in `generation-worker` as the preferred candidate;
- the spike proved non-interactive execution, read-only sandboxing, approval denial, disabled tool network/web search, bounded cancellation, and output-schema enforcement;
- Story 8 must additionally prove externally supplied unattended credentials, job/result contract integration, lease expiry, retry behavior, deployment restartability, and least privilege;
- Codex receives source snapshots through a versioned job payload and never receives MySQL, JWT-signing, administrator, or direct-publication credentials;
- an unattended external credential and restartable deployment probe must pass during Story 8 before the production adapter is frozen;
- if that gate fails, an approved Change Request must select another adapter. Spring publication gates and cross-process schemas remain unchanged.

Official references:

- Spring Boot system requirements: <https://docs.spring.io/spring-boot/system-requirements.html>
- Next.js installation: <https://nextjs.org/docs/app/getting-started/installation>
- Codex SDK: <https://developers.openai.com/codex/sdk>

## Local verification commands

```powershell
node --version
pnpm --version
pnpm install --frozen-lockfile
pnpm quality
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
pnpm --dir generation-worker lint
pnpm --dir generation-worker typecheck
pnpm --dir generation-worker test
pnpm --dir generation-worker build
pnpm --dir generation-worker spike:codex
pnpm exec playwright test
backend\gradlew.bat check
docker compose config
```

The full root `pnpm quality` gate passed with the checksum-verified workspace-local Java 25 runtime and Docker Desktop. It executed `backend\gradlew.bat clean check bootJar`, the non-skipped MySQL 8.4/Flyway compatibility test, frontend and worker lint/typecheck/tests/builds, Playwright, and Compose configuration validation.
