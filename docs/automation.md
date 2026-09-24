# 자동화 운영 가이드

## 런타임 경계

- 스케줄의 최종 기준 시스템은 Spring Boot입니다.
- Quartz는 도메인 `automation_schedule` 레코드를 기준으로 예약 실행을 구동합니다.
- generation-worker는 작업을 claim/submit만 할 수 있으며, 직접 발행할 수 없습니다.
- 발행 상태는 MySQL 트랜잭션 안에서 글, 리비전, 출처 인용, 감사 로그, 아웃박스 이벤트가 함께 커밋될 때만 확정됩니다.

## 관리자 진단 화면

관리자 자동화 화면에서는 아래 항목을 확인할 수 있어야 합니다.

- 상태별 최근 실행 수
- 대기 중인 generation job 수
- 발행 outbox 이벤트의 pending / delivered 수
- 최근 hold reason
- 보류된 source snapshot 수

같은 요약 정보는 `GET /api/v1/admin/automation/diagnostics`에서도 확인할 수 있습니다.

## 복구 흐름

만료된 `RUNNING` 실행은 Quartz 유지보수 작업이 회수합니다. 실행은 generation job이 커밋되기 전까지는 더 짧은 pipeline lease를 사용하고, handoff 이후에는 절대 최대 실행 시간을 사용합니다. 경쟁 상태가 생길 수 있는 모든 전이는 generation job보다 먼저 run을 잠그며, 복구 단계에서는 pending 또는 claimed job을 취소하고 run을 `FAILED`로 전환하며 lease를 비우고 시스템 감사 이벤트 1건을 남깁니다. 늦게 도착한 worker submit은 이 전이 이후 발행을 완료할 수 없습니다.

1. 가장 최근 run 상세와 hold 또는 failure reason을 확인합니다.
2. source 접근성이나 corroboration 부족으로 막힌 경우, source 구성을 수정하거나 나중에 다시 실행합니다.
3. 콘텐츠는 발행되었지만 cache propagation이 실패한 경우, 관리자 화면 또는 `POST /api/v1/admin/automation/outbox/process`로 pending outbox 이벤트를 재처리합니다.
4. worker가 실패한 경우, run 감사 이력을 확인하고 worker를 재시작한 뒤 새 manual trigger로 다시 실행합니다.
5. `RUN_DEADLINE_EXPIRED`의 경우 worker health와 source latency를 확인한 뒤 새 idempotency key로 재실행합니다. 만료된 기존 run은 재사용하지 않습니다.

### 런타임 제어 변수

- `AUTOMATION_RUN_PIPELINE_LEASE_DURATION`: generation-job handoff 전 최대 허용 시간
- `AUTOMATION_RUN_MAX_DURATION`: handoff 이후 절대 최대 실행 시간
- `AUTOMATION_RUN_RECOVERY_INTERVAL`: 만료 실행 sweep 주기

## 메트릭

Prometheus 스크레이프 엔드포인트는 `/actuator/prometheus`입니다.

현재 커스텀 metric family:

- `gtublog_auth_events_total`
- `gtublog_automation_generation_jobs_total`
- `gtublog_automation_source_snapshots_total`
- `gtublog_automation_publication_decisions_total`
- `gtublog_automation_publication_duration_seconds`
- `gtublog_automation_outbox_deliveries_total`
- `gtublog_automation_run_recoveries_total`

권장 대시보드 분해 기준:

- `action` 기준 auth event rate
- `outcome`, `reason` 기준 publication decision 분포
- `source_type` 기준 source snapshot 결과 분포
- outbox retry / delivery 추세
- publication duration histogram

## Generation worker 런타임

generation-worker는 별도로 배포되는 non-root Node 프로세스입니다. `pnpm --dir generation-worker build`로 빌드하고, `pnpm --dir generation-worker start` 또는 `start:once`로 실행합니다. worker는 `X-Worker-Token`을 사용해 `/api/v2/internal/generation-jobs`하고만 통신합니다.

