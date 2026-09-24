# Story 28 검증 증거 — 출처 계보와 자동 발행 보류

기준 브랜치: `codex/claim-corroboration` (`prototype`의 `65150ed`에서 분기). GitHub 이슈: #84.

## 수용 기준별 확인

| 기준 | 증거 | 결과 |
| --- | --- | --- |
| 공유 canonical·정규화 본문·명시적 상위 출처를 보존하고 자동 공개를 보류 | `SourceLineagePublicationIntegrationTests`의 실제 MySQL 수집→worker 제출 테스트 | 통과 |
| 일반 링크와 `rel="recite"`/`rel="excited"`는 상위 출처로 승격하지 않고, 정확한 `cite` 토큰만 인정 | 동일 통합 테스트의 양성·음성 fixture | 통과 |
| 인용되지 않은 공유 출처는 진단에 남기되 무관한 인용 초안의 `SHARED_UPSTREAM` 사유를 만들지 않음 | 인용 A/B 및 미인용 C/D 테스트, 인용 A와 미인용 C 공유 테스트 | 통과 |
| 주장별 검증 증거가 없는 v3 초안은 `HELD`; 차단·중복은 먼저 판정하고 수동 발행 불가 | `SourceLineagePublicationIntegrationTests`, `GenerationTaxonomyIntegrationTests` | 통과 |
| 같은 실행의 terminal 재전송은 저장된 판단을 재사용 | `SourceLineagePublicationIntegrationTests` 재전송 테스트 | 통과 |
| 스냅샷 상한과 RSS 재배포 증거 보존 | `SourceCollectionIntegrationTests` | 통과 |
| 관리자 진단 및 안전한 텍스트 렌더링 | `e2e/admin-ui.spec.ts`, Playwright | 통과 |
| Flyway·Hibernate·실제 MySQL 및 전체 백엔드 회귀 | `backend\gradlew.bat --no-daemon check` | 통과: 단위 19개, MySQL 115개, 실패 0 |
| 프런트엔드 lint·typecheck·test·build | `pnpm --dir frontend` 각 명령 | 통과: 단위 39개 |
| 워커 lint·typecheck·test·build | `pnpm --dir generation-worker` 각 명령 | 통과: 단위 81개 |
| 브라우저·전체 스택·운영 복구 | Playwright 8개, `pnpm e2e:fullstack` 2개, `pnpm drill:backup-restore`, `pnpm drill:restart-recovery` | 통과 |
| Compose 구성과 패치 형식 | `docker compose config`, `git diff --check`, `node --check scripts/run-restart-recovery-drill.mjs` | 통과 |

초기 전체 스택 E2E 기본 포트 `13306`은 기존 로컬 MySQL 컨테이너가 사용 중이었다. 해당 컨테이너를 변경하지 않고 일회용 테스트의 포트를 `23306`/`28080`/`23001`로 분리해 재실행했고 2개 시나리오가 모두 통과했다. 재시작 훈련은 V13 마이그레이션 재검증과 보류 초안의 관리자 발행을 포함해 통과했다.

독립 코드 리뷰에서 `rel` 부분 문자열 오탐과 미인용 출처의 보류 사유 오분류 두 건을 발견해 수정했고, 각 회귀 fixture가 실제 MySQL에서 통과했다. 최종 독립 검증은 추가 Change Request 없이 승인했다.

## 남은 범위

현재 v3 계약에는 핵심 주장별 근거가 없어 **새 자동 공개는 보류**한다. 이는 안전한 중간 상태이며 주장 추출·실질적 지지 확인·독립 출처의 긍정 판정은 후속 스토리 범위다. 기존 자연어 `hold_reason`은 DB에 상태 식별자로 저장되므로 문구 변경은 호환 마이그레이션과 함께 별도 처리한다. MySQL 중복 발행 동시성 제약 강화도 후속 스토리다.
