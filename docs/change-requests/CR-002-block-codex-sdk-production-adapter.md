# CR-002: Codex SDK 운영 어댑터를 격리 경계 재설계 전까지 차단

- 상태: 승인
- 작성일: 2026-07-18
- 관련 이슈: #62
- 영향 범위: generation-worker Codex SDK 운영 실행, canary attestation, 배포/운영 승인 절차

## 변경 요청

Codex SDK를 사용하는 현재 generation-worker의 운영 어댑터 승인을 중단한다. 기존 `artifactDigest` 일치 JSON은 이미지 동일성 확인에는 사용할 수 있지만, 독립적인 운영 승인 증명으로는 사용하지 않는다.

Codex SDK는 계속 provider-neutral 경계 뒤의 개발·호환성 후보로 유지한다. 다만 다음 조건을 모두 만족하는 재설계와 독립 검증 전에는 운영 자동 발행에 사용하지 않는다.

1. agent-phase에서 API key와 worker token을 읽을 수 없는 자격증명 경계를 제공한다.
2. clean-container probe가 모델 도구의 파일·프로세스·네트워크 접근을 안전한 canary 값으로 검증한다.
3. probe의 원본 증적, 두 번의 독립 재시작 결과, 이미지 digest를 독립 verifier가 검토한다.
4. verifier가 발급한 서명·발급시각·만료시각·이미지 digest를 검증할 수 있는 attestation 형식을 사용한다.

## 근거

- `@openai/codex-sdk` 0.144.5는 전달된 `apiKey`를 Codex CLI 자식 프로세스의 `CODEX_API_KEY` 환경변수에 주입한다. 현재 worker의 tool shell allowlist는 일반 명령 환경을 줄이지만, agent CLI 자체의 프로세스 자격증명을 외부 신뢰 경계로 분리하지 않는다.
- 현재 `generation-worker/src/config.ts`는 `version`, `result`, `artifactDigest`만 검증한다. 해당 JSON은 Compose host mount에서 제공되므로, 발급자·서명·신선도·독립 재시작 증명을 확인하지 않는다.
- OpenAI의 Codex 보안 문서는 신뢰된 host의 secret과 agent 실행 환경을 분리하는 경계를 권장한다. 현재 SDK 기반 단일 worker 컨테이너는 그 강한 경계를 증명하지 못한다.

## 결정

- `GENERATION_CODEX_CANARY_ATTESTATION_PATH`의 기존 v1 JSON은 운영 승인 근거가 아니다.
- 현재 fail-closed 동작을 유지한다. local smoke 또는 candidate canary 결과로 `result: passed` attestation을 만들거나 mount하지 않는다.
- 다음 provider 선택은 provider-neutral 계약을 유지하는 별도 Change Request에서 결정한다. 후보는 agent tool process에 장기 API key를 주입하지 않는 managed execution 또는 tool-free generation adapter여야 한다.
- Spring/MySQL의 source evidence, publication gate, audit, schedule 권한과 versioned worker contract는 변경하지 않는다.

## 수용 기준

1. 운영 문서와 호환성 매트릭스가 Codex SDK production adapter를 명시적으로 차단한다.
2. test specification이 단순 JSON/digest 일치가 아니라 credential agent-phase 분리와 independent verifier를 요구한다.
3. 실제 API key가 local ignored configuration 밖으로 복사되거나 로그·문서·commit에 기록되지 않는다.
4. 후속 adapter 전환 전까지 Compose 기본 worker는 계속 시작을 거부한다.

## 위험과 후속 작업

- 위험: Codex SDK 기반 자동 발행을 즉시 운영할 수 없다.
- 완화: fake provider와 versioned job contract를 유지하고, Spring의 안전한 publication gate는 계속 동작한다.
- 후속 Story: alternate provider 선택·위협 모델·credential broker 또는 managed execution의 분리 경계를 설계하고, 정식 clean-container/restart canary와 signed attestation을 구현한다.
