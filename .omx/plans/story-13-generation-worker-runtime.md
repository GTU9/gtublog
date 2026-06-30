# Story 13 — 실행형 생성 워커와 무인 Codex 경계

## 요구사항 요약

`generation-worker`를 테스트용 라이브러리 상태에서 외부 설정으로 실행 가능한 장기 실행 프로세스로 전환한다. 프로세스는 Spring의 claim/heartbeat/submit 계약만 사용하고, Codex SDK 호출에는 외부 주입 API 키·읽기 전용 sandbox·approval `never`·네트워크 및 web search 차단·구조화 출력·절대 timeout을 적용한다. 실자격증명 검증은 실행 가능한 smoke gate로 제공하되 자격증명이 없으면 운영 확정을 주장하지 않는다.

## RALPLAN-DR

### 원칙

1. Spring/MySQL의 스케줄·증거·게시 권한을 워커로 이동하지 않는다.
2. 워커는 재시작 가능하고 무상태에 가깝게 유지하며 lease를 진실의 원천으로 삼는다.
3. 자격증명과 소스 본문을 로그·오류·하위 프로세스 환경에 최소 노출한다.
4. timeout, shutdown, heartbeat, 중복 제출은 결정적으로 검증한다.
5. 실자격증명 증거가 없으면 production-ready로 표시하지 않는다.

### 결정 동인

1. 실제 자동 생성 루프의 부재를 가장 작은 독립 변경으로 해소할 것.
2. 기존 provider-neutral 계약과 Spring publication gate를 보존할 것.
3. Windows 개발과 Linux/container 운영에서 동일한 진입점을 제공할 것.

### 선택지

#### A. 독립 장기 실행 polling CLI (선택)

- 장점: 현재 claim/lease 계약을 그대로 사용하고 재시작·수평 확장이 단순하다.
- 단점: polling/backoff, 신호 처리, heartbeat lifecycle을 워커가 책임져야 한다.

#### A-2. 외부 supervisor가 `start:once`를 반복하는 short-lived worker

- 장점: 장기 상태, SDK child 잔류, secret lifetime을 줄이고 restart를 process manager에 위임한다.
- 단점: cold start와 pickup 지연이 증가하며 supervisor 주기가 domain schedule로 오인될 수 있다.
- 판단: 동일 entrypoint의 지원 모드로 유지하되 기본 운영은 long-running polling으로 둔다. supervisor는 worker availability만 관리하고 게시 schedule 권한은 계속 Spring/Quartz에 있다.

#### B. Spring 프로세스에서 Codex SDK 직접 호출

- 장점: 배포 프로세스 수가 줄어든다.
- 단점: Node SDK와 공급자 자격증명이 Spring 신뢰 경계로 들어와 AGENTS/ADR의 격리 불변식을 위반한다.

#### C. Codex App 자동화를 실행 스케줄러로 사용

- 장점: 로컬 운영 시작은 빠르다.
- 단점: MySQL/Quartz가 scheduler of record여야 한다는 승인 아키텍처와 재시작·무인 인증 요건을 위반한다.

대안 B/C는 격리 및 스케줄 권한 불변식을 위반하므로 운영 대안으로 무효다. A와 A-2는 동일한 one-job 실행 primitive를 공유한다. 장기 모드는 pickup latency와 cold-start를 줄이고, short-lived 모드는 외부 supervisor 환경에서 선택할 수 있다.

## 구현 단계

1. **구성 경계와 실행 진입점**
   - `generation-worker/src/config.ts`에 필수/선택 환경 변수 파싱을 추가한다.
   - 필수값: backend base URL, worker token, Codex API key. 선택값: worker ID, poll interval, error backoff min/max, claim/heartbeat/submit HTTP timeout, heartbeat interval, generation timeout, lease safety margin, shutdown grace, one-shot mode.
   - 오류 메시지는 변수 이름만 식별하고 값을 포함하지 않는다.
   - `generation-worker/src/cli.ts`와 `package.json`의 `start`/`start:once` 명령을 추가한다.
   - `start:once` 종료 코드는 job 없음/성공 `0`, 구성·인증·계약 오류 `2`, 일시적 backend/provider 실패 `3`, shutdown/timeout `4`로 문서화하고 테스트한다.
   - 운영 entrypoint에서는 fake provider 선택을 허용하지 않고 test fixture에서만 의존성 주입한다.

