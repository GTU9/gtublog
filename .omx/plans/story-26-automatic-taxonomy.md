# Story 26 — 생성 글 자동 분류와 원자적 발행

기준: `prototype` 병합 커밋 `7076404`; Story 23의 후속 누락 중 자동 분류. 요구 근거: `.omx/specs/deep-interview-automated-content-blog.md:61`, `.omx/plans/prd-automated-content-blog.md:228`, `.omx/plans/test-spec-automated-content-blog.md:29`. 현재 v2 생성 결과에는 분류가 없고 자동 발행은 `post_category`/`post_tag`를 쓰지 않는다.

## 원칙과 결정 동인

- Spring/MySQL이 분류 목록과 발행의 유일한 권한이다. 워커는 후보 ID만 선택하고 분류를 생성·수정하지 못한다.
- 기존 v2 계약과 저장된 작업을 파괴하지 않는다. 새 필수 필드는 `automation-job-v3` 계약에서만 요구한다.
- 원문·주장·중복 검증과 분류 검증이 모두 통과해야 발행한다. 글·리비전·출처·분류 연결·감사·outbox는 하나의 트랜잭션이다.
- 사용자에게 보이는 보류 이유와 재시도 경로를 남긴다. 잘못된 분류를 조용히 버리거나 무분류 글을 자동 공개하지 않는다.

상위 결정 동인: (1) 자동 글의 탐색성, (2) 워커 최소 권한과 근거 있는 발행, (3) 계약·재시도·기존 작업 호환성.

## 대안과 ADR

- A: v2 draft에 필수 분류 필드를 추가한다. 파일 수는 적지만 `additionalProperties:false`인 배포된 계약과 기존 워커를 깨므로 기각한다.
- B(선택): v3 계약에서 Spring이 작업 생성 시 관리자 분류 ID·slug·이름의 유한 목록을 고정하고 워커가 category ID 하나와 tag ID 1~5개를 선택한다. Spring이 작업 목록과 현재 DB를 재검증한다. 계약 변경은 크지만 권한 경계가 명확하다.
- C: topic slug를 카테고리로 추론한다. 워커의 실제 콘텐츠 분류가 아니고 다중 태그 요구를 만족하지 못해 기각한다.

결정: B. 장점은 기존 분류의 관리 권한·출처와 자동 글의 탐색 경로를 보존한다는 점이다. 비용은 v3 생산자·소비자·fixture와 rollout 동시 갱신이다. 기존 v2 계약 파일은 수정하지 않는다. 운영 전환 전에 원하는 v2 작업의 정상 종료를 끝낸다. 전환을 시작할 때 자동화 schedule과 모든 worker 프로세스를 중지하고, 구 백엔드의 worker claim/submit 유입을 차단한 뒤 이미 처리 중인 HTTP 요청이 끝날 때까지 기다린다. 모든 구 백엔드 인스턴스가 종료됐음을 확인한 후 새 백엔드를 배포·검증한다. 새 백엔드에서 v2 성공 제출도 분류 누락 이유로 HELD하며 저장된 draft를 보존하는 것을 확인하고서 v2/v3 워커와 schedule을 재개한다. 남은 PENDING/CLAIMED v2 작업은 실행 deadline 전에 재개되면 새 백엔드에서 완료·HELD로 처리한다. 중단 중 deadline이 지난 실행은 기존 복구 경로에서 FAILED가 되므로 관리자가 새 v3 실행을 시작한다. HELD 전용 재시도 API를 FAILED 실행에 적용하지 않는다. 이후 새 작업을 v3로 생성한다. 새 백엔드는 v2 claim 응답에 v3 필드를 추가하지 않는다. 새 워커는 v2·v3 claim을 인식해 이전 작업을 안전하게 종료한다. 운영 문서에 남은 v2 작업과 만료된 실행의 별도 회복 절차를 적는다. 전환 중 일시적인 작업 지연을 허용한다. 실제 운영 중지·배포는 별도 명시적 승인 없이는 실행하지 않는다.

## 수용 기준

