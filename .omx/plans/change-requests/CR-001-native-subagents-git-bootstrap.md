# CR-001: 네이티브 서브에이전트 실행과 Git 부트스트랩 정합성

## 상태

승인 완료 — 2026-06-28 후속 Architect 및 Critic 네이티브 리뷰 통과

## 배경

사용자는 Windows Codex App에서 tmux 기반 OMX Team 대신 Codex 네이티브 서브에이전트 3개를 사용하도록 명시했다. 또한 저장소는 커밋이 없는 `prototype` unborn 브랜치에서 시작하므로, 일반적인 story 브랜치와 pull request 흐름을 적용할 기준 커밋이 없다.

## 변경

1. 병렬 구현 권고를 `$team`에서 개발·테스트·검증 역할의 네이티브 서브에이전트로 교체한다.
2. 메인 에이전트가 Ultragoal 통합과 모든 Git/GitHub 작업을 전담한다.
3. 승인된 기획 기준선을 Story 0으로 정의한다. GitHub 이슈를 먼저 만든 뒤 문서 검증과 docs-only QA를 통과하면 최초 `prototype` 커밋으로 직접 push한다.
4. Story 1부터는 반드시 `prototype`에서 story 브랜치를 만들고 이슈 → 검증 → 커밋 → push → PR → 검사 → merge 순서를 따른다.

## 변경하지 않는 항목

- 제품 범위, 기술 스택, 보안 경계, 게시 정책, 테스트 계약
- 이슈 선행 및 한국어 이슈·커밋·PR 정책
- code-review와 UltraQA 완료 게이트

## 검증 조건

- Architect가 운영 변경이 아키텍처를 약화하지 않는다고 승인한다.
- 후속 Critic이 PRD, `AGENTS.md`, Git/GitHub 순서 사이에 모순이 없다고 승인한다.
- GitHub 인증, 원격 저장소, Story 0 이슈가 실제로 존재한다.
