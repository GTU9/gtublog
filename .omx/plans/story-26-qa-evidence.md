# Story 26 — 자동 분류 검증 증거

기준 브랜치: `codex/automatic-taxonomy` (`prototype`의 `7076404`에서 분기). 이슈: [#80](https://github.com/GTU9/gtublog/issues/80). 실제 운영 배포는 수행하지 않았다.

## 수용 기준과 증거

| 기준 | 구현/검증 |
| --- | --- |
| 승인된 분류 후보를 작업에 고정 | v3 claim에 ID·slug·name 카탈로그를 보관한다. MySQL 테스트가 정렬·빈 목록·101개 카테고리·201개 태그 보류를 확인했다. |
| 생성 선택의 형식과 정책 구분 | worker v3 계약 테스트가 구조 오류 실패와 정책상 무효한 ID/중복 태그 전달을 확인했다. MySQL 테스트는 잘못된 JSON·추가 필드 400/상태 불변, 유효한 구조의 무효 선택 초안 저장/HELD를 확인했다. |
| 동시 변경과 발행 원자성 | MySQL의 실제 `data_lock_waits`를 관측하며 카테고리 변경·태그 삭제 경합을 확인했다. 강제 outbox 실패는 글·리비전·분류 링크·outbox 전체 롤백을 확인했다. |
| 공개 탐색과 관리자 처리 | MySQL 테스트가 자동 글의 카테고리·태그·검색 조회, 유효/무효 보류 초안 override를 확인했다. 관리자 화면은 저장된 선택과 보류 이유를 표시한다. |
| v2 호환성과 재전송 | v2 serialized claim에 v3 필드가 없고 성공 제출은 draft 보존/HELD이며 override가 거부됨을 MySQL 테스트가 확인했다. Java는 TypeScript가 계산한 v3 fixture digest와 일치하고, taxonomy가 달라진 동일 terminal 제출은 충돌한다. |

## 실행한 검증

- `backend\gradlew.bat check --no-daemon --max-workers=1 --no-configuration-cache`: 통과. JUnit XML 합계 18 suites, 118 tests, 0 failures/errors. 새 v3 MySQL 통합 16/16, 변경된 v2 worker/pipeline 32/32 포함.
- `pnpm --dir generation-worker lint`, `typecheck`, `test`, `build`: 통과. 9 files, 81 tests.
- `pnpm --dir frontend lint`, `typecheck`, `test`, `build`: 통과. 7 files, 39 tests.
- Playwright 단독 브라우저 테스트: 8/8 통과. Windows에서 Playwright가 직접 띄운 dev server 종료가 지연되어 서버를 별도 실행한 뒤 테스트 명령을 정상 종료시켜 확인했다.
- `node scripts/run-fullstack-e2e.mjs` (`E2E_MYSQL_PORT=13307`): Spring/MySQL/Next 2/2 통과.
- `docker compose config --quiet`, `docker compose -f compose.prod.yaml config --quiet`, `git diff --check`: 통과.

## 검토와 운영 범위

독립 설계 검토와 계획 비평은 수정된 전환 절차를 승인했다. 독립 코드 검증자는 새 MySQL 테스트 16/16을 확인하고 Change Request 없이 구현을 승인했다. 운영 전환은 [자동화 운영 가이드](../../docs/automation.md)의 사전 v2 종료, worker/schedule 중지, 구 백엔드 worker API 차단, 진행 중 요청 종료, 구 인스턴스 제거, 새 백엔드 v2→HELD 확인 순서를 따라야 한다. 이 스토리는 운영 중지·배포를 실행하지 않는다.

새 기능을 직접 통과시키는 Spring/MySQL 통합 검증과 worker 계약 검증이 있고, 전체 스택 E2E는 기존 공개 발행·outbox 회귀를 확인한다. 실제 provider API 호출과 운영 혼합 버전 배포는 이 로컬 검증 범위 밖이다.
