# Story 22 - Codex SDK 모델 호환성과 무인 Smoke Gate 복구

## 배경

실제 외부 API 키로 실행한 `pnpm --dir generation-worker spike:codex`가 `@openai/codex-sdk` 0.142.3과 현재 기본 모델 `gpt-5.6-sol`의 호환성 오류로 HTTP 400을 반환했다. 인증은 API 요청 단계까지 성공했으나 SDK가 오래되어 live gate를 진행할 수 없다.

## 범위

- `@openai/codex-sdk`를 공식 최신 안정 호환 버전으로 최소 업데이트한다.
- lockfile을 갱신하고 타입·테스트·빌드를 검증한다.
- 외부 API 키를 출력·저장·커밋하지 않는 단일 process environment에서 real non-interactive smoke를 재실행한다.
- smoke 성공 여부와 별개로 clean-container 격리 canary attestation 전까지 production adapter freeze를 유지한다.

## 제외

- API 키, ChatGPT 로그인 세션, attestation JSON의 저장소 등록 또는 GitHub secret 등록
- production Compose generation profile 기동과 자동 게시
- provider contract, Spring publication gate, worker 권한 범위 변경

## 완료 기준

1. 현재 Codex 기본 모델에서 structured-output smoke가 non-interactive로 통과한다.
2. worker lint, typecheck, test, build가 통과한다.
3. 기존 shell environment policy와 read-only sandbox, approval `never`, tool network/web search 차단이 유지된다.
4. production adapter 상태는 matching clean-container canary attestation 전까지 frozen으로 유지된다.
5. API key·토큰·세션이 diff, git status, logs, CI 설정에 노출되지 않는다.
