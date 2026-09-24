# Story 30 — 검증 및 적대적 QA 기록

## 변경 범위와 판정

관리자는 주제별 출처 그룹과 `(설정 source ID, 실제 기사 originHost)` 승인을 기록·취소한다. 승인 근거와 취소 근거, revision, 감사 이력을 보존한다. 수집은 네트워크 요청을 마친 뒤 짧은 트랜잭션에서 설정 source 행을 잠그고 현재 URL·종류 및 활성 승인을 확인하여 성공한 스냅샷에 승인 ID/revision/그룹 ID를 고정한다. source URL·종류 변경과 취소도 같은 행 잠금으로 직렬화한다. v3 자동 발행의 보류 판정은 유지한다.

## 적대적 시나리오

| 시나리오 | 기대 결과 | 검증 경로 |
| --- | --- | --- |
| 관리자 외 권한으로 승인 생성 | 401/403 | `OriginApprovalIntegrationTests` |
| 같은 source+host 동시 승인 | 하나만 성공, 다른 요청은 409 | MySQL 유일 제약 및 동시 요청 테스트 |
| 오래된 revision 또는 반복 취소 | 409, 이전 근거 유지 | MySQL 통합 테스트 |
| scheme·port·path·wildcard, 다른 주제의 그룹 | 400, 승인 레코드 없음 | MySQL 통합 테스트 |
| RSS 피드와 실제 기사의 호스트가 다름 | 실제 기사 호스트만 정확히 승인·표시 | WireMock→MySQL 및 Playwright 테스트 |
| 수집 뒤 승인, 승인 뒤 취소 | 과거 스냅샷은 소급 변경되지 않고 새 수집은 미승인 | MySQL 통합 테스트 |
| source URL 변경과 증거 캡처 경합 | source 행 잠금, 새 설정에서 이전 승인 불인정 | 코드 검토 및 MySQL 통합 테스트 |
| 승인 두 개만으로 자동 발행 시도 | v3는 계속 `HELD` | 기존 발행 통합 테스트 및 게이트 코드 검토 |

## 실행 증거

- Story 30 MySQL targeted: `OriginApprovalIntegrationTests` 7개 통과. 빈 MySQL에서 Flyway V15 적용과 Hibernate validation을 확인했다.
- 프런트엔드: lint, typecheck, Vitest 40개, production build 통과.
- Playwright: 9개 통과. 새 UI 테스트는 RSS 피드 호스트와 기사 호스트가 다른 mock에서 관측 호스트 제안, 409 안내, revision 기반 취소를 확인한다.
- generation-worker: lint, typecheck, Vitest 81개, build 통과.
- `docker compose config --quiet`와 `git diff --check` 통과.
- 전체 `backend/gradlew.bat --no-daemon check`: 최종 코드에서 `BUILD SUCCESSFUL` (21 suites, 145 tests, 실패·오류·건너뜀 0).
- 독립 verifier `APPROVE`. 독립 코드 리뷰에서 발견한 RSS source 매핑과 승인 증거 저장 fallback 두 건을 수정했고, 재검토에서 잔여 코드 finding 0건이다.

## 남은 출시 경계

이 스토리의 승인은 출처 정책 기록이며 출처 간 독립성이나 글의 사실성을 자동으로 증명하지 않는다. 그룹 쌍 독립성 승인, 정형 주장 검증·Spring 렌더링, 공유 계보 veto와 동시 중복 차단이 끝나기 전에는 자동 발행을 켜지 않는다. Story 29의 근거 본문 보존 기간과 대량 조회 경로도 무인 운영 전에 결정해야 한다.
