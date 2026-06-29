# GTU Blog

한 명의 관리자가 여러 주제의 정보를 수집·검증하고 글을 자동 또는 수동으로 발행하는 블로그입니다. Spring Boot가 도메인과 게시 정책을 소유하고, Next.js가 공개 블로그와 관리자 UI를 제공하며, 격리된 생성 워커가 공급자 중립적인 콘텐츠 생성 작업을 수행합니다.

## Workspace

- `backend/`: Java 25 + Spring Boot 4.1 modular monolith
- `frontend/`: Next.js App Router + React 19.2 + TypeScript
- `generation-worker/`: provider-neutral TypeScript generation boundary
- `contracts/`: versioned OpenAPI and automation schemas
- `e2e/`: Playwright cross-service tests
- `docs/`: compatibility and operations documentation

## Start here

1. Install the prerequisites in [`docs/phase-0-compatibility.md`](docs/phase-0-compatibility.md).
2. Copy `.env.example` to an ignored `.env` and replace the placeholders.
3. Follow [`docs/development.md`](docs/development.md).
4. Use the quality commands defined in [`AGENTS.md`](AGENTS.md).

Approved requirements and test contracts live under `.omx/specs/` and `.omx/plans/`. Development branches from `prototype` into one reviewed story branch at a time.
