# 배포 가이드

## 목적

이 문서는 GTU Blog의 최소 운영 배포 계약을 정의합니다. 특정 클라우드 공급자에 종속되지 않고, 컴포넌트 경계, 시크릿 책임, 기동 순서, 검증, 롤백 기대치를 정리하는 것이 목적입니다.

이 저장소는 운영 자산 기준선으로 [compose.prod.yaml](../compose.prod.yaml), [`.env.production.example`](../.env.production.example), [ops/nginx/production.conf](../ops/nginx/production.conf)를 제공합니다. 운영 대상이 Docker Compose가 아니더라도, 이 파일들이 표현하는 same-origin 경계와 환경 변수 계약은 그대로 유지해야 합니다.

## 런타임 토폴로지

애플리케이션은 다음 다섯 개의 런타임 면으로 구성됩니다.

- 리버스 프록시 / 인그레스
  - TLS 종료
  - 공개 오리진 제공
  - 브라우저의 `/api/v1/**` 요청을 Spring Boot로 라우팅
  - 그 외 브라우저 요청은 Next.js로 라우팅
- Next.js 프론트엔드
  - 공개 블로그와 관리자 HTML/애플리케이션 셸 제공
  - SSR 및 재검증을 위해 Spring의 공개 읽기 API 호출
- Spring Boot 백엔드
  - 인증, 권한, 도메인 상태, 자동화, 발행, 감사, 메트릭의 최종 권한 보유
- MySQL 8.4
  - 글, 인증, 자동화, 감사, 아웃박스의 시스템 오브 레코드
- Generation Worker
  - 제한된 워커 계약으로만 작업 claim/submit이 가능한 별도 프로세스

현재 운영 기준선은 “애플리케이션 컨테이너는 저장소에서 제공하고, MySQL은 외부 서버 또는 별도 관리형 서비스로 연결한다”는 가정을 사용합니다. 즉, `compose.prod.yaml`은 frontend/backend/proxy/worker 중심이며 MySQL 컨테이너를 포함하지 않습니다.

## 표준 브라우저 흐름

- 운영 브라우저 트래픽은 하나의 공개 오리진을 사용해야 합니다.
- 리버스 프록시는 same-origin을 유지해야 합니다.
  - `https://blog.example.com/` → Next.js
  - `https://blog.example.com/api/v1/**` → Spring Boot
- Access JWT는 브라우저 메모리에만 존재해야 합니다.
- Refresh는 `/api/v1/auth` 경로의 host-only `HttpOnly` 쿠키를 사용합니다.

## 환경 변수 계약

### 1. 외부 시크릿 관리가 필요한 값

