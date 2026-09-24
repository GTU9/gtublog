# Story 27 — 자동 생성 Markdown 검증 증거

기준 브랜치: `codex/generated-markdown-rendering` (`prototype`의 `d619d51`에서 분기). 이슈: [#82](https://github.com/GTU9/gtublog/issues/82). 실제 운영 배포와 과거 글 데이터 재작성은 수행하지 않았다.

## 수용 기준과 적대적 시나리오

| 시나리오 | 실제 검증 |
| --- | --- |
| 정상 자동 발행과 승인 가능한 보류 초안 | Spring/MySQL 통합 테스트가 두 경로 모두 제목, 목록, 인용, 코드, 강조, 수평선, 링크를 구조화 HTML로 저장하고 리비전과 공개 조회가 일치하는지 검사한다. |
| 위험한 생성 본문 | raw HTML·스크립트·이벤트 속성·`javascript:`/`data:` 링크·외부 이미지가 실행 가능한 DOM에 남지 않는지 확인한다. 이미지의 alt 텍스트는 보존한다. URL의 최종 허용 목록은 jsoup 정화기가 적용한다. |
| Unicode와 지시문 문자열 | 한글·이모지·본문 속 악의적 지시문을 일반 콘텐츠로 보존하고 HTML 실행 권한을 주지 않는지 확인한다. |
| 관리자 제목만 수정 | 화면이 보낸 평문형 HTML을 무시하고 기존 구조화 HTML을 보존하며, 새 리비전과 공개 응답이 같은지 확인한다. |
| 관리자 Markdown 수정 | 화면이 보낸 오래된/위험 HTML을 무시하고 Spring이 수정 Markdown을 다시 렌더링한다. 안전한 링크·목록·코드는 유지하고 `data:` 링크·외부 이미지는 제거한 뒤 글·리비전·공개 응답을 대조한다. |
| 수동 글 호환성 | 기존 PostService 수동 작성·수정 HTML 입력과 정화 계약은 변경하지 않는다. 전체 백엔드 회귀 검사에 포함한다. |

## 실행한 검증

- `backend\gradlew.bat mysqlCompatibilityTest --tests com.gtublog.automation.GeneratedMarkdownIntegrationTests --no-daemon --max-workers=1 --no-configuration-cache`: 실제 MySQL 통합 3/3 통과. 처음 `test --tests`를 사용했을 때 Docker 태그 테스트가 제외되어 “No tests found”가 나왔고, 올바른 `mysqlCompatibilityTest` 태스크로 재실행해 통과했다.
- `backend\gradlew.bat check --no-daemon --max-workers=1 --no-configuration-cache`: 통과. JUnit XML 합계 19 suites, 121 tests, 0 failures/errors. 종료 중 Quartz 스레드의 interrupt 로그가 한 번 있었지만 Gradle 검사와 테스트 결과는 성공이다.
- `pnpm --dir frontend lint`, `typecheck`, `test`, `build`: 관리자 화면 안내 변경 후 통과. 7 files, 39 tests.
- `pnpm --dir generation-worker lint`, `typecheck`, `test`, `build`: 변경 전 통과. 9 files, 81 tests. Story 27에서 worker 코드/계약은 변경하지 않았다.
- Playwright 단독 브라우저 검사: 최신 변경 후 8/8 통과. `E2E_MYSQL_PORT=13307`로 실행한 실제 Spring/MySQL/Next 전체 스택 E2E도 2/2 통과했다.
- `docker compose config --quiet`, `docker compose -f compose.prod.yaml config --quiet`, `git diff --check`: 통과.

## 독립 검토와 잔여 범위

설계 검토는 관리자 편집 왕복에서 서식이 사라지는 초기 BLOCK을 발견했다. Spring이 최초 `AUTOMATION` 리비전을 기준으로 생성 글을 구분하고 Markdown 변경 여부에 따라 저장 HTML 보존/재렌더링하도록 고친 뒤 재검토에서 BLOCK이 해소되어 WATCH로 전환됐다. 독립 코드 리뷰는 변경 파일에 실행 가능한 심각도별 지적 없이 승인했고, 독립 검증자는 MySQL 편집 회귀를 근거로 승인했다.

관리자 편집 중 미리보기는 여전히 간이 문단 표현이다. 화면에 이를 알리고 저장 직후 실제 Spring HTML을 다시 불러온다. 실시간 정확한 미리보기는 후속 UX 계약 작업이다. 이전 자동 글의 저장 HTML은 그대로 남아 표현이 혼재할 수 있으며 일괄 변환은 별도 승인·마이그레이션 범위다. 로컬 Docker/Testcontainers 포트 응답이 한 차례 지연되어 실행을 중단하고 다시 시작한 뒤 대상 및 전체 검사를 정상 완료했다. 이는 제품 단정이 아닌 로컬 테스트 환경 관측이다.