2. **Codex 공급자 timeout·자격증명 격리**
   - `generation-worker/src/codex-provider.ts`가 명시적 `apiKey`, 격리 workspace, timeout, 상위 `AbortSignal`을 받도록 한다.
   - `new Codex({ apiKey, env })`를 사용한다. 실제 사용자 `HOME`, `USERPROFILE`, `CODEX_HOME` 및 로그인/session 경로는 전달하지 않고, 작업별 빈 격리 HOME을 생성해 child env의 HOME 계열로 지정한다.
   - child env는 격리 HOME과 OS별 실행 allowlist만 포함한다. Windows는 `SystemRoot`, `ComSpec`, `PATH`, 작업별 `TEMP`/`TMP`를, Linux는 `PATH`, 작업별 `HOME`/`TMPDIR`를 허용하며 backend worker token 등 provider 실행에 불필요한 환경 변수는 제외한다.
   - Codex CLI 인증 프로세스 env와 model-invoked tool/shell env를 별도 경계로 취급한다. 설치된 SDK/CLI의 공식 shell environment policy를 `inherit = none`과 비밀 없는 `PATH`/격리 `HOME`/`TMPDIR` allowlist로 고정하고, tool subprocess에 `CODEX_API_KEY`, `OPENAI_API_KEY`, worker token 및 사용자 env가 상속되지 않게 한다.
   - Phase 0 compatibility gate에서 실제 compiled adapter/container가 shell env inheritance 차단을 지원하는지 검증한다. Linux 운영 container는 AppArmor/seccomp 또는 동등한 OCI 정책으로 `/proc/*/environ`, ptrace, host mounts, Docker socket 접근을 차단한다. 이 분리와 canary 비노출을 증명하지 못하면 Codex adapter의 production freeze를 실패시키고 fake/대체 provider 상태로 유지한다.
   - thread는 read-only, approval `never`, network/web search disabled, structured schema를 유지한다.
   - timeout 또는 shutdown은 turn AbortSignal을 취소하고 비밀이 제거된 실패 이유만 Spring에 제출한다.
   - 격리된 빈 임시 workspace를 작업마다 생성하고 repository, `.env`, 사용자 Codex HOME/session을 mount/read하지 않으며 종료 시 정리한다. container는 read-only root filesystem과 전용 tmpfs workspace를 사용한다.

3. **장기 실행 loop와 lease heartbeat**
   - heartbeat 응답의 UTC RFC 3339 `serverTime`과 `leaseExpiresAt`을 versioned JSON contract와 TypeScript 타입에 추가하고 producer/consumer/fixture를 함께 갱신한다. Java/TypeScript는 이를 offset 없는 local time이 아닌 `Instant`로 처리한다.
   - `generation-worker/src/runtime.ts`에서 generation이 진행되는 동안 heartbeat interval을 유지하며 매 응답의 최신 lease deadline을 사용한다.
   - worker는 generation 시작 시 monotonic absolute-timeout deadline을 고정한다. heartbeat 수신 시 `leaseExpiresAt - serverTime - measuredRequestElapsed - leaseSafetyMargin`을 보수적 remaining duration으로 계산해 수신 시점 monotonic deadline으로 변환하고, 둘 중 이른 시각에 취소한다. `leaseSafetyMargin`은 양의 구성값으로 두되 heartbeat interval보다 크고 최초 lease duration보다 작게 검증하며 remaining이 0 이하이면 즉시 abort한다. heartbeat는 절대 generation timeout을 연장하지 않는다.
   - backend client는 claim/heartbeat/submit별 request timeout과 상위 shutdown `AbortSignal`을 결합하고 operation별 오류 분류표를 코드·문서·테스트의 단일 계약으로 둔다.
     - 모든 operation의 `401/403`, claim의 `400`/unsupported contract: process-fatal.
     - heartbeat의 lease-lost/expired/run-terminal `404/409`: active turn abort, terminal submit 금지, job-level 종료 후 long-running은 다음 poll, one-shot은 code `4`.
     - submit의 same-ID/same-digest: idempotent `2xx`; digest mismatch/different terminal `409`: secret-free security event 후 process-fatal.
     - `429/5xx`/network timeout: 해당 operation deadline 안에서만 bounded retry하며 known lease 또는 shutdown grace를 넘지 않는다.
   - heartbeat request hang도 request timeout 뒤 generation abort와 shutdown grace 안에 수렴해야 한다.
   - no-job은 정상 polling sleep, transient backend 오류는 worker ID 기반 deterministic jitter를 포함한 bounded exponential backoff를 사용하고 성공 시 reset한다.
   - provider/generation failure만 최대 한 번 failure submit한다. 성공 payload submit의 응답 유실/5xx는 동일 payload의 bounded idempotent retry로 다루며 다른 failure payload로 뒤집지 않는다.
   - SIGTERM 순서는 stop claiming → active turn abort → heartbeat stop/await → generation failure일 때만 bounded failure submit → shutdown grace 초과 시 Spring lease recovery에 위임한다.
   - 동시에 한 작업만 처리하는 기본값을 유지해 lease/메모리 경계를 단순화한다.

