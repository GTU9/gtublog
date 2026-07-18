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

Codex SDK production 실행은 의도적으로 동결되어 있습니다. 현재 `GENERATION_CODEX_CANARY_ATTESTATION_PATH`의 v1 JSON과 `artifactDigest` 일치는 **이미지 동일성 확인일 뿐 운영 승인 근거가 아닙니다**. host mount JSON에는 발급자, 서명, 신선도, 독립 재시작 증명이 없으므로 어떤 local smoke나 candidate canary도 이를 `result: passed`로 바꾸어 worker를 활성화해서는 안 됩니다. 기존 boolean 플래그는 test process에서만 허용됩니다. 상세 결정은 [CR-002](./change-requests/CR-002-block-codex-sdk-production-adapter.md)를 따릅니다.

CR-003의 `openai-responses` provider는 Codex CLI나 agent tool process를 실행하지 않는 별도 선택 경로입니다. `GENERATION_PROVIDER=openai-responses`, `OPENAI_API_KEY`, `GENERATION_OPENAI_RESPONSES_MODEL`을 외부 설정으로 모두 제공해야만 선택되며, 요청은 `tools: []`, `tool_choice: "none"`, `store: false`, strict JSON Schema를 고정합니다. 이 선택은 Spring의 발행 게이트를 우회하지 않으며 실제 운영 API 호출과 배포 smoke는 별도 release story에서 승인·검증합니다.

향후 승인 조건은 문서화된 clean-container probe가 model-invoked tool이 worker token, API key, 저장소, host home, Docker socket, 다른 프로세스 환경 변수를 읽지 못한다는 점을 입증하고, 독립 verifier가 서명된 attestation을 발급하는 것입니다. probe는 두 번의 fresh container restart 결과와 이미지 digest를 포함해야 합니다. 단순히 동작하는 Codex API key가 있다는 사실만으로는 승인으로 보지 않습니다. 컨테이너는 반드시 non-root, read-only root filesystem, `/tmp`와 `/home/worker`에 대한 private tmpfs를 사용해야 합니다. Compose는 모든 Linux capability를 제거하고 `no-new-privileges`를 켜며 PID 제한을 적용하고 `node dist/healthcheck.js`로 live process + 최근 성공적인 backend contact를 동시에 요구합니다. 상태는 `docker compose --profile generation ps`로 확인할 수 있습니다.

종료 시에는 새 claim을 멈추고 active turn을 중단하고 heartbeat를 정지하며, grace period 안에서 이미 선택된 terminal payload만 제출 시도합니다. success-submit ambiguity는 failure submission으로 바꾸지 않습니다.