1. 새 작업의 immutable request payload와 v3 claim에는 작업 시점에 관리자에게 승인된 category/tag 후보의 ID, slug, 표시 이름이 들어간다. 카테고리 최대 100개, 태그 최대 200개, 각 slug·이름 최대 120자이며 ID 오름차순으로 고정한다. 후보가 비었거나 상한을 넘으면 이유와 함께 실행을 HELD하고 워커 작업을 만들지 않는다.
2. v3 생성 결과는 category ID 하나와 tag ID 1~5개, 기존 제목·요약·본문·출처 ID를 포함한다. worker fake/provider 구조화 출력/검증/submit이 모든 필드를 함께 운반한다. 양의 정수·개수·필드 타입이 잘못된 제공자 출력은 계약/생성 실패로 처리해 실행을 FAILED하고 관리자가 새 실행을 시작할 수 있게 한다. 구조는 올바르지만 claim 밖 ID·중복 태그 등 정책상 무효인 선택은 worker가 조용히 삭제하거나 fatal 처리하지 않고 terminal draft에 담아 제출한다. Spring은 이를 저장한 뒤 이유가 있는 HELD로 처리한다. v3 JSON schema는 중복 ID를 구조 단계에서 거부하지 않아 애플리케이션 보류 경로로 보낸다. 직접 호출한 잘못된 JSON은 400이며 상태 변화가 없다.
3. Spring은 제출된 ID가 해당 job의 고정 후보와 현재 DB에 모두 존재하고 ID별 slug·이름이 일치하는지 검증한다. 분류 row를 category→정렬된 tag 순서로 `SELECT ... FOR UPDATE` 잠그고, 이 잠금을 게시 트랜잭션 종료까지 유지한다. 관리자 변경/삭제와 경합할 때 먼저 확정된 상태를 기준으로 검증하거나 기다렸다가 변경된 상태를 보고 HELD한다. 삭제·이름 변경 등 불일치는 명확한 HELD 사유로 남긴다. taxonomy 검증 실패로 공개 글·분류 링크·outbox가 생기지 않는다.
4. 성공 시 `post_category` 1건과 `post_tag` 1~5건이 글·초기 리비전·출처 연결·audit·outbox와 같은 MySQL 트랜잭션에서 저장된다. 공개 category/tag 페이지와 검색에서 글이 조회된다. 롤백/중복 제출/동시 제출에도 고아 링크나 중복 발행이 없다.
5. canonical terminal digest와 저장된 draft/held-run 상세에 taxonomy selection을 포함한다. 동일 terminal ID+payload 재전송은 같은 응답, taxonomy만 바뀐 재전송은 충돌한다. 관리자 override는 분류 누락·무효 draft를 발행하지 않는다.
6. v2 계약·fixtures는 그대로 유효하고 진행 중이던 v2 작업은 자동 무분류 발행하지 않는다. v3를 기본 설정으로 사용하며 호환성 전환을 문서화한다. 필요 없는 DB migration은 만들지 않는다.

## 구현 순서와 파일 소유

1. v3 JSON schema/fixtures, Java/TypeScript DTO 및 prompt/output schema를 함께 갱신한다. v2 fixture는 보존하고 digest parity를 먼저 검증한다.
2. Spring 작업 enqueue에서 bounded taxonomy catalog를 고정한다. 기존 `/api/v2/internal/generation-jobs` 전송 경로는 유지하되 `schemaVersion`으로 v2/v3 DTO를 구분한다. v3 claim만 고정 catalog를 반환하고 v2 claim 응답은 기존 필드 그대로 둔다. v3 worker claim 요청은 v2·v3 지원을 명시하고 backend는 해당 작업 버전에 맞는 응답만 보낸다. submit은 job catalog·잠근 현재 DB row를 검증한다. 보류 사유/운영 상세를 연결한다. taxonomy 관리자 update/delete가 발행과 경합할 때 DB row 잠금이 일관되게 적용되는지 확인한다.
3. 자동 발행 서비스가 검증된 ID만 받아 join을 게시 트랜잭션 내부에서 작성한다. 동일 경로의 관리자 override, outbox, cache 태그를 확인한다.
4. worker provider·runtime의 claim 검증/선택/terminal submit을 구현하고 fake provider, v2 legacy 동작을 결정적 테스트로 고정한다.
5. 관리자 자동화 보류 화면의 타입과 표시를 갱신해 선택 분류 및 보류 사유를 보여준다. 사전 v2 작업 종료→schedule·worker 중지 및 구 백엔드 worker API 차단→처리 중인 HTTP 요청 종료→구 백엔드 제거 확인→새 백엔드 v2 HELD 검증→워커·schedule 재개 순서를 운영 문서에 기록한다. 중단 중 deadline이 지난 실행은 FAILED로 복구되므로 새 v3 실행을 시작하는 절차와 테스트를 추가한다. 전체 검증·독립 설계/코드/UltraQA 판정을 받는다.

