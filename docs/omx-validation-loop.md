# OMX 자동화 루프 검증 절차

## 목적

이 문서는 GTU Blog 저장소에서 기능 구현 또는 문서 정리 후 어떤 순서로 OMX 스킬과 프로젝트 검증 게이트를 통과해야 하는지 정의합니다. 목적은 story별 완료 판정을 일관되게 만들고, 구현·테스트·리뷰·QA 결과를 같은 기준으로 누적하는 것입니다.

## 적용 범위

- Story 단위 기능 구현
- 보안, 운영, 문서, 테스트 보강
- 자동화 파이프라인 관련 수정
- GitHub 이슈, 커밋, PR 전 최종 검증

문서만 수정하는 경우에도 아래 루프를 축약 적용하되, 생략한 검증은 근거와 함께 명시해야 합니다.

## 표준 루프

1. `$deep-interview`
2. `$ralplan`
3. `$ultragoal`
4. Codex 네이티브 서브에이전트 병렬 실행(필요한 경우만)
5. 로컬 품질 게이트와 증거 수집
6. `$code-review`
7. `$ultraqa`
8. 수정 반영 후 재검증
9. GitHub 이슈, 커밋, PR, `prototype` 병합

## 단계별 검증 기준

### 1. `$deep-interview`

사용 시점:

- 요구사항이 모호할 때
- story 범위가 바뀌었을 때
- 구현보다 먼저 경계와 비목표를 다시 고정해야 할 때

검증 산출물:

- 요구사항 원문과 해석 차이 제거
- 비목표와 위험 경계 명시
- 다음 단계에서 바로 계획으로 넘길 수 있는 입력 정리

통과 조건:

- 구현 범위, 완료 조건, 위험 경계가 문장으로 명확하다

### 2. `$ralplan`

사용 시점:

- 아키텍처, 테스트 형태, 수용 기준 합의가 필요할 때
- story를 코드 변경 가능한 단위로 고정해야 할 때

검증 산출물:

- story 목표
- 범위 / 제외 범위
- 수용 기준
- 필요한 테스트와 증거

통과 조건:

- 구현 전에 “무엇을 바꾸고 무엇을 바꾸지 않는지”가 문서로 고정된다

### 3. `$ultragoal`

사용 시점:

- story를 durable하게 추적할 때
- 완료 증거를 누적하고 다음 story로 자동 진행해야 할 때

검증 산출물:

- 현재 story 상태
- 필요한 체크포인트
- 완료 증거 요약

통과 조건:

- 현재 story의 목표와 완료 기준이 ledger/brief/plan 문서 기준으로 추적 가능하다

### 4. 네이티브 서브에이전트 병렬 실행

프로젝트 기본 정책:

- 개발 담당
- 테스트 담당
- 검증 담당

각 서브에이전트의 기대 결과:

- 개발: 기능 구현, 변경 파일 요약, 구현상 리스크 보고
- 테스트: 회귀/엣지 케이스 보강, 실행 명령과 실패 증거 보고
- 검증: 요구사항 충족 여부, 보안/예외/UX 검토, 승인 또는 반려

통과 조건:

- 메인 에이전트가 세 결과를 통합할 수 있어야 하며, 공유 파일 충돌이나 미해결 반려가 남지 않아야 한다

### 5. 로컬 품질 게이트와 증거 수집

기본 실행 후보:

```text
backend\gradlew.bat check
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
pnpm --dir generation-worker lint
pnpm --dir generation-worker typecheck
pnpm --dir generation-worker test
pnpm --dir generation-worker build
pnpm exec playwright test
docker compose config
```

상황별 추가 게이트:

- `pnpm quality`
- `pnpm e2e:fullstack`
- `pnpm drill:backup-restore`
- `pnpm drill:restart-recovery`
- `pnpm audit --prod --audit-level moderate`

통과 조건:

- story 영향 범위에 맞는 명령이 실제로 실행되었고
- 실패가 있으면 수정 후 재실행되었으며
- 실행 불가 명령은 왜 불가한지 명시한다

### 6. `$code-review`

목적:

- 구현 품질, 일관성, 회귀 위험, 과도한 복잡도 점검

검토 포인트:

- 요구사항 누락
- 보안 경계 약화 여부
- 예외 처리와 상태 전이
- 테스트 부재 또는 증거 부족
- 문서/계약/코드 불일치

통과 조건:

- 치명적 또는 높은 위험도의 미해결 리뷰 항목이 없다

### 7. `$ultraqa`

목적:

- 정상 흐름만이 아니라 공격적/실패/복구 시나리오까지 확인

검증 포인트:

- 인증/권한 우회 가능성
- 자동화 실행 재시도, 취소, 복구, 중복 실행
- 운영자 UX 오해 가능성
- 배포/복구/재검증 경로

통과 조건:

- hostile 시나리오 기준으로도 story 완료를 뒤집을 만한 미해결 문제가 없다

### 8. 수정 반영 후 재검증

`$code-review` 또는 `$ultraqa`에서 실패하면:

- 임의 축소 해석으로 덮지 않는다
- 같은 story 안에서 수정한다
- 구현 결함이면 개발/테스트/검증 루프를 다시 돈다
- 요구사항 변경이면 Change Request를 작성하고 계획 문서를 갱신한다

통과 조건:

- 수정 후 관련 게이트를 다시 실행해 새 증거를 확보한다

### 9. GitHub 이슈, 커밋, PR, 병합

프로젝트 규칙:

- GitHub 이슈를 먼저 작성
- 커밋/이슈/PR은 한국어
- 메인 에이전트만 Git/GitHub 작업 수행
- story 브랜치는 `prototype`에서 분기
- story 완료 후 PR을 `prototype`에 병합하고 다음 story로 자동 진행

병합 전 최종 체크:

- 테스트 통과
- 빌드/린트/타입체크 통과
- `$code-review` 통과
- `$ultraqa` 통과
- 검증 담당 승인
- 변경 파일/실행 명령/남은 리스크 보고 완료

## 문서 전용 변경의 축약 루프

문서만 수정한 경우에도 아래는 유지합니다.

1. 범위 문서 고정
2. 관련 문서 간 모순 점검
3. UTF-8 / 링크 / 명령 예시 검증
4. 필요 시 `docker compose config` 같은 무해한 운영 검증 수행
5. 리뷰 관점에서 문서 누락과 오해 가능성 확인

## 실패 처리 규칙

- 검증 실패를 “다음 story에서 해결”로 넘기지 않는다
- 현재 story의 수용 기준에 영향을 주는 실패는 현재 story에서 닫는다
- 단, 신규 범위 확장이나 승인 없는 위험 변경은 Change Request로 분리한다

## 이 저장소에서의 실전 해석

현재 저장소 기준으로 자동화 루프 검증은 다음을 의미합니다.

- 요구사항은 `.omx/specs/`, `.omx/plans/`, `AGENTS.md`를 기준으로 고정
- story 진행은 `$ultragoal`과 story 문서로 추적
- 병렬 실행은 tmux 팀이 아니라 Codex 네이티브 서브에이전트 3개 정책을 따름
- 완료 선언 전에는 코드리뷰와 UltraQA를 모두 거침
- 병합 후에는 사용자 확인을 기다리지 않고 다음 planned story로 자동 진행

## 관련 문서

- [AGENTS.md](../AGENTS.md)
- [README.md](../README.md)
- [docs/development.md](./development.md)
- [docs/runbook.md](./runbook.md)
- [docs/deployment.md](./deployment.md)
- [implementation-stories-automated-content-blog.md](../.omx/plans/implementation-stories-automated-content-blog.md)
