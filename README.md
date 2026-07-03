# GTU Blog

GTU Blog는 자동 수집과 수동 검토를 결합한 한국어 블로그 프로젝트입니다.

한 명의 관리자 사용자가 다양한 주제의 정보를 수집하고, 필요하면 자동 생성 워크플로를 거쳐 글을 발행하며, 직접 수정·분류·검수할 수 있도록 설계되어 있습니다.

현재 저장소는 다음 목표를 중심으로 구현되어 있습니다.

- 공개 블로그 제공
- 관리자 전용 글/분류 관리 UI 제공
- 자동화 수집·발행 파이프라인 기반 마련
- 보안 중심 인증/인가 구조 적용
- 로컬 전체 스택 실행 및 브라우저 기반 기능 검증 가능

## 주요 기능

### 1. 공개 블로그

- 한국어 기반 공개 블로그 UI
- 홈 피드에서 최신 발행 글 목록 노출
- 글 상세 페이지 제공
- 카테고리별 목록 페이지 제공
- 태그별 목록 페이지 제공
- 검색 페이지 제공
- 월별 아카이브 제공
- RSS, sitemap, robots 메타 경로 제공

### 2. 공개 블로그 UI/UX

- 밝은 톤의 한국형 블로그 레이아웃
- 읽기 중심 본문 구조
- 공통 사이드 섹션 제공
  - 블로그 소개
  - 최근 글
  - 카테고리
  - 태그
  - 아카이브
- 반응형 레이아웃 지원

### 3. 관리자 기능

- 관리자 로그인
- 관리자 대시보드
- 드래프트 생성
- 글 수정
- 글 발행
- 글 아카이브
- 글 삭제 / 복원
- 리비전 조회 및 복원
- 카테고리 생성/수정/삭제
- 태그 생성/수정/삭제
- 감사 로그 조회

### 4. 자동화/운영 기능

- 자동화 토픽 관리
- 수집 소스 관리
- 스케줄 관리
- 수동 실행 트리거
- 자동화 진단 정보 확인
- 발행 복구 아웃박스 확인
- 발행 후 공개 반영 경로 지원

### 5. 보안 및 인증

- Spring Security 기반 인증/인가
- JWT 액세스 토큰
- Refresh 토큰 회전 구조
- CORS 설정 적용
- CSRF 방어 정책 포함
- 관리자 API 보호
- 공개 API와 관리자 API 분리

## 기술 스택

### Backend

- Java 25
- Spring Boot 4.1
- Spring Web MVC
- Spring Data JPA
- Spring Security
- OAuth2 Resource Server / JWT
- Quartz Scheduler
- Flyway
- Micrometer + Prometheus registry
- Jsoup
- Rome
- Resilience4j

### Frontend

- Next.js 16 App Router
- React 19
- TypeScript
- 전역 CSS 기반 스타일링

### Database

- MySQL 8.4

### Generation Worker

- Node.js
- TypeScript
- `@openai/codex-sdk`

### Test / QA

- JUnit 5
- Testcontainers
- WireMock
- Vitest
- Playwright

### Tooling

- pnpm workspace
- Gradle Kotlin DSL
- Docker / Docker Compose

## 저장소 구조

```text
backend/             Spring Boot 백엔드
frontend/            Next.js 공개/관리자 UI
generation-worker/   생성 워커
contracts/           API/자동화 계약 문서
e2e/                 Playwright E2E
docs/                개발/운영/보안 문서
scripts/             실행/검증/드릴 스크립트
```

## 현재 구현 기준 핵심 도메인

- 글(Post)
- 리비전(Post Revision)
- 카테고리(Category)
- 태그(Tag)
- 자동화 토픽(Automation Topic)
- 자동화 소스(Automation Source)
- 자동화 스케줄(Automation Schedule)
- 자동화 실행 이력(Automation Run)
- 감사 로그(Audit)
- 발행 아웃박스(Publication Outbox)

