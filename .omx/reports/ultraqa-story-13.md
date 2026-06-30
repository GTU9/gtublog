# Story 13 UltraQA — 실행 가능한 generation worker runtime

검증 시각: 2026-07-01 (Asia/Seoul)

## 판정

PASS. 실제 Codex 자격증명을 사용하는 clean-container canary는 실행하지 않았으며, 그 증거가 생길 때까지 production adapter는 fail-closed 상태로 유지된다.

## 적대적 시나리오

| 시나리오 | 기대 결과 | 동적 증거 | 결과 |
| --- | --- | --- | --- |
| heartbeat transport 불확실 중 provider가 `AbortError`로 래핑 | terminal 제출 금지, heartbeat 정리 | worker 회귀 테스트 | PASS |
| generation timeout이 `AbortError`로 래핑 | 동일 실패 terminal 후 timeout/exit 4 분류 | worker 회귀 테스트 | PASS |
| 성공 submit 응답 유실 | 동일 UUID/digest만 bounded retry | worker 및 MySQL 통합 테스트 | PASS |
| 다른 terminal/digest 재제출 | Spring 409, 추가 side effect 없음 | MySQL 통합 테스트 | PASS |
| claim 밖 citation ID | submit 전 차단, timer/heartbeat 0 | worker fake-timer 회귀 테스트 | PASS |
| heartbeat와 submit 경합 | 공통 run→job 잠금 순서 | backend check/MySQL 통합 테스트 | PASS |
| lease 만료 직전 terminal retry | 최신 lease와 shutdown grace 중 짧은 경계 적용 | worker 회귀 테스트 및 코드 리뷰 | PASS |
| oversized/chunked worker body | 역직렬화 전 413 | backend filter 테스트 | PASS |
| boolean만으로 production 활성화 | 구성 단계 exit 2 | hardened container 실행 | PASS |
| 위조/구 artifact attestation | image 내부 runtime digest 불일치로 거부 | config 회귀 테스트 | PASS |
| 컨테이너 권한 상승/쓰기 | non-root, read-only, cap-drop, no-new-privileges, private tmpfs | Compose config 및 image 실행 | PASS |
| stale/uncontacted worker | health probe 실패 | file-based probe와 Compose healthcheck | PASS |
| compiled CLI 성공 경로 | claim→heartbeat→generate→submit 후 exit 0 | compiled fake-provider E2E | PASS |

## 전체 게이트

- `pnpm quality`: PASS — backend check/MySQL, frontend lint/typecheck/test/build, worker lint/typecheck/60 tests/build, Playwright 5 tests.
- `docker compose --profile generation config --quiet`: PASS.
- generation-worker image build 및 `/app/ARTIFACT_DIGEST`, `dist/cli.js`, production Codex SDK payload 확인: PASS.
- `git diff --check`: PASS.
- 독립 verifier: APPROVE.
- 독립 code review: APPROVE.

## 남은 비차단 위험

- 실제 Codex API credential과 악성 prompt를 사용하는 2회 clean-container canary가 아직 없다. Attestation을 배포 secret으로 마운트하기 전에는 production adapter를 활성화하지 않는다.
- pnpm legacy deploy가 개발 도구 bin 링크 경고를 출력하지만 runtime이 사용하는 production SDK와 compiled CLI 존재 및 실행은 검증됐다.