4. **Spring의 untrusted-worker 입력 한도와 계약 강화**
   - 필수 terminal identity 및 heartbeat 시간 필드는 breaking change이므로 automation contract를 `v2`로 승격한다. v2 endpoint/media contract, producer, consumer, JSON schema, fixture, OpenAPI를 한 변경에서 갱신하고 v1 worker 요청은 명시적 unsupported-version 응답으로 거부한다.
   - `GenerationJobHeartbeatResponse` v2를 automation JSON schema/fixture에 추가하고 UTC `serverTime`/`leaseExpiresAt` 계약을 명시한다.
   - `GenerationJobSubmitRequest`와 schema에 title/excerpt/markdown/failure reason/citation count 및 전체 요청 byte 상한을 추가한다.
   - claim의 nested snapshot 필드, 유한 양수 ID, ISO timestamp/deadline, provider/schema exact match, payload byte 상한을 worker와 Spring 양쪽에서 검증한다.
   - 성공 terminal submit에는 worker가 생성한 `terminalSubmissionId`와 canonical terminal payload digest를 포함한다. canonicalization v1은 UTF-8 JSON, 고정 필드 순서, Unicode/line-ending 정규화, citation의 안정 정렬 키와 제외 필드를 versioned contract로 정의한다. Spring은 검증된 DTO에서 canonical payload를 독립 재구성해 digest를 재계산하며 worker 문자열을 신뢰하지 않는다. TS/Java가 공유하는 정상·순서변형·Unicode fixture로 같은 digest를 증명한다.
   - Spring은 `(jobId, terminalSubmissionId, serverRecomputedPayloadDigest)`를 멱등 identity로 저장해 같은 성공 payload 재요청에는 기존 terminal 결과를 반환하고, 전송 digest 불일치, 같은 ID의 다른 canonical digest 또는 성공 이후 failure 역전은 `409 Conflict`로 거부한다.
   - terminal 결과와 publication/audit side effect는 하나의 MySQL transaction에서 단 한 번 기록한다. 실제 Spring/MySQL 통합 테스트가 첫 성공 commit 직후 HTTP 응답 유실을 주입한 뒤 동일 payload retry가 같은 결과를 반환하고 게시·revision·audit가 각각 한 번뿐임을 증명한다.
   - 새 terminal identity/digest/version 컬럼과 uniqueness를 새 Flyway migration으로 추가하고 빈 DB migration, 기존 v1 데이터 upgrade, Hibernate `validate`, MySQL uniqueness/concurrency를 검증한다.
   - v2 배포 preflight는 v1 `PENDING/CLAIMED` job이 0건임을 요구한다. runbook은 먼저 claim 중단과 bounded drain을 수행하고, 잔여 active v1 job은 명시적으로 `CANCELLED`/감사 기록 후 v2 run recovery를 통해 재-enqueue한다. terminal v1 row는 immutable history로 보존하며 active row를 무조건 version rewrite하지 않는다.
   - servlet/filter/container 경계에서 DTO 역직렬화 전에 body byte 상한을 적용한다. 초과 `Content-Length`와 길이 없는 chunked body 모두 `413 Payload Too Large`로 중단하고 publication/audit payload를 만들지 않는 MySQL/API 통합 테스트를 추가한다.

