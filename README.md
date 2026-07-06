# GTU Blog

GTU Blog는 자동 정보 수집, 자동 초안 생성, 관리자 검수/수정, 공개 블로그 발행을 하나의 흐름으로 묶은 개인 운영형 자동화 블로그 프로젝트입니다.

현재 저장소는 다음 목표를 기준으로 구현되어 있습니다.

- 공개 블로그 페이지 제공
- 관리자 로그인과 글/분류/감사 로그 관리
- 자동화 주제, 소스, 스케줄, 실행 이력 관리
- 보류된 자동화 실행의 재시도, 취소, 수동 발행
- Spring Boot + Next.js + MySQL + generation-worker 기반 구조

## 핵심 기능

### 공개 블로그

- 최신 글 목록
- 글 상세 페이지
- 카테고리/태그별 글 목록
- 검색 페이지
- 아카이브 페이지
- RSS, sitemap, robots.txt
- SSR 기반 메타데이터와 검색엔진 친화 HTML 제공

### 관리자 기능

- 관리자 로그인
- 글 작성, 수정, 발행, 보관, 삭제, 복원
- 리비전 이력 조회와 복원
- 카테고리/태그 관리
- 감사 로그 조회

### 자동화 운영 기능

- 자동화 주제 관리
- 수집 소스 관리
- 자동화 스케줄 관리
- 수동 실행
- 실행 이력 및 실행 상세 조회
- 보류 실행 재시도
- 실행 중 자동화 취소
- 보류 초안 수동 발행
- 운영 진단 정보와 발행 복구 아웃박스 상태 확인

## 기술 스택

### Backend

- Java 25
- Spring Boot 4.1
- Spring MVC
- Spring Data JPA
- Spring Security
- OAuth2 Resource Server / JWT
- Quartz Scheduler
- Flyway
- Micrometer / Prometheus
- jsoup
- Rome
- Resilience4j

### Frontend

- Next.js 16 App Router
- React 19
- TypeScript

### Database

- MySQL 8.4

### Generation Worker

- Node.js
- TypeScript
- Provider-neutral job contract
- `@openai/codex-sdk` 연동은 조건부 운영 경로로 설계

### Test / QA

- JUnit 5
- Testcontainers
- WireMock
- Vitest
- Playwright

## 저장소 구조

```text
backend/             Spring Boot 백엔드
frontend/            Next.js 공개/관리자 UI
generation-worker/   생성 작업 워커
contracts/           API 및 자동화 계약
e2e/                 Playwright E2E
docs/                개발/보안/자동화/운영 문서
ops/                 로컬 운영용 관측 및 구성 파일
```

## 빠른 시작

사전 준비사항:

- Java 25
- Node.js 24+
- pnpm 11+
- Docker Desktop 또는 Docker Engine

### 1. 의존성 설치

```powershell
pnpm install
```

### 2. 환경 변수 준비

`.env.example`을 복사해 로컬 실행용 `.env`를 만들고 placeholder 값을 교체합니다.

### 3. MySQL 실행

```powershell
docker compose up -d mysql
docker compose ps
```

### 4. 백엔드 실행

```powershell
backend\gradlew.bat bootRun
```

### 5. 프론트 실행

```powershell
pnpm --dir frontend dev
```

### 6. 워커 실행 예시

```powershell
pnpm --dir generation-worker build
pnpm --dir generation-worker start:once
```

## 검증 명령

### 루트 품질 게이트

```powershell
pnpm quality
```

### 백엔드

```powershell
backend\gradlew.bat check
backend\gradlew.bat mysqlCompatibilityTest --tests "com.gtublog.automation.AutomationPipelineIntegrationTests"
```

### 프론트엔드

```powershell
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
pnpm exec playwright test
```

### 워커

```powershell
pnpm --dir generation-worker lint
pnpm --dir generation-worker typecheck
pnpm --dir generation-worker test
pnpm --dir generation-worker build
```

### 통합/운영 검증

```powershell
pnpm e2e:fullstack
docker compose config
```

## 운영 메모

- 브라우저와 백엔드는 production에서 same-origin 기준으로 동작하도록 설계됩니다.
- Spring Boot가 인증, 권한, 발행, 자동화 상태의 최종 권한을 가집니다.
- generation-worker는 비신뢰 경계이며 데이터베이스 직접 접근 권한이나 직접 발행 권한을 가지지 않습니다.
- 자동 발행은 출처 접근 가능, 독립 출처 보강, 중복 검사를 모두 통과해야 합니다.
- 보류 실행은 기록으로 남고 관리자 후속 조치가 가능해야 합니다.

## 문서

- [AGENTS.md](./AGENTS.md)
- [docs/development.md](./docs/development.md)
- [docs/security.md](./docs/security.md)
- [docs/automation.md](./docs/automation.md)
- [docs/runbook.md](./docs/runbook.md)
- [docs/deployment.md](./docs/deployment.md)
- [docs/omx-validation-loop.md](./docs/omx-validation-loop.md)

## 현재 상태 요약

- 공개 블로그 기본 기능 구현 완료
- 관리자 글/분류/감사 기능 구현 완료
- 자동화 주제/소스/스케줄 관리 구현 완료
- 보류 실행 재시도/취소/수동 발행 구현 완료
- 관리자 UI 한국어화 및 운영 UX 개선 반영 완료
- 로컬 백엔드/프론트/Playwright/MySQL 기반 검증 루프 정리 완료

## 다음 우선순위

- 실배포 리허설 및 운영 검증 스토리 정의
- 배포 환경 기준 smoke test / 복구 테스트 강화
- 자동화 품질 고도화와 운영 모니터링 보강