## 로컬 실행 환경

권장 사전 요구사항:

- Java 25
- Node.js 24+
- pnpm 11+
- Docker Desktop 또는 Docker Engine

환경 변수는 `.env.example`를 기준으로 로컬 `.env`를 만들어 사용합니다.

## 빠른 시작

### 1. 의존성 설치

```powershell
pnpm install
```

### 2. MySQL 실행

```powershell
docker compose up -d mysql
docker compose ps
```

### 3. 백엔드 실행

```powershell
backend\gradlew.bat bootRun
```

### 4. 프론트 실행

```powershell
pnpm --dir frontend dev
```

### 5. 생성 워커 실행 예시

```powershell
pnpm --dir generation-worker build
pnpm --dir generation-worker start:once
```

## 빌드 및 검증 명령

### 루트 워크스페이스

```powershell
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm quality
```

### 백엔드

```powershell
backend\gradlew.bat check
backend\gradlew.bat bootJar
```

### 프론트엔드

```powershell
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
```

### 생성 워커

```powershell
pnpm --dir generation-worker lint
pnpm --dir generation-worker typecheck
pnpm --dir generation-worker test
pnpm --dir generation-worker build
```

### E2E / 전체 스택 검증

```powershell
pnpm exec playwright test
pnpm e2e:fullstack
docker compose config
```

## 로컬 브라우저 기준 검증된 흐름

다음 흐름은 실제 로컬호스트 환경에서 브라우저로 직접 검증했습니다.

- 관리자 로그인
- 카테고리 생성
- 드래프트 작성
- 포스트 발행
- 공개 홈 반영 확인
- 공개 아카이브 반영 확인
- 공개 상세 페이지 확인
- 공개 검색 확인
- 공개 카테고리 페이지 확인
- 관리자 자동화 화면 확인
- 관리자 감사 로그 확인

## 공개 블로그 화면 특징

- 한국어 기반 UI
- 밝은 블로그형 디자인
- 읽기 쉬운 카드형 포스트 목록
- 사이드바 기반 보조 탐색
- 발행 즉시 홈/아카이브 반영 구조

## 관리자 화면 특징

- 단일 관리자 중심 운영 화면
- 대시보드, 포스트, 분류, 자동화, 감사 화면 분리
- 수동 작성과 자동화 파이프라인 운영을 함께 고려한 구조

## 문서

- [AGENTS.md](D:/study/project/spring/gtublog/AGENTS.md)
- [DESIGN.md](D:/study/project/spring/gtublog/DESIGN.md)
- [docs/development.md](D:/study/project/spring/gtublog/docs/development.md)
- [docs/security.md](D:/study/project/spring/gtublog/docs/security.md)
- [docs/automation.md](D:/study/project/spring/gtublog/docs/automation.md)
- [docs/runbook.md](D:/study/project/spring/gtublog/docs/runbook.md)

## 주의 사항

- 실제 운영 전에는 `.env.example`의 placeholder 값을 반드시 교체해야 합니다.
- 로컬 Codex App 로그인 상태를 생성 워커의 무인 운영 자격으로 사용하면 안 됩니다.
- 자동화 수집은 기본적으로 사설/루프백/메타데이터 네트워크 접근을 제한합니다.

## 프로젝트 상태 요약

현재 저장소는 다음 상태까지 구현되어 있습니다.

- 공개 블로그 기본 기능 구현 완료
- 관리자 글/분류/감사 기능 구현 완료
- 자동화 관리 기초 기능 구현 완료
- 로컬 전체 스택 실행 및 수동 웹 검증 가능
- README, 디자인 문서, 개발 문서 정리 진행

추가 고도화 후보:

- 관리자 UI 전체 한국어화
- 공개 블로그 인기 글/운영자 프로필 위젯 고도화
- 자동 발행 워커 운영 시나리오 확장
- 배포/모니터링 자동화 정리