5. **실행 패키징과 운영 문서**
   - Linux container/프로세스에서 동일한 compiled entrypoint를 실행할 수 있도록 worker Dockerfile 또는 동등한 독립 패키징을 추가한다.
   - `.env.example`, `docs/automation.md`, `docs/development.md`, ADR에 변수·시작·종료·재시작·secret rotation·health 관찰 절차를 기록한다. liveness는 process/event-loop 생존으로, readiness는 최근 성공 backend 접촉과 active shutdown 부재로 정의하고 supervisor/container 판정 명령을 제공한다.
   - 네이티브 Windows 실행은 개발용으로만 표시한다. 운영 격리 승인은 repository/사용자 HOME/host root/Docker socket을 mount하지 않고 read-only root filesystem, non-root user, 전용 tmpfs HOME/workspace를 사용하는 container 증거에만 부여한다.
   - live smoke는 명시적 opt-in 명령으로 두고 기본 CI에서는 fake provider를 사용한다.

6. **계약 및 회귀 테스트**
   - config 누락/잘못된 duration/secret 비노출 테스트.
   - no-job polling, deterministic jitter/backoff cap/reset, periodic heartbeat deadline 갱신, heartbeat 실패, timeout cancellation, SIGTERM 중단, shutdown grace 테스트.
   - 성공 submit 응답 유실 후 동일 payload retry, provider failure의 single failure submit, terminal payload 역전 방지 테스트.
   - 격리 workspace가 repository/.env/Codex session을 볼 수 없고 종료 후 정리되는지 검증한다.
   - fake HTTP backend와 fake provider를 사용한 compiled CLI smoke test.
   - 기존 automation JSON fixture와 runtime payload가 계속 호환되는지 contract test를 추가한다.
   - source 본문에 `env`, process environ, repository/HOME 파일, 임의 network/tool, schema 이탈과 secret 탈취를 유도하는 악성 prompt-injection fixture를 추가한다. mock assertion이 아니라 실제 sandboxed compiled adapter/container에서 token canary가 model 결과·로그·submit 어디에도 나타나지 않고 각 접근이 차단됨을 검증한다.
   - worker credential로 admin/publication API 접근이 `403`이고 claim/heartbeat/submit v2 범위만 허용됨을 Spring Security 통합 테스트로 증명한다.

7. **검증과 전달**
   - generation-worker lint/typecheck/test/build, root `pnpm quality`, Docker build/config를 통과한다.
   - 독립 코드 리뷰와 UltraQA에서 secret leakage, lease race, ambiguous submit, shutdown race, unhandled rejection을 검사한다.
   - 실 `CODEX_API_KEY`가 제공되지 않으면 live unattended smoke를 `NOT RUN — credential unavailable`로 명확히 기록하고 ADR 상태를 conditional로 유지한다.

## 수용 기준

