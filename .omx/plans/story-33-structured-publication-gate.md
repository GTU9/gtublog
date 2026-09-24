# Story 33 — 출처 관찰 카드의 조건부 자동 발행

기준: `prototype` 병합 헤드 `bde0d1a`. 사용자는 **검증 가능한 정형 글만** 자동 발행하도록 선택했다. Story 32의 v4 `SOURCE_MENTION`은 두 저장 기사에 동일 문구가 존재하는지만 검증하고 항상 `HELD`로 끝난다. 이 스토리는 한 문구의 관찰 사실만 표현하는 카드형 글에 한해 양성 발행을 연다. 문구의 내용이 참이라는 주장은 하지 않는다.

## 공개 문구와 입력 경계

- 양성 발행 후보는 `automation-job-v4` 성공 제출에서 `SOURCE_MENTION` 관찰 **정확히 1개**, 서로 다른 스냅샷 정확히 2개, 유효한 taxonomy 선택이다. 2~3개 관찰은 계약상 유효해도 이번 범위에서는 이유를 기록하고 `HELD`다. v2/v3 자유 서술 초안, worker 제목·요약·Markdown, 관리자 v4 override는 자동 발행 대상이 아니다.
- Spring은 Story 32의 기사 증거 해시·완전성·정책·동일 실행·정확한 문구 위치 검증을 제출 트랜잭션에서 재실행한다. 외부 네트워크를 호출하지 않는다. 원문 문구를 제목이나 요약에 넣지 않는다. 제목과 요약은 고정된 귀속 문장, slug는 실행 ID에 기반한 결정적 값으로 기존 제목 기반 `uniqueSlug`와 분리한다. 본문은 “두 출처의 기사에서 다음 문구가 관찰됨”과 “문구의 사실 여부는 검증하지 않음”을 명시하고, 문구를 Markdown escape한 인용으로만 표시한다. 출처 두 곳은 기존 revision citation API에서 링크한다. Spring renderer의 HTML escaping·URL sanitization을 통과해야 한다.
- `app.automation.worker.schema-version`의 기본 v3는 유지한다. 운영자가 기존 승인·주제 설정을 검토하고 명시적으로 v4를 선택했을 때만 신규 작업이 양성 경로에 들어간다. active v2/v3 작업과 기존 v3 보류 의미는 그대로 유지한다.

## 양성 발행 정책

- 게시 트랜잭션에서 run 행과 job 행 잠금 뒤 주제 행을 잠그고 `publicationEnabled`를 재확인한다. 각 인용 스냅샷의 설정 source 행을 ID 오름차순으로 잠근다. MySQL REPEATABLE READ의 오래된 consistent snapshot을 신뢰하지 않도록 현재 출처 승인과 그룹 쌍 승인은 잠금/current read로 다시 읽는다. 스냅샷에 캡처된 `origin_approval_id`, revision, group ID가 현재 승인 행의 active/revision/source/host/group/source URL·type과 정확히 일치해야 한다. source가 현재 비활성화되어 있거나 URL·type이 변경되었거나 승인이 취소·재승인되었으면 보류한다.
- 두 스냅샷의 설정 source·실제 origin host·origin group이 모두 달라야 한다. 실행에 캡처된 그룹 쌍 승인 `(approval ID, revision, low/high group)`이 있고, 현재 같은 승인 행이 active이며 revision/주제/그룹 쌍이 정확히 일치해야 한다. 그룹 쌍 승인 취소와 게시 판정은 주제 행 잠금으로 직렬화한다. 출처 승인 취소/설정 변경과 게시 판정은 source 행 잠금으로 직렬화한다. 잠금 순서는 run → job → topic → source ID 오름차순이다.
- 같은 실행의 허용된 스냅샷 전체에서 동일 canonical URL, 본문 해시, 명시적 upstream URL의 공유 간선을 모두 구성하고, 두 인용 중 하나와 연결된 성분이 있는지 검사한다. 간접 관계를 기존 `relation()`의 첫 결과나 `SHARED_UPSTREAM` 간선만으로 축소하지 않는다. 두 인용 사이 또는 인용과 연결된 성분에 `SAME_HOST`, `SAME_CONFIGURED_SOURCE`, `SHARED_UPSTREAM`이 있으면 보류한다. v4 양성 경로의 잠금 순서는 run → job → topic → source ID 오름차순 → taxonomy이며, 현재 taxonomy-first 처리 순서는 재배치한다. 외부에 드러나지 않은 재배포 관계는 증명할 수 없으므로 진단·문서에서 편집상 독립성의 한계를 유지한다.
- 승인/계보/증거/분류/중복 조건이 하나라도 불충분하면 명확한 detail reason과 함께 `HELD`다. 양성만 `PUBLISHED`로 전이한다. terminal 동일 ID·digest 재전송은 게시·감사·outbox를 다시 만들지 않는다.

