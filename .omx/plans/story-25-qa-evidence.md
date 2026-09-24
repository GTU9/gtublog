# Story 25 검증 기록

상태: 로컬 백엔드 게이트, 전체 스택 E2E, 독립 설계·코드·검증 검토 통과. Git/GitHub 절차를 진행한다.

## 통과 증거 (2026-09-24)

- `backend\gradlew.bat check --no-daemon --max-workers=1 --no-configuration-cache`: 4분 44초, `BUILD SUCCESSFUL`. MySQL 호환성 테스트 XML 10개 스위트의 83건 모두 통과, 실패·오류 0건.
- `SourceCollectionIntegrationTests`: MySQL/WireMock 23/23. RSS/Atom 기사별 원문, XML 인코딩·DTD·피드 위장, HTML5/HTML4 doctype과 긴 주석, 중복·10건/100건 경계, 실패 증거와 불변 피드 계보를 검증했다.
- `SourceCollectionDeadlineIntegrationTests`: 1/1. 임대에서 예약된 수집 시간이 소진되면 네트워크 요청과 워커 작업 없이 실행을 HELD 처리한다.
- `AutomationPipelineIntegrationTests`: 재시도 동안 이전 실행 잠금 해제와 동시 재시도 단일 실행을 MySQL 트랜잭션으로 검증했다.
- 실제 Spring·MySQL·Next 전체 스택 E2E 2/2 통과 (`E2E_MYSQL_PORT=13307`). 관리자 작성 게시와 서명된 outbox 캐시 재검증을 확인했다.
- Frontend lint/typecheck/test 39건/build, generation-worker lint/typecheck/test 76건/build, Playwright 8/8, 루트·운영 Compose `config --quiet`는 이 스토리의 프런트엔드·워커 코드 변경 전에 통과했다. 해당 코드 변경은 없다.
- `git diff --check`: 통과. 독립 gpt-6-sol 설계 검토는 CLEAR, 독립 gpt-6-sol 코드 검토는 APPROVE. 정식 `architect` 역할의 고정 모델은 현 계정에서 미지원이므로 사용자가 지정한 gpt-6-sol 모델의 독립 검토로 대체했다.
- 독립 verifier: 최신 단위 19건과 MySQL 83건의 실패·오류 0건, 테스트 전용 HTTP 설정, 마이그레이션·계약 경계를 확인하고 APPROVE.

## 실행 참고

- Windows Testcontainers 실행에 `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`, `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`를 사용했다.
- 앞선 전체/대상 실행에서 로컬 WireMock 대량 수집 중 일부 HTTP 요청이 일시적으로 실패해 HELD가 발생했다. 재실행한 수집 통합 테스트 23/23과 최종 전체 `check` 83/83은 통과했다. 원인 예외는 기존 일반 오류 문구만으로 확정할 수 없으므로 로컬 통합 테스트의 잔여 불안정 위험으로 기록한다.
- 이슈 [#78](https://github.com/GTU9/gtublog/issues/78)은 Story 25 커밋 전에 생성됐으며 저장된 한국어 범위·완료 기준을 확인했다.
