# Story 24 검증 기록

상태: 로컬 구현, 독립 코드 검토, 백엔드·프런트엔드·워커·전체 스택 검증 통과. GitHub 이슈 #76 생성 완료.

## 통과 증거 (2026-09-24)

- `backend\\gradlew.bat check --no-daemon --max-workers=1`: 종료 코드 0. Windows Testcontainers에서 `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`, `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`를 사용했다.
- `backend\\gradlew.bat mysqlCompatibilityTest --tests 'com.gtublog.automation.PublicationOutbox*' --no-daemon`: MySQL 통합 9/9 통과. Windows Testcontainers에서는 `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`, `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`를 사용했다.
- Frontend lint/typecheck/test 39건/build, generation-worker lint/typecheck/test 76건/build, 루트 및 운영 Compose config: 통과.
- 실제 Spring + MySQL + Next 전체 스택 E2E: 발행과 signed outbox cache convergence 2/2 통과 (`E2E_MYSQL_PORT=13307`).
- 재시작 복구 드릴 `node scripts/run-restart-recovery-drill.mjs`: 종료 코드 0. 인도 중단 후 재시작·재시도·outbox 배출을 확인했다.
- 화면 Playwright `corepack pnpm exec playwright test`: 8/8 통과, 종료 코드 0.
- 독립 코드 검토: critical/high/medium 지적 없음, 최종 APPROVE.

`13307`은 로컬에서 `13306`이 이미 사용 중이어서 선택한 포트다. 재시작 드릴은 재처리 backoff 동안 `/outbox/process`를 반복 호출해 회복을 검증한다.
