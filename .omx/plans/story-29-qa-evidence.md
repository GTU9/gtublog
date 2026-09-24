# Story 29 검증 증거

대상: GitHub 이슈 #86, `codex/claim-evidence-verification`. 검증 범위는 기사 증거 보존과 HTTP 200 경계이다. 정형 자동 발행은 이 스토리에서 열지 않았다.

## UltraQA 시나리오 행렬

App 환경에서 저장소의 WireMock→Spring→MySQL 통합 테스트와 Playwright를 동적 검증 표면으로 사용했다. 수집된 원문은 자동 발행 게이트나 worker 입력으로 새로 전달되지 않으므로 OMX 상태 중단·재개, 별도 프롬프트 주입은 이 변경의 실행 경로가 아니다.

| ID | 사용자/공격자 모델·의도 | 설정·명령 | 기대 신호 | 실제 결과·수정 | 정리 |
| --- | --- | --- | --- | --- | --- |
| N1 | 일반 기사, 1,000자 이후의 근거 보존 | WireMock RSS/기사→Spring 수집→MySQL `retainsNormalizedArticleEvidenceBeyondExcerptAndHashesTheStoredText` | 정규화 텍스트와 저장 prefix 해시 일치 | 통과 | fixture는 테스트 종료 시 정리 |
| A1 | 매우 긴 기사와 보충 평면 Unicode로 절단 경계 공격 | `truncatesArticleEvidenceWithoutSplittingUtf16SurrogatePair` | 16,000 UTF-16 한도, 고립 서로게이트 없음, 잘림 참 | 통과 | 테스트 데이터 정리 |
| A2 | 기사 영역이 없는 페이지·HTTP 503 | WireMock 수집 통합 테스트 | 저장 증거 없음 | 통과 | 테스트 데이터 정리 |
| A3 | 부분 응답 HTTP 206을 완전한 근거로 오인시키기 | `partialArticleResponseHasNoVerifiableEvidence` | 저장 증거 없음 | 검토 중 발견, HTTP 200만 허용하도록 수정 후 통과 | 테스트 데이터 정리 |
| A4 | 수집 뒤 원본 페이지 변경으로 과거 증거 바꾸기 | WireMock 원본 교체 후 MySQL 행 재조회 | 기존 증거 불변 | 통과 | 테스트 데이터 정리 |
| A5 | 자동 발행 보류를 새 필드로 우회하기 | v3 발행 서비스 경로 독립 검토 및 백엔드 전체 테스트 | 계속 `HELD` | 통과 | 새 영구 상태 없음 |
| A6 | 장시간 e2e 종료·성공 문자열만 믿기 | `pnpm e2e` 종료 코드와 8개 시나리오 확인 | exit 0, 8/8 | 첫 자동 서버 실행은 8개 녹색 뒤 종료 지연으로 중단; 별도 서버 재사용으로 재실행 exit 0·8/8 | 별도 서버 종료, 생성된 `next-env.d.ts` 복원 |

기존 수집 계약의 잘못된 JSON·worker 프롬프트 주입, OMX 상태 재개/취소는 이 스토리의 입력이나 상태 전이를 바꾸지 않아 신규 harness 대상에서 제외했다. 후속 v4 정형 검증 스토리에서는 이 항목을 계약·발행 공격 시나리오로 다시 포함한다.

## 요구사항 대응

| 수용 기준 | 증거 |
| --- | --- |
| 1,000자 이후 본문 보존·공백 정규화·원문 대소문자·SHA-256 | `SourceCollectionIntegrationTests.retainsNormalizedArticleEvidenceBeyondExcerptAndHashesTheStoredText` MySQL 통과 |
| 16,000 UTF-16 문자 상한 및 서로게이트 안전 절단 | `truncatesArticleEvidenceWithoutSplittingUtf16SurrogatePair` MySQL 통과 |
| 기사 없음·수집 실패·HTTP 206은 검증 증거 없음 | `pageWithoutArticleHasNoVerifiableEvidence`, 기존 503 케이스, `partialArticleResponseHasNoVerifiableEvidence` MySQL 통과 |
| 수집 후 원본 변경으로 기존 증거가 바뀌지 않음 | WireMock 응답 교체 후 기존 MySQL 행 유지 검사 통과 |
| 빈 MySQL 마이그레이션 및 Hibernate 매핑 | `backend\gradlew.bat --no-daemon check`의 MySQL Testcontainers 통과 |
| v3 자동 발행 보류, 관리자/worker 응답 미노출 | 독립 verifier의 코드 경로 검토 승인. `AutomationPublicationService`와 명시적 DTO/worker payload 변경 없음 |

## 실행 게이트

- `backend\gradlew.bat --no-daemon mysqlCompatibilityTest --tests com.gtublog.automation.SourceCollectionIntegrationTests`: HTTP 206 수정 후 성공.
- `backend\gradlew.bat --no-daemon check`: 성공, 5분 13초. XML 보고서 20개 suite, 138개 test, 실패·오류·건너뜀 0.
- `pnpm --dir frontend lint`, `typecheck`, `test`, `build`: 성공, frontend unit 39개.
- `pnpm --dir generation-worker lint`, `typecheck`, `test`, `build`: 성공, worker unit 81개.
- `pnpm e2e`: 별도 실행 중인 Next 개발 서버 재사용으로 성공, Playwright 8개. 최초 자동 webServer 방식은 테스트 8개가 모두 녹색이었으나 프로세스가 종료되지 않아 중단했고, 재실행 결과는 exit 0.
- `docker compose config --quiet`: 성공.
- `git diff --check`: 성공.
- 독립 verifier: HTTP 206 보강 포함 최종 승인, 미해결 차단 항목 없음.

## 남은 경계

16,000자 밖의 원문은 증거로 사용할 수 없다. 저장된 문구의 존재는 사실의 진실성이나 출처의 편집상 독립성을 증명하지 않는다. 일반 v3 생성 글의 자동 발행은 계속 보류한다. 후속 스토리에서 정형 사실 문법, 승인 출처 그룹, 공유 계보 차단 및 동시 중복 제약을 함께 검증해야 한다.

설계 리뷰는 `WATCH`를 기록했다. `source_snapshot` 일반 엔티티 조회가 기사당 최대 16,000자의 증거 텍스트를 함께 가져오므로 선택적 조회/분리 저장이 후속 성능 과제다. 무인 운영 전 원문 보존 기간·저작권 정책도 확정해야 한다. 두 항목은 계획 문서에 출시 전 게이트로 기록했고 이번 스토리의 보수적인 `HELD` 정책은 유지된다.