v2 terminal request는 UUID와 canonical UTF-8 JSON의 SHA-256 digest를 함께 전달합니다. Spring은 digest를 다시 계산하고 publication side effect와 함께 terminal identity를 커밋합니다. 같은 identity와 digest로 재시도하면 이미 커밋된 결과를 반환하고, terminal transition 충돌이나 digest mismatch는 `409`를 반환합니다.

### 자동 분류 계약 전환

새 generation job은 `automation-job-v3`를 사용합니다. Spring은 작업 생성 시 관리자 카테고리와 태그 후보를 고정하여 claim에 전달하고, worker는 카테고리 ID 하나와 태그 ID 1~5개를 선택합니다. Spring은 제출된 선택이 고정 후보와 현재 분류 데이터에 모두 맞는지 확인한 후에만 발행합니다. 분류가 없거나 변경되었거나 허용 목록 밖이면 생성 초안을 보존하고 실행을 `HELD`로 둡니다. 구조가 깨진 provider 출력은 `FAILED`이며 관리자는 새 실행을 시작합니다. 기존 v2 작업도 분류 없이 자동 발행하거나 관리자 override로 공개할 수 없습니다.

운영 전환에서는 먼저 종료하려는 v2 작업을 끝냅니다. 이후 자동화 schedule과 **모든** worker 프로세스를 중지하고, 구 백엔드에 대한 worker claim·submit 유입을 차단합니다. 이미 처리 중인 HTTP 요청이 끝난 것을 확인하고 구 백엔드 인스턴스를 모두 제거한 뒤 새 백엔드를 배포합니다. 새 백엔드에 v2 성공 결과를 제출하는 시험에서 초안이 저장되고 `HELD`가 되는지 확인한 후 v2/v3 worker와 schedule을 재개합니다. 일시 중단 중 남은 v2 작업은 실행 deadline 전에 재개되면 새 백엔드에서 처리되어 `HELD`가 됩니다. deadline이 지나 `FAILED`로 복구된 실행은 `HELD` 전용 재시도 API를 쓰지 않고 새 v3 실행을 시작합니다. 실제 운영 중지·배포에는 별도 승인이 필요합니다.

### 자동 생성 Markdown 표시

새로 자동 발행되거나 보류 초안을 승인한 글은 Spring이 Markdown 구조를 HTML로 렌더링하고 정화한 결과를 `contentHtml`에 저장합니다. 생성 초안의 외부 이미지는 공개 글에 싣지 않고 대체 텍스트만 남깁니다. 기존에 발행된 글의 저장 HTML은 바꾸지 않으므로 일부 오래된 글에서는 Markdown 기호가 문단 텍스트로 보일 수 있습니다. 저장된 `contentMarkdown`과 `contentHtml`을 비교하면 이전 표현을 확인할 수 있습니다. 과거 글의 일괄 재처리는 이 배포에 포함하지 않습니다.

자동 생성 글을 관리자가 수정할 때는 최초 `AUTOMATION` 리비전으로 출처를 판단합니다. Markdown을 바꾸지 않은 저장은 기존 HTML을 유지하고, Markdown을 바꾼 저장은 Spring이 같은 렌더러로 다시 정화합니다. 관리자 화면의 편집 중 미리보기는 간이 표현이며, 저장 후 다시 불러온 HTML이 공개 결과입니다. 수동 작성 글의 HTML 입력 방식은 그대로 유지됩니다.

### 출처 계보와 검증 대기

