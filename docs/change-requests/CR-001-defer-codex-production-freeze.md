# CR-001: Codex 운영 어댑터 확정 게이트를 Story 8로 이동

- 상태: 승인
- 작성일: 2026-06-29
- 영향 범위: Phase 0 공급자 확정 게이트, Story 8 generation-worker 구현

## 변경 요청

Phase 0에서는 `@openai/codex-sdk`의 로컬 런타임·샌드박스·취소·구조화 출력 호환성과 provider-neutral 경계만 승인한다. 외부 회전형 무인 자격증명, 독립 재시작, 배포 격리, lease/retry 계약을 포함한 **운영 어댑터 최종 확정**은 해당 기능을 실제 구현하는 Story 8의 선행 게이트로 이동한다.

Codex SDK는 그때까지 preferred candidate이며, 운영 어댑터로 확정하지 않는다. Story 8 게이트가 실패하면 동일한 버전 계약 뒤에 대체 어댑터를 선정하며 Spring의 증거·게시·감사·스케줄 권한은 변경하지 않는다.

## 근거

- Phase 0 스파이크는 Codex SDK 0.142.3에서 read-only sandbox, approval `never`, 도구 네트워크/web search 차단, 90초 abort, 구조화 출력 검증을 통과했다.
- 현재 워크스테이션에는 외부 공급 회전형 운영 자격증명과 독립 배포 환경이 없다. 기존 로그인 세션 성공을 운영 무인 인증 증거로 간주하면 안 된다.
- Story 8은 durable job, lease, retry, cancellation, schema validation, credential injection과 provider substitution을 함께 구현하므로 운영 검증을 수행할 최소 실행 단위다.

## 수용 기준

1. Story 1은 provider-neutral worker, 고정 SDK 후보, 로컬 호환성 스파이크와 미검증 운영 위험을 문서화한다.
2. Story 8은 외부 회전형 자격증명으로 무인 실행하고 Codex App 세션 없이 재시작되는 통합 테스트를 통과해야 한다.
3. Story 8 완료 전 자동 게시 또는 운영 스케줄을 활성화하지 않는다.
4. 실패 시 대체 어댑터만 교체하며 Spring/MySQL publication gate와 versioned contract는 유지한다.

## 위험과 완화

- 위험: 운영 인증 부적합이 후반에 발견될 수 있다.
- 완화: provider-neutral 계약과 fake provider를 유지하고 Story 8 시작 시 인증·배포 스파이크를 첫 번째 필수 작업으로 실행한다.
- 위험: 기능 개발이 특정 SDK에 결합될 수 있다.
- 완화: SDK 의존성은 `generation-worker`에 격리하고 Spring 계약 테스트는 provider-independent fixture로 유지한다.

## 승인 시 필요한 문서 변경

- `.omx/plans/prd-automated-content-blog.md` Phase 0/ADR follow-up의 production freeze 시점을 Story 8 선행 게이트로 변경
- `.omx/plans/test-spec-automated-content-blog.md`에 Story 8 unattended auth/restartability release gate 명시
- `docs/adr/0001-generation-provider.md` 상태를 `Accepted`로 변경하되 Codex SDK는 Story 8 통과 전까지 conditional candidate로 유지

## 승인 기록

- 승인 일시: 2026-06-29
- 승인 주체: 메인 에이전트 재량 승인
- 승인 근거: 사용자가 판단을 위임했고, Story 1의 로컬 호환성 증거와 Story 8의 운영 게이트 분리가 PRD, 테스트 계약, ADR 전반에서 가장 일관된 기준으로 검증되었다.
