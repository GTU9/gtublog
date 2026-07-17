# Story 21 - 운영 복구 drill의 CI 릴리스 게이트화

## 목표

이미 구현된 백업·복원 및 재시작·복구 drill을 GitHub Actions에서 반복 실행하여, 릴리스마다 실제 MySQL 복원과 자동화 복구 계약의 증거를 남긴다.

## 범위

- disposable MySQL 8.4 container를 사용하는 `pnpm drill:backup-restore` CI job을 추가한다.
- fake provider를 사용하는 `pnpm drill:restart-recovery` CI job을 추가한다.
- 두 job에 Java 25, Node 24, pnpm, Chromium, Docker 준비 및 명시적 timeout을 적용한다.
- 실패 시 drill 로그와 Playwright 산출물을 보존한다.
- 런북과 배포 문서에서 두 drill을 릴리스 게이트로 명시하고, 실제 Codex 무인 자격증명 검증과 구분한다.

## 제외

- 실제 Codex API key, Codex App 세션, canary attestation 승인 또는 production adapter freeze
- 실제 운영 MySQL, DNS, TLS, 클라우드 리소스 조작
- 자동화 게시 정책 또는 사용자 기능 변경

## 완료 기준

1. CI의 독립 job에서 `pnpm drill:backup-restore`가 publish → dump → restore → login/public/diagnostics를 통과한다.
2. CI의 독립 job에서 `pnpm drill:restart-recovery`가 outbox replay, backend restart, 만료 lease 복구 및 감사 기록을 통과한다.
3. 각 job은 20분 이내 timeout을 가지며 실패 시 진단 산출물을 보존한다.
4. runbook 및 deployment 문서가 fake-provider 기반 검증 범위와 실제 Codex 승인 제외를 명확히 설명한다.
5. 기존 CI 및 Security workflow가 회귀 없이 통과한다.

## 검증

```text
pnpm drill:backup-restore
pnpm drill:restart-recovery
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
pnpm exec playwright test
```

GitHub Actions에서 Story branch CI, Security workflow 및 PR 검사를 최종 증거로 사용한다.
