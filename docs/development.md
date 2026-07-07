# 개발 기본선

## 사전 준비

- Java 25 LTS
- Node.js 24 및 pnpm 11.7
- Docker Desktop 또는 Docker Engine + Compose v2 이상

`.env.example`을 무시 대상인 `.env`로 복사한 뒤 placeholder 값을 로컬 값으로 교체합니다. 생성된 `.env` 파일은 절대 커밋하지 않습니다.

source collection은 기본적으로 loopback, private, link-local, reserved, metadata-network 대상과 그 redirect target을 차단합니다. 격리된 개발/테스트 환경이 아닌 경우 `AUTOMATION_COLLECTION_ALLOWED_PRIVATE_HOSTS`는 비워 두세요. 로컬 feed가 꼭 필요하다면 신뢰하는 정확한 host 이름만 쉼표로 나열하고, 넓은 네트워크 범위는 허용하지 않습니다.

## 인프라 시작

```powershell
docker compose up -d mysql
docker compose ps
```

## 애플리케이션 실행

```powershell
backend\gradlew.bat bootRun
pnpm --dir frontend dev
```

Story 8에서 shared generation-job contract, internal worker claim/submit API, provider-neutral worker runtime이 추가되었습니다. 장기 폴링이나 상시 호스팅 자체는 애플리케이션 선택 사항이며, 현재 저장소는 worker를 library/runtime 테스트와 backend integration coverage로 검증합니다.

## 품질 게이트

`pnpm quality`는 backend clean/check/package, Node lint/typecheck/test/build, Playwright, Compose configuration validation을 함께 실행합니다. Docker daemon이 실행 중이어야 하며, 그렇지 않으면 필수 MySQL Testcontainers 작업 때문에 명령이 실패합니다.

원격 기준선은 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)에 있습니다. 이 워크플로는 `prototype`, `story/**`, 그리고 `prototype` 대상 pull request에 대해 backend check, frontend / generation-worker 품질 작업, Playwright E2E, `docker compose config`를 실행합니다.

가능하면 push 전에 로컬 게이트를 먼저 통과시키고, 원격 워크플로는 merge guard이자 Linux runner parity check로 사용하세요.

## 실서비스 기반 브라우저 검증

Docker가 사용 가능한 상태에서 `pnpm e2e:fullstack`를 실행합니다. 이 명령은 일회성 MySQL 8.4 컨테이너를 만들고, 임시 RSA signing key pair를 생성하며, 실제 Spring API와 production Next build를 기동한 뒤 public SSR publication까지 포함한 로그인 흐름을 검증합니다. 성공, 실패, 중단 여부와 관계없이 데이터베이스 컨테이너는 정리됩니다. 기본 포트는 13306(MySQL), 18080(Spring), 13001(Next)이며 `E2E_*_PORT` 변수로 덮어쓸 수 있습니다.

이 테스트는 development mock fallback을 명시적으로 비활성화합니다. 따라서 backend 요청이 실패하면 fixture content로 대체되지 않고 브라우저 테스트 자체가 실패해야 합니다.

## Generation worker

`.env.example`의 generation 관련 변수를 채운 뒤 worker를 빌드하고 `pnpm --dir generation-worker start:once`로 단일 claim을 실행할 수 있습니다. 종료 코드는 다음 의미를 갖습니다.

- `0`: 작업 없음 또는 성공
- `2`: 설정 / 인증 / 계약 실패
- `3`: 일시적인 backend 또는 provider 실패
- `4`: 종료 / timeout

로그는 structured JSON 형식이며, 의도적으로 event, worker ID, job ID, category, status만 포함합니다. 컨테이너 health probe는 `node dist/healthcheck.js`를 사용하며, worker 프로세스가 살아 있고 shutdown이 시작되지 않았으며 `GENERATION_READINESS_MAX_AGE_MS` 안에 성공적인 backend contact가 있을 때만 ready가 됩니다.

Codex adapter는 기본적으로 비활성입니다. 로컬 Codex App 로그인 상태는 unattended credential이 아니며, runtime container에 절대 마운트하면 안 됩니다.

## 교차 참고 문서

- 배포 토폴로지와 production startup order: [deployment.md](./deployment.md)
- 운영용 Docker Compose/env/proxy 기준선: [../compose.prod.yaml](../compose.prod.yaml), [../.env.production.example](../.env.production.example), [../ops/nginx/production.conf](../ops/nginx/production.conf)
- 운영 drill과 복구 절차: [runbook.md](./runbook.md)
- 브라우저 / 보안 경계 요구사항: [security.md](./security.md)
- 환경 변수 기준선: [../.env.example](../.env.example)
