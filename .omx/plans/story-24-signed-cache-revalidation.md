# Story 24 — 서명된 발행 캐시 갱신과 outbox 복구

이슈 #76. Story23이 PR #75로 prototype에 병합된 뒤 시작한다. 기존 PRD 230 및 test-spec 57–60 요구를 구현하며 새 제품 기능이나 운영 의존성을 추가하지 않는다. 독립 아키텍처 검토와 적대적 계획 검토에서 APPROVE 판정을 받았다.

## 범위

- Next POST /api/revalidate와 Spring outbox 전달을 동일한 버전 계약으로 연결한다.
- HMAC-SHA256 서명: timestamp + newline + eventKey + newline + raw UTF-8 body. timestamp는 epoch seconds, eventKey는 UUID, signature는 lowercase hex. 서버 비밀만 사용하고 공개 번들에 넣지 않는다. 5분 window를 초과하거나 미래로 30초 넘으면 거부한다.
- body는 version=1, eventKey, postId, slug, tags 및 paths. 허용하는 공개 경로·태그만 처리한다. 글/list/taxonomy/archive/RSS/sitemap의 기존 캐시만 무효화한다.
- 동일 유효 이벤트의 재전송은 멱등 캐시 무효화로 처리하고 재발행하지 않는다. 인증 없는 요청/잘못된 서명/낡은 timestamp/임의 admin 경로/미등록 tag는 거부한다.
- Spring은 원래 발행 트랜잭션에 outbox만 저장한다. 별도 전달자가 짧은 claim 트랜잭션, 트랜잭션 밖 bounded HTTP, 별도 completion 트랜잭션을 실행한다. 전달 lease와 CAS로 수동/주기 처리 경합 및 중단 복구를 보장한다.
- availableAt 도달한 이벤트만 처리한다. 최대 5회, 지수 backoff capped, deadline 5초. max attempts 후 DEAD_LETTER, 안전한 이유 기록. 구성 누락은 성공으로 위장하지 않는다.
- 기존 수동 발행/수정/복구/보관/삭제 경로도 공개 상태 변화를 캐시 갱신에 연결한다. 이전 slug 캐시까지 제거해야 한다.
- 수동 mutation 계약: `publish`는 `POST_PUBLISHED` 이벤트를 같은 DB 트랜잭션에 기록한다. `update`와 `restoreRevision`은 변경 전 공개 상태일 때 각각 `POST_UPDATED`, `POST_REVISION_RESTORED`를 기록한다. `archive`와 `delete`는 변경 전 공개 상태일 때 각각 `POST_ARCHIVED`, `POST_DELETED`를 기록한다. `restore`는 삭제 글을 초안으로 복원하므로 이벤트를 기록하지 않는다. 초안만의 작성·수정·복구·보관·삭제도 기록하지 않는다. 각 mutation은 변경 전 공개 여부와 slug를 먼저 캡처하고, 공개 상태가 변경 전후 어느 쪽이든 참인 경우 old/new slug 경로와 `public-posts` 태그를 단일 이벤트에 담는다. 기존 자동 발행은 동일한 outbox 형식의 `POST_PUBLISHED`를 유지한다.
- `AutomationAdminService.processOutbox()`의 현재 `@Transactional`도 제거하거나 분리해 HTTP 동안 DB transaction이 없음을 통합 테스트로 검증한다. claim/completion은 별도 transaction owner가 맡는다.
- 상태는 `PENDING → IN_FLIGHT → DELIVERED/PENDING/DEAD_LETTER`, HTTP timeout 5초, lease 30초, claim 소유 UUID를 둔다. claim 시 attempt 증가, completion은 소유 토큰 CAS; 만료 claim은 재시도 대상이다.
- 신규 migration은 기존 outbox에 `attempt_count INT NOT NULL DEFAULT 0`, `claim_owner CHAR(36) NULL`, `lease_expires_at DATETIME(6) NULL`, `failure_reason VARCHAR(255) NULL`을 추가하고 due 조회용 `(delivery_status, available_at, lease_expires_at, id)` 인덱스를 둔다. claim 대상은 `PENDING AND available_at <= now AND attempt_count < 5` 또는 `IN_FLIGHT AND lease_expires_at <= now AND attempt_count < 5`이고, `id + expected status + due + attempt_count` 조건부 UPDATE로 소유권을 획득한다. 완료 UPDATE는 `id + claim_owner + IN_FLIGHT` CAS다. 다섯 번째 claim 직후 crash가 난 경우 lease 만료 후 `IN_FLIGHT AND attempt_count >= 5`를 `DEAD_LETTER`로 바꿔 영구 잔류를 막는다.
- 서명 timestamp는 매 전송마다 갱신한다. 헤더와 본문의 eventKey 일치, body 16KiB 이내, paths/tags 각 32개 이내, 상수시간 digest 비교를 강제한다.
- 운영자 진단에 DEAD_LETTER, attempt, next available, secret 없는 safe failure reason을 보여준다. 기존 unprocessed JSON은 event_key/aggregate_id/slug를 이용해 version1 전송 body로 변환하며 원본 JSON을 유지한다.
- 환경 변수는 backend `AUTOMATION_REVALIDATION_SHARED_SECRET`, Next 서버 `GTUBLOG_REVALIDATION_SHARED_SECRET`을 매칭한다. 둘 다 외부 구성의 32바이트 이상 고엔트로피 비밀을 요구하고 production placeholder를 거부한다. polling 30초, backoff 시작 30초/최대 15분이다.
- `compose.prod.yaml`은 Next frontend에 `GTUBLOG_REVALIDATION_SHARED_SECRET`을 server-only 환경 변수로 주입한다. Next route는 값 누락·placeholder를 fail-closed 처리하고 `NEXT_PUBLIC_*` 접두사의 비밀은 사용하지 않는다. 개발·테스트 설정에는 문서화된 placeholder만 두고 실제 배포 비밀은 외부에서 주입한다.
- fetch와 tag 대응: 공용 list/search/category/tag/archive/sidebar/detail/related 및 RSS/sitemap에 공용 `public-posts` tag를 둔다. 변경 이벤트는 이 tag와 실제 경로를 무효화한다. 첫 단계 broad tag를 사용해 이전/새 slug·taxonomy 경로 누락을 피한다.
- Next 16.3.6의 `revalidateTag("public-posts", { expire: 0 })`로 다음 요청에서 새 데이터를 강제하고, 동적 페이지·RSS·sitemap에는 필요한 `revalidatePath`를 함께 호출한다. `cache: "no-store"` 경로는 tag 캐시 대상이 아니므로 현재의 즉시 신선도를 유지하면서 테스트에서 변경 후 수렴을 확인한다. 계약의 `slug`는 변경 후 canonical slug이고, `paths`는 변경 전·후 URL 무효화 범위다.
- Flyway 신규 migration, 계약 fixture, docs/config을 함께 변경한다.

## 대안과 검증

단순 endpoint만 추가하면 transaction/재시도 결함이 남는다. 외부 큐 도입은 불필요하다. 기존 MySQL outbox와 스케줄러를 활용한다.

MySQL: 원자적 발행, Next503 후 발행 유지, due-time, lease race, retry recovery, 5번째 claim crash의 dead-letter 복구, no network transaction. Frontend: 서명/시간/경로/본문 크기/중복/idempotency 단위 테스트. Cross-service: 먼저 실제 Next 캐시를 채운 뒤 publish·공개 글 내용/slug 수정·revision 복구·archive·delete를 수행한다. 전달 장애/Next 재시작 후 `/posts/old`, `/posts/new`, list/search/category/tag/archive/related/RSS/sitemap이 갱신되는지 검사한다. 잘못된 서명과 재전송도 검사한다. 변경되는 생산자/소비자 fixture는 양쪽에서 동일 signature 값으로 검증한다.

자동 출처 수집/분류/claim 검증/Markdown/보류 편집은 후속 story로 남는다.
