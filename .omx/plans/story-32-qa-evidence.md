# Story 32 — 검증 및 적대적 QA 기록

## 안전 경계

`SOURCE_MENTION`은 저장된 두 기사에 같은 문구가 관찰된다는 사실만 뜻한다. 독립 출처의 확인, 숨은 재배포 부재, 그 문구가 주장하는 내용의 진실은 아직 검증하지 않는다. v4 결과가 양성이어도 실행은 `HELD`이고 게시물·리비전·outbox를 만들지 않는다.

## 검증할 시나리오

| 시나리오 | 기대 결과 | 증거 |
| --- | --- | --- |
| v4 자유 서술 제목·Markdown·HTML 혼입 | 계약 단계 400 또는 worker 출력 거부 | 계약/worker/Spring 테스트 예정 |
| 동일 실행의 허용된 완전 기사 두 곳에 같은 문구 | 검증 진단 양성, 실행은 `HELD`, 공개 부작용 0 | MySQL 통합 테스트 예정 |
| 타 실행 ID, 동일 ID 두 번, 차단된 source | 검증 음성 또는 계약 거부 | MySQL 통합 테스트 예정 |
| 증거 없음·절단·해시 불일치 | 검증 음성, 재수집 없음 | MySQL 통합 테스트 예정 |
| 부분 단어·대소문자·Unicode 공백·중복 관찰 | 결정적인 문구 판정 | 경계 fixture 예정 |
| v4 taxonomy 후보 변경, lease 만료, terminal digest 변조 | 기존 권한·충돌·보류 의미 유지 | MySQL/worker 테스트 예정 |
| 동일 terminal 재전송 | 최초 보류 결정과 진단 불변 | MySQL 통합 테스트 예정 |
| 관리자 조회와 수동 override | 문구·기사 원문 미노출, v4 override 거부 | API 통합 테스트 예정 |
| v2/v3 호환성과 v3 기존 보류 | 회귀 없음 | 전체 게이트 예정 |

## 실행 결과

- `backend\\gradlew.bat --no-daemon mysqlCompatibilityTest --tests com.gtublog.automation.V4StructuredEvidenceIntegrationTests`: 최초에는 테스트 canonical digest의 `draft:null` 누락 등으로 실패했다. 도우미와 v3 저장 형식 기대를 바로잡은 뒤 25/25 통과했다.
- 코드 리뷰에서 위험 문자 문구가 `job.submit` 이후에야 거부되는 문제가 발견되었다. 요청 DTO 경계에서 `<`, `>` 및 Unicode category C를 거부하고, 계약과 400/상태 불변 테스트를 보강했다. 수정 후 해당 v4 테스트와 기존 실패 테스트 2개의 좁은 MySQL 재실행은 통과했다.
- `backend\\gradlew.bat --no-daemon check`: 최초 전체 실행에서 MySQL 161개 중 기존 출처 수집 테스트 2개가 테스트용 WireMock 응답을 수집하지 못해 실패했다. 두 테스트는 v4 변경과 함께 좁혀 재실행하여 통과했다. 두 번째 전체 실행에서도 162개 중 v4 준비 단계에서 같은 출처 수집 실패가 1회 발생했다. 세 번째 전체 실행 `backend\\gradlew.bat --no-daemon --max-workers=1 check`는 성공했고 XML 기준 전체 23개 테스트 묶음, 181개 테스트, 실패·오류 0이었다. Windows localhost/WireMock 경로의 간헐 오류 가능성이 남아 있으나 저수준 IOException은 현재 스냅샷에 기록되지 않는다.
- `pnpm --dir frontend lint`, `typecheck`, `test`, `build`: 모두 통과. frontend 테스트 7파일/41개 통과.
- `pnpm --dir generation-worker lint`, `typecheck`, `test`, `build`: 모두 통과. worker 테스트 10파일/88개 통과.
- Playwright가 직접 시작한 Next 개발 서버의 Windows 종료가 멈춰 첫 실행은 중단했다. Next 서버를 별도 실행한 뒤 `node_modules\\.bin\\playwright.CMD test`로 재실행해 9/9 통과와 종료 코드 0을 확인했다. 검증 후 별도 서버를 종료했다.
- `docker compose config --quiet`: 종료 코드 0. Docker 사용자 구성 읽기 경고가 출력되었다.

독립 검증자와 코드 리뷰 모두 위험 문자 사전 거부 수정 후 **APPROVE**를 명시했다. 남은 안전 경계는 v4가 검증 양성이어도 `HELD`로 끝나며, 실제 자동 발행은 별도 스토리의 승인 revision·계보·중복 경쟁 검증을 통과해야 한다는 점이다.