1. `pnpm --dir generation-worker start`가 필수 외부 설정으로 장기 실행되며 자격증명 값은 출력하지 않는다.
2. `start:once`는 job 없음에 0으로 종료하고, job 성공 시 claim→heartbeat→generate→submit을 한 번 수행한다.
3. generation 동안 heartbeat가 발생하며 UTC server time과 lease expiry를 request elapsed만큼 보수적으로 줄여 monotonic deadline으로 변환한다. heartbeat는 절대 timeout을 연장하지 않으며 양·음 clock skew, 지연/hang 응답, margin 이하 lease, SIGTERM에서 Codex turn이 제한 시간 안에 취소된다.
4. provider failure는 최대 한 번의 failure submit으로 수렴한다. 성공 submit 응답 유실 후 동일 terminal identity 재요청은 Spring이 DTO에서 재계산한 canonical digest로 같은 terminal 결과를 반환하고 게시·revision·audit는 한 번만 발생하며, 전송 digest 불일치·다른 payload·failure 역전은 409로 거부된다.
5. Codex SDK는 명시적 외부 API key, OS별 allowlist child env, 작업별 격리 HOME/workspace를 사용하고 실제 사용자 HOME/Codex session 및 backend worker token을 보지 않으며 App 로그인 세션을 필요 조건으로 삼지 않는다.
6. Spring은 DTO 역직렬화 전에 `Content-Length` 및 chunked 초과 body를 413으로 거부하고 malformed nested worker payload를 게시 전에 거부하며 v2 schema/fixture/OpenAPI와 DTO 한도가 일치한다.
7. structured lifecycle 로그는 event name, worker ID, job ID, duration/status allowlist만 포함하고 token, prompt, source body, generated body를 포함하지 않는다. liveness와 마지막 backend 접촉 상태를 관찰할 수 있다.
8. fake backend/provider compiled smoke가 long-running 및 `start:once` 재시작 전후 동일한 계약으로 통과한다.
9. lint, typecheck, tests, build, root quality, container read-only/tmpfs build/config가 통과한다.
10. clean HOME/container에 App session/config를 mount하지 않고 명시적 API key로 두 번 재시작하는 live smoke가 제공된다. 자격증명이 없으면 실행되지 않고 production adapter 확정 문구가 추가되지 않는다.
11. v2 Flyway migration이 빈 DB와 기존 v1 fixture DB에서 성공하고 Hibernate validation 및 terminal uniqueness/concurrency 테스트를 통과한다.
12. 실제 sandboxed adapter/container의 악성 prompt-injection canary는 shell env/process environ/repository/HOME/network/schema 경계를 넘지 못하고 token canary가 결과·로그·submit에 없다. 이 증거가 없으면 Codex adapter는 production 미승인이다. worker credential은 admin/publication API에서 403을 받으며 `start:once` 종료 코드와 liveness/readiness 판정이 문서·테스트와 일치한다.
13. HTTP 오류 분류가 operation별 계약과 일치하여 lease-loss heartbeat 404/409는 job만 종료하고 다음 poll로 진행하지만 submit digest 충돌 409와 인증/계약 오류는 보안 이벤트 후 프로세스를 종료한다.
14. v2 migration preflight가 active v1 job을 감지해 배포를 막고, drain 또는 audited cancel/re-enqueue 뒤에만 migration이 진행되며 terminal v1 history는 보존된다.

## 위험과 완화

- **heartbeat와 submit 경합**: generation 종료 시 heartbeat loop를 먼저 중단·await한 후 submit한다.
- **shutdown 중 실패 submit 중복**: job별 단일 terminal-submit guard를 둔다.
- **backend 장애 busy loop/thundering herd**: injectable clock/sleeper와 worker ID 기반 deterministic jitter를 사용하고 cap/reset을 테스트한다.
- **secret 누출**: config 오류·logger·provider 오류를 allowlist 기반으로 정규화한다.
- **SDK child process 잔류**: 하나의 composed AbortSignal로 timeout/SIGTERM을 전달하고 shutdown grace 안에서 active run 정리를 await한다.
- **success submit 응답 유실**: terminal payload identity를 고정하고 동일 payload만 bounded retry하며 다른 terminal 상태로 바꾸지 않는다.
- **live credential 부재**: fake provider 증거와 live smoke 증거를 분리하고 conditional ADR 상태를 유지한다.

## Pre-mortem

1. Codex 호출이 lease보다 오래 걸려 Spring이 run을 회수한 뒤 늦은 submit이 반복된다. → heartbeat 응답의 갱신 deadline과 safety margin 기반 취소 통합 테스트로 차단한다.
2. SIGTERM 직후 heartbeat와 failure submit이 동시에 실행되어 unhandled rejection 또는 중복 audit가 발생한다. → shutdown 상태 머신과 terminal-submit 단일화 테스트로 차단한다.
3. 운영 환경에 API key가 없는데 로컬 App 세션으로 우연히 성공해 무인 인증이 검증됐다고 오판한다. → key 필수 파싱과 clean-home live smoke를 별도 gate로 둔다.

## 확장 테스트 계획

