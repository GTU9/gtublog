# 운영 런북

## 일일 점검

1. `/actuator/health`와 `/actuator/prometheus`를 확인합니다.
2. `/admin/automation`을 열어 아래 항목을 검토합니다.
   - held run
   - failed run
   - pending outbox event
   - 최근 hold reason
3. 자동화가 성공한 뒤 최신 발행 글이 public page, RSS, sitemap에 반영되었는지 확인합니다.

## 백업 / 복원 drill

권장 로컬 검증 순서는 다음과 같습니다.

0. 선택 사항: `pnpm drill:backup-restore`를 실행해 publish → dump → restore → login/public/diagnostics 검증 흐름을 disposable MySQL 8.4 container로 자동화합니다.
1. 타임스탬프가 포함된 MySQL dump로 schema와 data를 백업합니다.
2. 새로운 MySQL 8.4 인스턴스에 복원합니다.
3. `ddl-auto=validate` 상태로 Flyway validation과 애플리케이션 기동을 확인합니다.
4. 복원 후 administrator login, public post 조회, automation diagnostics를 확인합니다.

## 재시작 drill

선택 사항: `pnpm drill:restart-recovery`를 실행해 pending-outbox replay, backend restart, compatible worker claim, expired-run recovery 흐름을 disposable infrastructure에서 검증합니다.

1. manual automation run을 하나 실행합니다.
2. outbox가 생성되는 도중 또는 그 직후 backend를 재시작합니다.
3. run이 복구 가능한 상태로 유지되고 pending outbox event를 안전하게 재처리할 수 있는지 확인합니다.
4. generation-worker를 재시작하고 다음 compatible job을 계속 claim할 수 있는지 확인합니다.
5. 테스트용 run lease를 과거 시점으로 강제로 이동시킨 뒤 recovery sweep을 실행하고, run이 `FAILED`, job이 `CANCELLED`가 되며 `AUTOMATION_RUN_RECOVERED_AS_FAILED` 감사 이벤트가 정확히 1건만 남는지 확인합니다.
6. deterministic operator recovery가 필요하면 관리자가 `POST /api/v1/admin/automation/runs/{runId}/recovery`를 호출할 수 있습니다. batch recovery는 `POST /api/v1/admin/automation/recovery/process`를 사용합니다.

## 마이그레이션 사전 점검

`V6__automation_run_recovery.sql`을 적용하기 전에, 과거 generation job이 run당 최대 1행만 가지는지 확인합니다.

```sql
SELECT run_id, COUNT(*) AS job_count
FROM generation_job
GROUP BY run_id
HAVING COUNT(*) > 1;
```

결과가 반환되면, 해당 행은 감사 가능한 운영 판단으로 먼저 정리해야 합니다. 이 마이그레이션은 모호한 job 이력을 자동 삭제하지 않고 의도적으로 실패하도록 설계되어 있습니다.

## 릴리스 게이트 체크리스트

- `backend\\gradlew.bat check --no-configuration-cache`
- `pnpm --dir frontend lint`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend test`
- `pnpm --dir frontend build`
- `pnpm --dir generation-worker lint`
- `pnpm --dir generation-worker typecheck`
- `pnpm --dir generation-worker test`
- `pnpm --dir generation-worker build`
- `pnpm exec playwright test`
- `docker compose config`
- `pnpm audit --prod --audit-level moderate`
- `Security` 워크플로가 CodeQL, secret, dependency, worker image scan을 통과했는지 확인
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)이 story branch 또는 pull request에서 통과했는지 확인

## 배포 인수인계

첫 실제 배포 전에 대상 환경이 [deployment.md](./deployment.md)에 문서화된 runtime topology와 startup order를 따르는지 확인합니다. 특히 아래 항목은 반드시 만족해야 합니다.

- browser traffic이 reverse proxy를 통해 same-origin으로 유지될 것
- generation-worker가 polling을 시작하기 전에 Spring Boot가 기동과 migration을 성공적으로 마칠 것
- production secret은 커밋된 파일이 아니라 외부 secret management에서 주입될 것
- Codex production adapter freeze는 문서화된 canary attestation gate를 충족하지 않는 한 계속 유지될 것
- [../.env.example](../.env.example)의 로컬 placeholder 값은 대상 환경의 secret/configuration system으로 대체될 것