- `AUTH_PUBLIC_KEY_PEM`
- `AUTH_PRIVATE_KEY_PEM`
- `AUTOMATION_WORKER_SHARED_TOKEN`
- `AUTOMATION_REVALIDATION_SHARED_SECRET`
- `ADMIN_BOOTSTRAP_USERNAME`
- `ADMIN_BOOTSTRAP_PASSWORD`
- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`

운영에서는 반드시 외부 시크릿 스토어 또는 배포 플랫폼의 비밀 변수로 주입해야 하며, 실제 값을 저장소에 커밋하면 안 됩니다.

### 2. 환경별로 달라질 수 있는 값

- `PUBLIC_ORIGIN`
- `SPRING_PROFILES_ACTIVE`
- `AUTH_ALLOWED_ORIGIN`
- `AUTH_COOKIE_SECURE`
- `AUTH_ISSUER`
- `AUTH_AUDIENCE`
- `AUTOMATION_REVALIDATION_BASE_URL`
- `GTUBLOG_SITE_URL`
- `GTUBLOG_PUBLIC_API_BASE_URL`
- `GTUBLOG_APPLICATION_API_BASE_URL`
- `NEXT_PUBLIC_GTUBLOG_APPLICATION_API_BASE_URL`
- `GENERATION_BACKEND_BASE_URL`
- `GENERATION_PROVIDER`
- `PROXY_PORT`

### 3. 운영 기본값 기대치

- `AUTH_COOKIE_SECURE=true`
- `AUTH_ALLOWED_ORIGIN`은 공개 오리진과 정확히 일치
- 브라우저에서 쓰는 `NEXT_PUBLIC_GTUBLOG_APPLICATION_API_BASE_URL`은 same-origin 유지를 위해 `/api/v1` 같은 상대 경로를 권장
- Next 서버 런타임이 Spring 공개/관리자 API를 직접 호출해야 하므로 `GTUBLOG_PUBLIC_API_BASE_URL`, `GTUBLOG_APPLICATION_API_BASE_URL`은 보통 내부 네트워크 절대 주소를 사용
- `AUTOMATION_COLLECTION_ALLOWED_PRIVATE_HOSTS`는 승인된 격리망 요구가 없는 한 비워둘 것
- `GENERATION_PROVIDER`는 canary attestation 조건이 충족되기 전까지 Codex production 모드로 전환하지 말 것

## 저장소 제공 운영 자산

- [compose.prod.yaml](../compose.prod.yaml)
  - `proxy`, `frontend`, `backend`를 기본 서비스로 제공
  - `generation-worker`는 `generation` profile에서만 제공한다. Codex canary attestation 승인 전에는 기본 리허설이 worker 자격 증명을 요구하지 않는다.
  - `prometheus`, `grafana`는 `ops` profile에서 선택적으로 활성화
- [`.env.production.example`](../.env.production.example)
  - 외부 MySQL, 공개 오리진, 인증 키, 워커 토큰, 관측 계정 값을 주입하는 예시
- [ops/nginx/production.conf](../ops/nginx/production.conf)
  - `/api/v1/**`는 Spring Boot, 그 외 브라우저 요청은 Next.js로 보내는 same-origin 예시
- [ops/prometheus/production.yml](../ops/prometheus/production.yml)
  - backend 컨테이너의 `/actuator/prometheus`를 수집하는 production compose 기준 scrape 대상

## 운영용 Compose 기동 예시

1. 예시 환경 파일을 리허설 전용 파일로 복사한 뒤 실제 값으로 수정합니다. `.env.rehearsal`은 Git에서 제외되며 실제 운영 `.env.production`과 분리됩니다.

```powershell
Copy-Item .env.production.example .env.rehearsal
```

2. 외부 MySQL 접속 정보와 운영 시크릿을 채웁니다.
3. rehearsal 환경이 placeholder 없이 Compose 계약을 만족하는지 검사합니다. 이 명령은 컨테이너를 만들거나 외부 MySQL에 연결하지 않습니다.

```powershell
$env:COMPOSE_REHEARSAL_ENV_FILE = ".env.rehearsal"
pnpm rehearsal:compose:preflight
```

4. 운영 스택을 기동합니다.

```powershell
docker compose --env-file .env.rehearsal -f compose.prod.yaml up -d --build
```

5. 관측 스택까지 필요하면 `ops` profile을 함께 사용합니다.

```powershell
docker compose --env-file .env.rehearsal -f compose.prod.yaml --profile ops up -d --build
```

6. `generation-worker`는 별도 Codex canary release gate가 승인된 이후에만 기동합니다. `GENERATION_CODEX_CANARY_ATTESTATION_FILE`에는 컨테이너 `/app/ARTIFACT_DIGEST`와 일치하는 외부 발급 attestation JSON의 **호스트 경로**를 지정합니다. 기본 `ops/attestations/unapproved.json`은 의도적으로 worker 기동을 거부합니다.

```powershell
docker compose --env-file .env.rehearsal -f compose.prod.yaml --profile generation up -d --build
```

7. 프록시 경유 운영 스모크를 실행합니다. `PROD_SMOKE_ADMIN_*`를 지정하면 로그인·세션·자동화 진단도 함께 확인하고, `PROD_SMOKE_POST_SLUG`를 지정하면 공개 게시글 API와 상세 페이지도 확인합니다.

```powershell
$env:PROD_SMOKE_ENV_FILE = ".env.rehearsal"
$env:PROD_SMOKE_BASE_URL = "http://127.0.0.1:13002" # .env.rehearsal의 PROXY_PORT 값과 일치시킵니다.
$env:PROD_SMOKE_ADMIN_USERNAME = "rehearsal-admin"
$env:PROD_SMOKE_ADMIN_PASSWORD = "rehearsal-only-password"
pnpm smoke:compose
```

## 기동 순서

1. MySQL을 시작하고 readiness를 확인합니다.
2. Spring Boot를 시작하고 다음을 확인합니다.
   - `/actuator/health`
   - Flyway 마이그레이션 성공
   - Quartz 기동 완료
3. Next.js를 시작하고 공개/관리자 라우트 렌더링을 확인합니다.
4. Codex canary release gate가 승인된 경우에만 Spring Boot가 정상 상태가 된 뒤 generation-worker를 시작합니다.
5. 리버스 프록시가 same-origin 경로를 올바르게 전달하는지 확인합니다.

`compose.prod.yaml`은 frontend/backend healthcheck 이후 proxy가 시작되도록 구성되어 있습니다. generation-worker는 backend healthcheck 이후에만 시작되며, 미승인 상태에서는 profile 밖에 두어 기본 리허설에서 제외합니다.

## 운영 스모크 체크

배포 직후 최소한 다음을 확인합니다.

1. 공개 홈 페이지가 렌더링된다.
2. 관리자 로그인이 동작한다.
3. 로그인 후 `/api/v1/auth/session`이 인증 세션을 반환한다.
4. `/actuator/health`가 정상이다.
5. `/actuator/prometheus`가 의도한 운영 경계 안에서만 노출된다.
6. `/admin/automation`에서 진단 정보, 실행 이력, 아웃박스 상태가 조회된다.
7. 수동 자동화 실행을 안전하게 시작하고 추적할 수 있다.
8. 최신 발행 글이 다음에 반영된다.
   - 공개 목록
   - 글 상세
   - RSS
   - sitemap

## 복구 릴리스 게이트

배포 자산 변경을 포함한 릴리스는 CI의 `Backup and restore drill`과 `Restart and recovery drill`을 통과해야 합니다. 두 job은 disposable MySQL 8.4 container와 fake provider만 사용하므로 실제 운영 데이터나 Codex API key를 사용하지 않습니다. 따라서 이 검증은 복구 계약의 반복 증거이며, 별도의 Codex 무인 자격증명·격리 canary attestation 승인이나 production adapter freeze를 대체하지 않습니다.

## 리버스 프록시 요구사항

- Spring과 Next가 기대하는 canonical host 및 forwarded protocol 헤더를 보존합니다.
- 운영에서 브라우저용 API 오리진을 별도로 노출하지 않습니다.
- HTTPS를 강제하고 HTTP는 HTTPS로 리다이렉트합니다.
- 관리자 편집과 워커 계약에 맞는 요청 본문 크기 제한을 설정합니다.

저장소에 포함된 `ops/nginx/production.conf`는 최소 예시이며, 실제 TLS 종료와 인증서 관리 방식은 운영 환경에 맞게 바꿔도 됩니다. 단, `/api/v1/**`와 나머지 브라우저 요청의 분리 규칙은 유지해야 합니다.

## 로깅과 관측성

- Spring 메트릭은 보호된 운영 경로에서만 Prometheus 수집을 허용합니다.
- 로그는 가능하면 구조화 JSON으로 남깁니다.
- 다음 값은 절대 로그에 남기지 않습니다.
  - access token
  - refresh token
  - worker shared token
  - private key
  - 시크릿이 섞일 수 있는 원본 생성 payload

## 백업과 복구

시스템 오브 레코드는 MySQL입니다. 최소 운영 기준은 다음과 같습니다.

- 애플리케이션 데이터베이스 정기 논리 백업
- 깨끗한 MySQL 8.4 인스턴스로의 주기적 복원 훈련
- 복원 후 Flyway validation 수행
- 복원 후 다음 스모크 체크 수행
  - 관리자 로그인
  - 공개 글 조회
  - 자동화 진단 화면 조회

실행 절차는 [runbook.md](./runbook.md)를 따릅니다.

## 재시작 및 롤백 기대치

### 안전한 재시작

- Spring 재시작은 이미 커밋된 글을 재발행하면 안 됩니다.
- Outbox 재생은 멱등해야 합니다.
- generation-worker 재시작이 중복 발행 권한으로 이어지면 안 됩니다.
- 만료된 자동화 실행은 문서화된 복구 엔드포인트로 회복 가능해야 합니다.

### 롤백

- 스키마 가정보다 애플리케이션 프로세스를 먼저 롤백합니다.
- 적용된 Flyway 마이그레이션은 수정하거나 삭제하지 않습니다.
- 릴리스를 되돌려야 할 경우, 이전 호환 애플리케이션 빌드를 배포하고 스키마 보정이 필요하면 새 forward migration으로 해결합니다.

## 배포 전 체크리스트

- `backend\gradlew.bat check`
- `backend\gradlew.bat mysqlCompatibilityTest --tests "com.gtublog.automation.AutomationPipelineIntegrationTests"`
- `pnpm --dir frontend test`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend build`
- `pnpm --dir generation-worker test`
- `pnpm --dir generation-worker build`
- `pnpm exec playwright test`
- `docker compose config`
- `docker compose --env-file .env.production.example -f compose.prod.yaml config`
- rehearsal 전용 비밀값을 채운 뒤 `pnpm rehearsal:compose:preflight`
- GitHub CI와 Security 워크플로가 릴리스 후보 브랜치에서 모두 녹색

## 현재 비목표

이 문서는 아직 다음 항목을 정의하지 않습니다.

- 클라우드 벤더별 인프라 모듈
- blue/green 또는 canary 트래픽 전환
- 관리형 시크릿 스토어 연동 코드
- 오토스케일링 정책

실제 배포 대상이 결정되면 이후 story에서 구체화합니다.
