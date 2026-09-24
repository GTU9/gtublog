# Story 34 — 정형 관찰 발행 원자성·승인 경합 QA

관련 계획: [Story 34](./story-34-v4-publication-atomicity-race-hardening.md), [GitHub 이슈 #96](https://github.com/GTU9/gtublog/issues/96). 기준: `prototype` `6e94f6d`.

## 검증할 계약

| 시나리오 | 성공 기준 | 증거 |
| --- | --- | --- |
| v4 양성 발행 중 outbox 삽입 실패 | HTTP 500, run/job은 제출 전 상태, post/revision/citation/taxonomy/claim/decision/진단/outbox/발행 감사는 모두 롤백 | MySQL 대상 테스트 통과 |
| 장애 제거 후 동일 terminal 재시도·재전송 | 게시물·claim·outbox 각 한 번만 확정 | MySQL 대상 테스트 통과 |
| 그룹 쌍 승인 철회 선행 | 실제 승인 철회 서비스가 먼저 commit, 제출은 `HELD`, 공개 부작용 없음 | MySQL 경합 테스트 통과 |
| v4 발행 선행 후 그룹 쌍 승인 철회 | 발행 하나가 확정되고 이후 철회가 성공, 중복 부작용 없음 | MySQL 경합 테스트 통과 |
| 출처 승인 철회 선행 | 실제 승인 철회 서비스가 먼저 commit, 제출은 `HELD`, 공개 부작용 없음 | MySQL 경합 테스트 통과 |
| v4 발행 선행 후 출처 승인 철회 | 발행 하나가 확정되고 이후 철회가 성공, 중복 부작용 없음 | MySQL 경합 테스트 통과 |

MySQL Testcontainers의 `performance_schema.data_lock_waits`에서 첫 요청이 테스트 보유 주제/출처 행 잠금을 기다리는 상태를 확인했다. 잠금 해제 후 첫 요청을 외부 트랜잭션의 commit 직전에 멈추고, 두 번째 요청이 첫 트랜잭션의 행 잠금을 기다리는 상태를 확인한 뒤 첫 commit을 허용했다. 테스트용 outbox 트리거는 `finally`에서 제거했다. 최종 대상 클래스 XML은 **46개 테스트, 실패·오류 0**, Gradle 종료 코드 0이다. 첫 대상 실행의 기존 v3 수동 게시 fixture에서 일시적 기사 수집 실패가 발생했지만 동일 전체 클래스를 재실행해 통과했다.

## 적대적 시나리오 행렬

| ID | 주체·상황 | 실행·설치 | 기대/실제 신호 | 정리 |
| --- | --- | --- | --- | --- |
| AQ-01 | 정상 v4 worker, 유효한 두 기사 | 기존 양성 MySQL 테스트 | 카드·인용·claim·outbox 1회, 통과 | 영속 fixture 정리 |
| AQ-02 | 저장 장애를 유도하는 운영 실패 | outbox `BEFORE INSERT` 오류 트리거 | HTTP 500 뒤 모든 게시 부작용 롤백, 통과 | `finally`에서 트리거 제거 |
| AQ-03 | 실패 후 동일 terminal 재시도·재전송 | AQ-02 이후 트리거 제거 후 동일 payload 2회 | 첫 재시도만 게시, 재전송은 idempotent, 통과 | fixture 정리 |
| AQ-04 | 그룹 쌍·출처 승인 철회 관리자와 worker 경합 | 실제 승인 서비스와 제출을 주제/출처 행 잠금 및 commit 장벽 뒤 대기 | 각 승인 종류에서 철회 우선 시 `HELD`, 발행 우선 시 게시 1회; 네 순서 통과 | 잠금 트랜잭션 종료 |
| AQ-05 | 악의적 문구·잘못된 v4 계약 | 기존 hostile Markdown/형식·digest 테스트 | 링크·스크립트 실행 없음, 잘못된 요청 거부; 대상 클래스 통과 | fixture 정리 |
| AQ-06 | 거짓 녹색 신호·테스트 장치 오류 | 최초 컴파일·트리거 권한·MockMvc 기대값·존재하지 않는 테이블 오류 후 실제 테스트 재실행 | 실패를 제품 결함과 구분하고 수정, 대상 클래스 최종 46/46 | 임시 코드·트리거 없음 |

OMX 런타임의 중단·재개, 오래된 상태 파일, CLI 입력 주입 시나리오는 이 Spring 발행 경계의 실행 입력이 아니므로 이 스토리의 적대적 범위에서 제외한다. worker 생성 입력은 기존 v4 계약·악의적 문구 테스트로 확인했다.

## 품질 게이트

- frontend lint/typecheck/test/build: 통과, Vitest 41/41.
- generation-worker lint/typecheck/test/build: 통과, Vitest 88/88.
- Playwright: 9/9 통과.
- `docker compose config --quiet`: 통과.
- backend MySQL 대상: 46/46 통과. 전체 `check`: 24묶음/203개, 실패·오류·건너뜀 0, `BUILD SUCCESSFUL`.
- 독립 verifier **APPROVE**, 코드 리뷰 **APPROVE**(높은 위험 0), 설계 리뷰 **CLEAR**. 적대적 QA 행렬 AQ-01~AQ-06 확인.

전체 `check`의 앞선 두 시도에서는 기존 출처/RSS 수집 fixture가 각각 한 번 실패하고 Testcontainers MySQL 응답 대기가 길어졌다. 두 실패 사례는 각각 단독 MySQL 재실행에서 통과했다. 최종 전체 검사는 Windows의 컨테이너 누적을 줄이기 위해 임시 Gradle init script로 `mysqlCompatibilityTest`의 `forkEvery=4`, `maxParallelForks=1`을 설정하고 `--max-workers=1`, `--no-configuration-cache`, `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`로 실행했다. 이는 저장소의 빌드 정의를 변경하지 않았으며 임시 script는 실행 후 제거했다. 전체 검사 종료 코드는 0이다.

정책 한계: 검증된 카드가 관찰 문구 자체의 참을 보증하지 않으며, 숨겨진 재배포 관계를 탐지할 수 없다는 Story 33 한계는 유지한다. 이 스토리는 일반 관리자 게시 API에 전역 중복 claim을 추가하지 않는다.