- **Unit**: config, abortable sleep, deterministic jitter, error redaction, terminal payload state, deep structured validation.
- **Integration**: fake HTTP backend claim/heartbeat/latest deadline/submit, operation별 request timeout/오류 분류와 lease-loss-vs-security-409, heartbeat의 절대 timeout 불연장, 양·음 clock skew/지연 응답 monotonic 변환, pre-deserialization Content-Length/chunked 413, Spring 재계산 canonical digest 및 TS/Java 공유 fixture, 실제 Spring/MySQL의 commit 후 response-drop 및 동일 terminal payload retry 단일 side-effect 증명, conflicting digest/failure 409, active-v1 preflight/drain/cancel-re-enqueue upgrade, worker credential least privilege, delayed provider, backend 5xx/backoff, restart 후 재claim.
- **E2E**: compiled long-running/`start:once` + fake provider fixture; read-only root/tmpfs container; 자격증명 제공 시에만 clean HOME/App-session-free Codex live smoke 2회.
- **Observability**: allowlisted structured lifecycle events, liveness/last backend contact, token/prompt/source/generated body 부재 assertion.

## ADR

- **Decision**: provider-neutral Spring 계약을 소비하는 독립 long-running polling CLI를 채택한다.
- **Drivers**: 신뢰 경계 유지, 재시작/수평 확장, 기존 lease 계약 재사용.
- **Alternatives considered**: long-running polling, supervisor 반복 `start:once`, Spring 내장 호출, Codex App scheduler.
- **Why chosen**: 기본 운영에서 아키텍처 불변식을 유지하면서 cold-start와 pickup latency를 줄이므로 선택한다. `start:once`는 동일 경계를 지키는 supervisor 운영 대안으로 유지한다.
- **Consequences**: worker lifecycle/backoff/heartbeat 구현과 별도 배포 단위가 필요하다.
- **Follow-ups**: 실제 외부 API key로 clean-home smoke를 통과한 뒤에만 Codex production freeze ADR을 갱신한다.

## 사용 가능한 에이전트와 실행 인력

- `executor` (medium): worker/config/provider 구현.
- `test-engineer` (medium): concurrency, timeout, signal, fake backend tests.
- `verifier` (high): 자격증명 경계, 요구사항, 회귀 검증.
- `architect` / `critic` (high): 계획 및 최종 경계 검토.
- `code-reviewer` (high): 통합 diff 검토.

병렬 실행 시 메인 에이전트가 Ultragoal ledger와 Git/GitHub를 소유하고 executor/test-engineer/verifier 세 lane의 공유 파일 충돌을 제한한다. tmux Team은 이 프로젝트 정책상 사용하지 않고 Codex native subagents를 사용한다. `$ralph`는 사용자가 명시적으로 선택하는 단일 소유자 fallback에만 사용한다.

## Goal-mode 후속 제안

- 기본: `$ultragoal`로 Story 13 durable checkpoint를 소유한다.
- 병렬 필요 시: Ultragoal + native subagents로 구현·테스트·검증 증거를 통합한다.
- 연구/성능 전용 목표가 아니므로 `$autoresearch-goal`, `$performance-goal`은 해당하지 않는다.

## Team 검증 경로

1. executor가 구현과 변경 파일을 보고한다.
2. test-engineer가 독립 회귀 테스트와 명령 결과를 보고한다.
3. verifier가 secret/lease/shutdown 요구를 승인 또는 반려한다.
4. 메인 에이전트가 전체 quality, code-review, UltraQA를 실행하고 승인된 증거만 Ultragoal checkpoint와 GitHub story에 반영한다.

## 변경 기록

- 2026-06-30: 초기 deliberate consensus draft 작성. 공식 Codex 문서와 설치된 SDK 타입의 `apiKey`, structured output, AbortSignal 근거를 반영.
- 2026-06-30: Architect 1차 REVISE 반영. heartbeat response contract, terminal submit ambiguity, isolated workspace, input limits, short-lived worker 대안, deterministic jitter와 observability 기준을 추가.
- 2026-06-30: Architect 2차 REVISE 반영. Spring terminal submit 멱등 identity와 response-loss MySQL 증거, timestamp 기반 deadline, 작업별 격리 HOME/OS별 환경 allowlist를 명시.
- 2026-06-30: Critic 1차 ITERATE 반영. Spring canonical digest 재계산, HTTP timeout/오류 분류, clock-skew 안전 monotonic lease, 역직렬화 전 body 제한, v2/Flyway 절차, container-only 운영 격리 및 prompt-injection/최소권한/health 기준을 추가.
- 2026-06-30: Architect 재검토 REVISE 반영. Codex 인증 env와 model shell env 분리 및 실 canary gate, operation별 409 정책, active v1 drain/cancel/re-enqueue migration 절차를 명시.