메인 에이전트는 계약/worker/프런트 관리자 표시와 통합, Git/GitHub를 소유한다. 개발 하위 에이전트는 backend production, 테스트 하위 에이전트는 backend regression, 검증 하위 에이전트는 읽기 전용 독립 확인을 소유한다. 공유 파일 충돌은 메인이 조정한다. 역할은 `executor`, `test-engineer`, `verifier`, `architect`, `critic`를 사용하되 사용자 지정 모델 `gpt-6-sol`과 현재 계정의 역할 모델 제약을 우선한다.

## 검증과 실패 사전 검토

- 단위/계약: v2·v3 schema fixtures, provider output, claim/submit 검증, taxonomy ID bounds, canonical digest parity, terminal retry/conflict, empty/oversized catalog.
- MySQL 통합: 분류 없는 실행 보류, 구조 오류 400/FAILED와 정책 오류 HELD의 구분, 존재/삭제/변경된 후보, 관리자 update/delete와 제출의 row-lock 경합, 원자적 join/rollback, 동일/경쟁 제출, 보류 초안/override, v2 legacy 전환과 deadline 만료 시 FAILED 복구, 공개 category/tag 검색.
- E2E: 관리자가 분류를 만든 뒤 가짜 워커가 글을 자동 생성해 category/tag 공개 경로에서 찾고 outbox revalidation을 확인한다. 기존 관리자/Playwright 흐름도 유지한다.
- 전체 게이트: `backend\gradlew.bat check`, frontend·worker lint/typecheck/test/build, `pnpm exec playwright test`, 전체 스택 E2E, 루트·운영 `docker compose config`, CI Security/CodeQL, 독립 reviewer와 verifier.
- 관측: taxonomy 보류 이유는 run 상태/감사/metrics에서 식별 가능하고 작업 payload·로그에 비밀이 포함되지 않는다.

사전 실패 시나리오: (1) enqueue 뒤 분류 삭제 또는 동시 변경 → row lock 순서에 따라 일관되게 게시하거나 HELD, 공개 부작용 0; (2) 재전송 때 taxonomy만 변경 → digest/terminal conflict, 기존 결과 유지; (3) 구 v2 워커/작업 혼재 → 전환 전에 원하는 v2 작업을 종료하고 worker·schedule 및 구 백엔드 worker API를 중지, 진행 중인 HTTP 요청 종료와 구 백엔드 제거를 확인한 후 새 백엔드 v2 HELD 정책으로 자동 무분류 발행 금지. deadline 전 작업은 새 백엔드로 이어가고 만료된 FAILED 실행은 새 v3 실행으로 회복한다. 각 시나리오를 통합/계약/전환 테스트로 증명한다.

## 중지 조건과 후속

모든 v3 생산자/소비자·fixtures와 rollback 증거가 맞고, 필수 게이트 및 독립 검토가 깨끗하며 GitHub 이슈→한국어 UTF-8 커밋→PR→필수 검사→`prototype` 병합이 완료돼야 이 스토리를 닫는다. Markdown 렌더링, 주장별 출처 검증, 보류 편집/폐기는 별도 후속 스토리다.
