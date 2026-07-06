# Phase 0 호환성 매트릭스

2026-06-29 기준 검증되었습니다. 이 문서는 공식적으로 공개된 호환성과, 이 워크스테이션에서 실제로 실행해 확인한 결과를 구분합니다.

| 구성 요소 | 선택 기준선 | 근거 및 로컬 결과 | 결정 / 대안 |
| --- | --- | --- | --- |
| Java | Temurin 25.0.3 LTS | Spring Boot 4.1은 Java 17~26을 지원합니다. checksum으로 검증한 workspace-local JDK로 Gradle wrapper, unit/context test, dependency resolution, packaging을 성공적으로 실행했습니다. | Java 25를 유지합니다. Java 21은 구체적인 build/test 비호환이 재현되고 이 문서에 기록된 경우에만 허용합니다. |
| Spring Boot | 4.1.0 | 해당 릴리스는 Spring Initializr에서 제공되며, Gradle build는 dependency-management plugin을 통해 Boot BOM을 사용합니다. | 4.1.x patch만 사용합니다. snapshot은 사용하지 않습니다. |
| Gradle | wrapper 9.5.0 | Spring Boot 4.1은 Gradle 8.14+ 및 9.x를 지원합니다. `backend\gradlew.bat check`가 Java 25와 함께 통과했고, 필수 MySQL compatibility task도 포함되었습니다. | 커밋된 wrapper와 dependency lockfile을 공식 진입점으로 유지합니다. |
| Node.js | 24.18.0 | `node --version`이 로컬에서 통과했습니다. Next.js 16.2.9는 Node 20.9 이상을 요구합니다. | 초기 workspace 기준선을 Node 24로 고정합니다. |
| pnpm | 11.7.0 | `pnpm --version`이 로컬에서 통과했고 루트 manifest가 pnpm 11.7.0을 고정합니다. | Corepack 또는 동등한 pinned pnpm 설치를 사용합니다. |
| Next.js / React | 16.2.9 / 19.2.7 | lint, route type 생성, TypeScript check, Vitest, production Turbopack build가 Node 24에서 통과했습니다. 최신 Next.js는 더 이상 `next lint`를 기본 제공하지 않으므로 lint는 ESLint 직접 실행 방식을 사용합니다. | 선택한 major/minor 범위 안에서 patch release만 갱신합니다. |
| MySQL | 8.4.10 LTS | Testcontainers가 pinned 공식 multi-platform digest를 시작했고, MySQL 8.4를 확인했으며, Flyway compatibility migration을 skip/failure 없이 적용했습니다. 같은 Compose 서비스는 기존 로컬 MySQL 8.0 서비스가 3306을 사용 중이어서 host port 3307에서 `healthy`가 되었습니다. | MySQL 8.4.10과 digest pin을 유지합니다. Story 2에서 production domain migration과 concurrency coverage를 추가합니다. |
| Docker Compose | Docker Desktop 4.79.0 / Engine 29.5.3 / Compose v5.1.4 | `docker compose config`가 통과했고 MySQL 서비스가 `healthy` 상태가 되었으며, 이후 test container/network를 named volume 삭제 없이 정리했습니다. | Windows 개발 기준선은 Docker Desktop으로 유지합니다. 별도 설치된 MySQL 8.0 서비스가 3306을 사용 중일 때는 `MYSQL_PORT=3307`을 사용합니다. |
| Generation provider | provider-neutral boundary 뒤의 `@openai/codex-sdk` 0.142.3 선호 후보 | 로컬 authenticated-session spike가 Node 24, read-only sandboxing, approval `never`, tool network와 web search 비활성화, 90초 abort signal, schema-constrained output 조건에서 통과했습니다. 다만 외부 unattended production credential이나 재시작 가능한 배포 환경은 없었습니다. | Codex SDK는 조건부 선호 후보로만 유지합니다. production adapter freeze는 Story 8의 첫 번째 게이트로 넘기고, 실패 시 계약을 약화시키지 않는 대체 adapter를 Change Request로 선택해야 합니다. |

## Backend dependency spike

Gradle build는 Spring Boot 4.1 BOM을 통해 framework-integrated library를 해석합니다: Flyway 12.4, Quartz 2.5, Micrometer 1.17, Testcontainers 2.0. BOM이 관리하지 않는 source collection 및 test-only library는 명시적으로 pin했습니다: jsoup 1.21.1, ROME 2.1.0, Resilience4j 2.3.0 core retry/rate-limiter module, WireMock standalone 3.13.1. Resilience4j는 Spring Boot 3 starter가 아니라 core module을 사용해 초기 Spring Boot 4 경계에서 auto-configuration mismatch를 피합니다.

Java 25와 Docker가 준비된 뒤에는 `backend\gradlew.bat dependencies`와 `backend\gradlew.bat check`가 의존성 해석과 호환성의 기준 명령입니다. Story 1에는 release-blocking MySQL 8.4/Flyway compatibility probe가 포함되고, Story 2에서 production domain migration과 schema/concurrency integration coverage를 추가합니다.

## Codex 가능성 판단

공식 Codex SDK 문서는 `@openai/codex-sdk`를 server-side Node.js 18+ 애플리케이션용으로 제공하며 programmatic run을 지원합니다. 선택한 Node 24 런타임은 이 최소 요구사항을 충족합니다. pinned 0.142.3 SDK는 로컬 실행 가능성 spike를 통과했고, production adapter freeze는 의도적으로 Story 8로 연기했습니다.

- SDK 의존성은 `generation-worker` 안에 격리된 선호 후보로 남깁니다.
- spike는 non-interactive execution, read-only sandboxing, approval denial, tool network / web search 비활성화, bounded cancellation, output-schema enforcement를 입증했습니다.
- Story 8에서는 추가로 externally supplied unattended credential, job/result contract integration, lease expiry, retry behavior, deployment restartability, least privilege를 입증해야 합니다.
- Codex는 versioned job payload를 통해 source snapshot만 받으며, MySQL, JWT signing key, administrator credential, direct publication credential은 받지 않습니다.
- unattended external credential과 restartable deployment probe가 Story 8에서 통과되기 전까지 production adapter를 고정하지 않습니다.
- 이 게이트를 통과하지 못하면 승인된 Change Request로 다른 adapter를 선택해야 하며, Spring publication gate와 cross-process schema는 그대로 유지합니다.

공식 참고 자료:

- Spring Boot system requirements: <https://docs.spring.io/spring-boot/system-requirements.html>
- Next.js installation: <https://nextjs.org/docs/app/getting-started/installation>
- Codex SDK: <https://developers.openai.com/codex/sdk>

## 로컬 검증 명령

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

전체 루트 `pnpm quality` 게이트는 checksum으로 검증한 workspace-local Java 25 런타임과 Docker Desktop 환경에서 통과했습니다. 실행 범위는 `backend\gradlew.bat clean check bootJar`, 생략되지 않은 MySQL 8.4/Flyway compatibility test, frontend 및 worker의 lint/typecheck/test/build, Playwright, Compose configuration validation까지 포함합니다.