자동 발행은 출처 호스트 수만으로 독립성을 판단할 수 없습니다. 재배포 페이지와 같은 상위 보고서를 가리키는 출처는 하나의 근거로 취급해야 하며, 글의 제목·요약·본문에 있는 핵심 주장마다 실질적인 지지가 확인되어야 합니다. 현재 v3 생성 결과에는 주장별 검증 증거가 없으므로 새 실행의 자동 공개를 보류하고 관리자에게 근거와 사유를 표시합니다. 이미 발행한 글과 완료된 실행은 재작성하지 않습니다. 허용된 보류만 Story 17의 인증·감사된 수동 발행 절차를 따르며, 출처 차단이나 중복은 수동 발행할 수 없습니다. 관리자 재시도는 새 실행에서 출처를 다시 수집하므로 이전 보류 판단을 지우지 않습니다. 주장별 검증과 자동 발행 재개는 후속 스토리의 검증 완료 전까지 수행하지 않습니다.

### 기사 출처 승인 기록

관리자는 자동화 화면에서 주제별 출처 그룹을 만들고, 설정된 출처와 **실제 수집된 기사 응답의 정확한 호스트**를 그룹에 승인할 수 있습니다. 피드 주소의 호스트 승인만으로 피드가 연결한 다른 기사 호스트가 승인되지는 않습니다. 승인과 취소에는 근거를 남기며, 변경 이력은 관리자 감사 로그에 기록됩니다. 출처 URL이나 종류를 변경하면 기존 승인이 무효화되므로 새 설정에 맞는 기사 호스트를 다시 확인하고 승인해야 합니다.

새로 수집한 스냅샷은 수집 당시 유효한 승인 ID, revision, 그룹 ID만 고정해서 기록합니다. 나중에 승인하거나 취소해도 과거 스냅샷은 변경되지 않습니다. 승인되지 않은 호스트와 과거 스냅샷은 이 증거가 없습니다. 그룹이 서로 다르거나 호스트가 다르다는 사실만으로 독립성을 인정하지 않으며, 이 승인 기능만으로 자동 발행을 재개하지 않습니다. 별도의 그룹 간 독립성 승인, 정형 주장 검증, 공유 계보 차단, 중복 방어가 완성될 때까지 v3 생성 글은 계속 보류됩니다.

관리자는 같은 주제에 속한 서로 다른 출처 그룹 두 개의 편집상 독립성을 별도로 승인·취소할 수 있습니다. 승인에는 원래 근거와 취소 근거, revision을 남기고 실행이 새로 시작될 때 활성 승인 목록을 그 실행의 정책 기록으로 고정합니다. 실행을 같은 idempotency key로 다시 요청해도 기존 기록은 바뀌지 않으며, 새 재시도 실행은 새 시점의 정책을 기록합니다. 실행 상세에서 이 정책 기록을 확인할 수 있습니다. 그룹 쌍 승인 역시 기사 내용의 진실이나 알려지지 않은 재배포 관계를 보증하지 않습니다. 후속 자동 발행 게이트가 현재도 유효한 source-host 승인과 그룹 쌍 승인, 공유 계보 및 주장 증거를 모두 확인하기 전에는 v3 보류를 해제하지 않습니다.

### 정형 출처 관찰의 조건부 발행

선택적인 `automation-job-v4` 계약은 자유 서술 초안 대신 `SOURCE_MENTION` 관찰과 taxonomy 선택만 제출합니다. Spring은 같은 실행에서 저장한 허용 기사 증거가 완전하고 해시가 맞는지 먼저 확인한 뒤, 두 스냅샷의 기사 텍스트에 제출 문구가 각각 실제로 나타나는지 검사합니다. 이 검사는 **문구의 존재**만 확인하며 그 문구의 사실성이나 출처의 독립성을 보증하지 않습니다. 관리자 진단에는 문구 해시와 스냅샷 ID, 판정 이유만 노출합니다.

v4 결과에 관찰이 정확히 하나이고 두 인용의 저장 기사에서 문구가 확인되면, Spring은 게시 직전에 주제 발행 설정, 활성 출처, 현재 출처 승인 ID·revision·URL·종류·호스트·그룹, 실행에 캡처된 그룹 쌍 승인, 분류, 공유 계보를 다시 확인합니다. 같은 설정 출처·호스트·그룹이거나 canonical URL·본문 해시·명시적 상위 출처를 공유하는 관계가 있으면 보류합니다. MySQL 고유 claim은 v4 자동 발행과 v3 관리자 override가 동일 fingerprint 또는 canonical URL을 동시에 게시하지 못하도록 합니다. 중복, 철회 또는 근거 부족은 이유를 기록하고 `HELD`로 끝납니다.

