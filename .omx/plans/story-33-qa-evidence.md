# Story 33 — 조건부 출처 관찰 카드 QA

관련 이슈: [#94](https://github.com/GTU9/gtublog/issues/94). 기준 브랜치: `codex/structured-source-mention-publication-gate`.

## 주장 범위

양성 v4 `SOURCE_MENTION` 게시물은 저장된 두 기사에 동일 문구가 관찰되었다는 사실만 말한다. 그 문구가 주장하는 내용의 참이나 알려지지 않은 재배포 관계를 보증하지 않는다. v2/v3 자유 서술은 계속 자동 발행하지 않는다. 기본 작업 스키마도 v3이며 신규 v4 작업만 명시적으로 선택할 수 있다.

## 검증 행렬

| 시나리오 | 기대 결과 | 확인 결과 |
| --- | --- | --- |
| 단일 관찰, 완전 기사 2건, 현재 승인/그룹 쌍/분류/주제 설정 유효 | 고정 귀속 카드, 인용 2개, `SUCCEEDED` run + `PUBLISHED` decision, 감사/outbox 한 번 | MySQL 양성 통합 테스트 통과 |
| 승인 철회·revision 변경, source 비활성화·URL/type 변경, 주제 발행 비활성화 | 현재 잠금 조회 후 `HELD`, 공개 부작용 없음 | source 비활성, pair 철회, origin 승인 철회/revision, source URL/type 변경 통과. 주제 비활성은 기존 처리와 정적 검토 |
| 같은 출처·host·그룹 또는 동일 canonical/hash/upstream과 간접 공유 계보 | `HELD`, 독립 증거로 계산하지 않음 | 동일 source/host/group, 공유 canonical/body hash/명시 upstream MySQL 사례 통과. 간접 연결은 코드 리뷰 검토 |
| 증거 누락·절단·해시 불일치·비허용 정책, 다중 관찰, 분류 변경 | `HELD`, 진단 이유 기록 | 기존 v4 증거·분류 회귀 통과. 다중 관찰은 구현/계약 정적 검토 |
| 중복 fingerprint/canonical 및 동시 v4/v3 override | MySQL 고유 claim으로 한 게시만 성공, 나머지 `HELD`/409 | 동시 v4 canonical 충돌 및 v4→v3 override 차단 MySQL 테스트 통과 |
| 동일 terminal 재제출, claim/outbox 실패 | 재게시 없음; 오류는 원자적 롤백 | 기존 terminal 재제출 통과. claim 중복과 기존 outbox 회귀 통과; 새 v4 outbox 강제 실패 전용 테스트는 없음 |
| 악의적 Markdown/URL 유사 문구 | 글 HTML에 새 링크·이미지·스크립트가 생기지 않음 | HTML 텍스트로만 남고 `<a>`·`<img>` 없음 확인 |
| V17 기존 데이터 backfill | 결정적 대표 claim, 기존 글 불변, 중복 재발행 차단 | 실제 MySQL V16→V17 중복 seed/대표/unique 검사 통과 |
| v2/v3 및 관리자 일반 게시 회귀 | 기존 계약 유지; 일반 게시 API는 claim 보장 범위 밖 | 전체 backend check, 관련 관리자 override MySQL 묶음 통과 |

## 필수 게이트

- [x] Story 33 MySQL Testcontainers 통합 테스트와 backend `check`: `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`, XML 24묶음/198개/실패·오류·건너뜀 0
- [x] frontend lint/typecheck/test/build: 7파일/41개 테스트 및 빌드 통과
- [x] generation-worker lint/typecheck/test/build: 10파일/88개 테스트 및 빌드 통과
- [x] Playwright 9/9 및 `docker compose config --quiet` 종료 코드 0
- [x] 독립 verifier **APPROVE**, 코드 리뷰 **APPROVE**, 적대적 입력·경합·이전 통합 검사
- [ ] PR 필수 검사와 병합 후 `prototype` 동기화

전체 `check` 첫 시도는 Windows Testcontainers MySQL 연결 핸드셰이크가 장시간 멈춰 중단했다. IPv4 우선 재시도에서는 테스트 정리 코드가 새 claim을 남기는 회귀를 발견하여 네 기존 테스트 묶음의 fixture를 고쳤다. 그다음 전체 검사에서는 기존 v4 `localhost` WireMock 수집이 간헐적으로 보류되어 숫자형 루프백 주소로 fixture를 통일했다. 최종 전체 `check`는 성공했다. Playwright는 별도 Next 개발 서버를 시작한 뒤 직접 실행해 9/9와 종료 코드 0을 확인하고 서버를 종료했다.

남은 한계: 정형 카드가 관찰 문구의 참을 보증하지 않는다. 공유 관계가 출처에 명시되지 않으면 숨은 재배포를 탐지할 수 없다. V17 claim은 v4 자동 및 v3 override에 적용되며 일반 관리자 게시 경로 전체의 중복을 보증하지 않는다. 새 v4 outbox 강제 실패와 승인 철회가 정확히 commit과 경합하는 동시성 시험은 별도로 추가하지 않았고, 서비스·트랜잭션 경계를 정적/기존 회귀 테스트로 확인했다.
