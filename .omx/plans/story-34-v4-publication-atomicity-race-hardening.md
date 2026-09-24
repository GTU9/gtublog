# Story 34 — 정형 관찰 발행의 원자성·승인 경합 실증

기준: `prototype` 병합 헤드 `6e94f6d`. Story 33 QA에서 정형 v4 양성 발행의 outbox 강제 실패 및 승인 철회와 제출의 정확한 경합이 MySQL 통합 테스트로 실증되지 않은 상태를 이어받는다. 사용자가 선택한 “검증 가능한 정형 글만 자동 발행” 정책은 유지한다.

## 범위와 성공 기준

- v4 양성 제출 중 outbox 삽입을 강제로 실패시켜 post, revision, citation, taxonomy, claim, publication decision, audit, run/job terminal 상태가 함께 롤백되는지 검증한다. 장애 제거 후 같은 terminal 제출이 한 번만 정상 발행되는지도 확인한다.
- 출처 승인 또는 그룹 쌍 승인 철회가 v4 제출과 경합할 때 잠금 순서에 따라 직렬 가능한 결과만 발생하고, 철회가 먼저 확정된 경우 공개 부작용 없이 `HELD`가 되는지 실제 MySQL 동시성 테스트로 확인한다. 시간 지연에만 의존하지 않고 명시적 잠금·동기화를 사용한다.
- 테스트가 결함을 발견하면 관련 Spring 서비스의 최소 수정과 회귀 테스트를 같은 변경에 포함한다. 새 공개 계약, 새로운 자동 발행 유형, 일반 관리자 게시 정책은 변경하지 않는다.
- backend 전체 `check`, frontend/worker lint·typecheck·test·build, Playwright, Compose, 독립 verifier·코드 리뷰·적대적 QA를 통과한다. 한국어 이슈→커밋→PR→`prototype` 병합 순서를 지킨다.

## 실패 분석

- outbox 실패 이후 claim이 남으면 재시도가 영구 보류될 수 있다. 실패 전후 모든 테이블과 재시도 결과를 검사한다.
- 단순 동시 호출은 원하는 교착 위치를 보장하지 않는다. 승인 변경과 발행이 공유하는 topic/source 잠금을 명시적으로 잡아 두 순서를 각각 관찰한다.
- 테스트용 DB 트리거가 다른 테스트를 오염시키면 잘못된 실패를 만든다. 반드시 `finally`에서 제거하고 테스트 데이터를 매번 정리한다.
