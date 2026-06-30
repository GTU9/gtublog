# Story 13 Critic 합의 검토

- 역할: Critic
- 순서: 2
- 최종 판정: `APPROVE`
- 검토 대상: `.omx/plans/story-13-generation-worker-runtime.md`
- 선행 조건: Architect `CLEAR`

## 최종 근거

계획은 AGENTS.md의 신뢰 경계, PRD의 Spring 게시 권한, release test의 무인 인증·재시작·lease·멱등성 요구를 충족하며 검증 가능한 구현 계획이다.

## 구현 시 핵심 검증

- Spring은 worker digest를 신뢰하지 않고 canonical payload에서 독립 재계산하며 TS/Java 공유 fixture와 일치해야 한다.
- 논리적 failure terminal payload 한 개와 동일 payload의 bounded transport retry를 구분해야 한다.
- active v1 drain/cancel/re-enqueue는 실제 MySQL upgrade test로 증명해야 한다.
- container canary가 인증 env와 model shell env 분리를 증명하지 못하면 Codex production freeze를 실패로 유지해야 한다.
- 실 API key가 없으면 fake-provider 검증만 보고하며 운영 준비 완료를 주장하지 않는다.
