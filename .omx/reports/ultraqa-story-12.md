# Story 12 UltraQA 보고서

## 범위

- 소스 수집 리다이렉트 체인 전체에 단일 단조 시계 deadline 적용
- 만료된 `RUNNING` 자동화 실행과 연결 생성 작업의 원자적 복구
- Quartz JDBC JobStore/Flyway 스키마 및 클러스터 설정 검증
- 작업 claim, heartbeat, submit, recovery 경합의 잠금 순서와 단일 승자 보장

## 적대적 시나리오 결과

| 시나리오 | 결과 | 증거 |
| --- | --- | --- |
| 다단계 리다이렉트 누적 지연이 전체 제한시간을 초과 | 통과 | `PinnedSourceHttpClientTests` 누적 지연 테스트 |
| 느린 본문, 과대 본문, TLS 실패, DNS 고정/SSRF 우회 | 통과 | `PinnedSourceHttpClientTests` 전체 통과 |
| 만료된 고아 실행을 한 번만 실패 처리 | 통과 | MySQL `AutomationPipelineIntegrationTests` |
| recovery와 hold/enqueue/worker submit 경합 | 통과 | MySQL 동시성 통합 테스트 |
| 두 worker가 동일 작업을 동시에 claim | 통과 | 단일 claim 및 단일 감사 이벤트 검증 |
| 10개 초과 만료 작업이 정상 작업을 가림 | 통과 | 11개 만료 작업 선행 후 정상 작업 claim 검증 |
| worker lease가 실행 deadline을 초과 | 통과 | claim/heartbeat deadline 상한 검증 |
| Quartz 영속 JobStore와 클러스터/복구 trigger | 통과 | MySQL 통합 테스트 및 빈 DB Flyway/Hibernate 검증 |
| 전체 회귀/빌드/정적 검사 | 통과 | `pnpm quality` (2026-06-30, 162.2초) |

## 전체 게이트

- Backend Gradle `clean`, `check`, `bootJar`: 통과
- MySQL 8.4 Testcontainers 호환성/동시성 테스트: 통과
- Frontend lint, typecheck, 7 tests, production build: 통과
- Generation worker lint, typecheck, 6 tests, build: 통과
- Playwright: 5/5 통과
- Docker Compose config: 통과
- `git diff --check`: 오류 없음 (Windows CRLF 안내만 존재)
- 독립 코드 리뷰: `APPROVE`
- 독립 아키텍처/보안 검증: `CLEAR`

## 잔여 위험

- JVM DNS 조회 자체는 플랫폼의 blocking resolver에 의존하므로 병적인 DNS 정지 시간을 강제로 중단하지 못할 수 있다. 조회가 반환된 뒤에는 전체 deadline을 즉시 재검증한다.
- 복구 sweep 중 개별 `REQUIRES_NEW` 처리에서 예외가 발생하면 해당 sweep의 나머지 후보는 다음 주기에 재시도된다. 상태는 보존되며 조용히 누락되지 않는다.

## 판정

**PASS** — Story 12의 보안, 동시성, 복구, 회귀 조건을 충족한다.