모든 게이트를 통과한 v4 실행은 Spring이 고정한 제목·요약과 안전하게 표시한 문구를 사용해 **출처 관찰 카드**를 발행합니다. 카드에는 두 기사에서 해당 문구가 관찰되었다는 사실과 문구의 사실 여부를 검증하지 않았다는 설명, 두 인용이 담깁니다. worker가 제목·본문이나 발행 결정을 지정할 수 없습니다. 게시물·첫 리비전·인용·분류·감사·중복 claim·캐시 outbox는 실행 성공과 함께 하나의 MySQL 트랜잭션에서 저장합니다. v4 결과에는 관리자의 기존 자유 서술 초안 override를 적용하지 않습니다. 새 작업의 기본 계약은 v3로 유지하며, v4는 운영자가 명시적으로 선택해야 합니다. 이 판정은 숨은 재배포 관계나 인용 문구의 진실성을 보증하지 않습니다. 일반 수동 게시 API는 자동화 claim 보장의 범위 밖입니다.

Codex SDK production 실행은 의도적으로 동결되어 있습니다. 현재 `GENERATION_CODEX_CANARY_ATTESTATION_PATH`의 v1 JSON과 `artifactDigest` 일치는 **이미지 동일성 확인일 뿐 운영 승인 근거가 아닙니다**. host mount JSON에는 발급자, 서명, 신선도, 독립 재시작 증명이 없으므로 어떤 local smoke나 candidate canary도 이를 `result: passed`로 바꾸어 worker를 활성화해서는 안 됩니다. 기존 boolean 플래그는 test process에서만 허용됩니다. 상세 결정은 [CR-002](./change-requests/CR-002-block-codex-sdk-production-adapter.md)를 따릅니다.

CR-003의 `openai-responses` provider는 Codex CLI나 agent tool process를 실행하지 않는 별도 선택 경로입니다. `GENERATION_PROVIDER=openai-responses`, `OPENAI_API_KEY`, `GENERATION_OPENAI_RESPONSES_MODEL`을 외부 설정으로 모두 제공해야만 선택되며, 요청은 `tools: []`, `tool_choice: "none"`, `store: false`, strict JSON Schema를 고정합니다. 이 선택은 Spring의 발행 게이트를 우회하지 않으며 실제 운영 API 호출과 배포 smoke는 별도 release story에서 승인·검증합니다.

향후 승인 조건은 문서화된 clean-container probe가 model-invoked tool이 worker token, API key, 저장소, host home, Docker socket, 다른 프로세스 환경 변수를 읽지 못한다는 점을 입증하고, 독립 verifier가 서명된 attestation을 발급하는 것입니다. probe는 두 번의 fresh container restart 결과와 이미지 digest를 포함해야 합니다. 단순히 동작하는 Codex API key가 있다는 사실만으로는 승인으로 보지 않습니다. 컨테이너는 반드시 non-root, read-only root filesystem, `/tmp`와 `/home/worker`에 대한 private tmpfs를 사용해야 합니다. Compose는 모든 Linux capability를 제거하고 `no-new-privileges`를 켜며 PID 제한을 적용하고 `node dist/healthcheck.js`로 live process + 최근 성공적인 backend contact를 동시에 요구합니다. 상태는 `docker compose --profile generation ps`로 확인할 수 있습니다.

종료 시에는 새 claim을 멈추고 active turn을 중단하고 heartbeat를 정지하며, grace period 안에서 이미 선택된 terminal payload만 제출 시도합니다. success-submit ambiguity는 failure submission으로 바꾸지 않습니다.