## MySQL 중복·원자성

- Flyway V17로 자동 발행 claim 테이블을 추가한다. `(claim_type, claim_hash)` 유일성은 v4 자동 발행과 v3 관리자 override 간 동일 fingerprint와 canonical URL 재사용을 MySQL에서 직렬화한다. 관리자 일반 게시 API는 이번 변경 범위 밖이며 전역 중복 보장이라고 문서화하지 않는다. 기존 post fingerprint와 revision 인용 canonical URL을 결정적인 대표 post 순으로 backfill하되 기존 글은 변경·삭제하지 않는다. archive/delete 후에도 claim은 유지해 자동 재발행을 막는다. canonical URL은 SHA-256 키와 원문 값 모두 보관해 충돌/운영 진단이 가능해야 한다.
- 신규 양성 게시와 기존 v3 관리자 override 모두 트랜잭션 안에서 fingerprint claim 및 인용 canonical URL claim을 획득한다. 기존 post/citation 조회는 명료한 사전 보류용으로 유지하지만 최종 경합 승자는 DB 유일성으로 결정한다. 같은 트랜잭션의 JPA `saveAndFlush` 유일성 예외를 잡아 계속 진행하지 않는다. JDBC의 명시적 충돌 감지 SQL로 claim을 획득하고, 실제 SQL 오류는 상위 트랜잭션을 롤백한다. claim 충돌은 새 게시를 만들지 않고 자동 경로는 `HELD`, 관리자 경로는 기존 409 의미를 유지한다. 경합에서 한 트랜잭션이 롤백되면 다른 트랜잭션이 정상 진행할 수 있어야 한다.
- post, taxonomy links, initial revision, 두 citation, `SUCCEEDED` run 상태와 `PUBLISHED` publication decision, audit, claim, 유일 outbox가 **하나의 MySQL 트랜잭션**에서 commit된다. outbox/중간 저장 실패는 모두 롤백한다. 캐시 재검증은 기존 post-commit outbox 소비자가 담당한다.

## 회귀·적대적 검증

1. 빈 MySQL V17 migration/Hibernate validation과 기존 데이터 backfill, fingerprint/canonical claim 유일성을 검사한다. 동시 v4 제출, v4 대 v3 수동 override, 동일 terminal 재전송에서 단 한 게시만 성공하는지 Testcontainers로 증명한다.
2. 현재 source 승인 또는 pair 승인 취소·revision 변경이 제출과 경합할 때 MySQL 잠금 순서에 맞는 직렬 결과만 허용한다. 실행 뒤 새로 승인한 쌍은 과거 실행에 소급되지 않는다. 같은 host/source/group, 공유 canonical/hash/upstream, 비허용·절단·해시 불일치 증거, 다중 관찰, taxonomy 변경을 각각 보류한다.
3. 양성 글은 고정 제목·요약·귀속 본문·2개 citation·taxonomy·initial revision·audit·outbox를 가진다. 악의적인 문구의 Markdown/HTML/URL 유사 구문이 실행 스크립트·이미지·링크가 되지 않으며 SSR metadata/RSS/sitemap에도 과장된 사실 주장이 나오지 않아야 한다. outbox 삽입 실패와 DB claim 충돌 시 post/decision/job terminal이 원자적으로 롤백되거나 결정된 보류 상태를 유지한다.
4. 기존 v2/v3 보류·수동 override, frontend/worker 계약, full backend check, frontend/worker lint·typecheck·test·build, Playwright, Compose, 독립 verifier·코드 리뷰·적대적 QA와 PR 필수 검사를 통과한다. 한국어 이슈→커밋→PR→`prototype` 병합 순서를 지킨다.

## 사전 실패 분석

- 단순 조회만으로 중복을 판단하면 동시 실행 두 개가 모두 게시할 수 있다. MySQL 유일 claim을 최종 권한으로 삼는다.
- 수집 시점 승인만 믿으면 철회 직후 게시될 수 있다. 승인 변경과 게시가 같은 topic/source 잠금 경계에서 직렬화되게 한다.
- 인용된 두 URL만 비교하면 간접 공유 upstream을 놓친다. 같은 실행의 허용 스냅샷 전체로 연결 성분을 검사한다.
- 문구를 원시 Markdown에 삽입하면 worker가 본문 표현을 바꿀 수 있다. Spring 고정 템플릿과 escape/sanitizer, hostile fixture로 제한한다.
- 기존 데이터에 이미 중복이 있을 수 있다. migration은 기존 글을 변경하지 않고 claim backfill에서 결정적인 대표만 기록하며 이후 중복 발행을 차단한다.
