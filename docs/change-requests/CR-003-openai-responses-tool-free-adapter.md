# CR-003: 도구 미사용 OpenAI Responses 생성 어댑터

- 상태: 승인됨
- 작성일: 2026-07-19
- 관련 이슈: #68
- 영향 범위: generation-worker provider 선택, 운영 환경 변수, 생성 어댑터 검증

## 변경 요청

CR-002로 운영 차단된 Codex SDK 어댑터를 대체할 선택 가능한 생성 경로로 OpenAI Responses API 직접 어댑터를 추가한다. 이 어댑터는 Codex CLI나 에이전트 도구 프로세스를 시작하지 않고, worker 프로세스가 HTTPS로 Responses API를 한 번 호출한다.

## 결정

- `GENERATION_PROVIDER=openai-responses`를 **명시한 경우에만** 새 어댑터를 선택한다. 환경 변수가 없으면 기존 Codex 선택은 계속 CR-002에 의해 fail-closed 된다.
- backend의 `AUTOMATION_WORKER_PROVIDER=openai-responses`와 worker의 `GENERATION_PROVIDER=openai-responses`는 함께 설정한다. 둘 중 하나라도 다르면 worker는 호환 job을 claim하지 않는다.
- `OPENAI_API_KEY`와 `GENERATION_OPENAI_RESPONSES_MODEL`은 외부 환경 변수로만 주입한다. 키와 원문 응답은 로그, 계약, 프런트엔드, 저장소에 기록하지 않는다.
- Responses 요청은 `tools: []`, `tool_choice: "none"`, `store: false`를 고정하고, strict JSON Schema로 초안 필드를 제한한다.
- 응답은 application-level 검증을 다시 통과해야 하며, Spring의 source evidence, publication gate, audit, schedule, idempotency 권한은 변경하지 않는다.
- 실제 API 키 호출, 운영 Compose 활성화, 자동 발행은 이 변경 범위에서 제외한다. CR-003은 코드 경로 승인이지 운영 release gate 승인이 아니다.

## 수용 기준

1. 직접 API 호출이 도구와 도구 선택을 모두 비활성화하고 strict JSON Schema를 요청한다.
2. 누락된 키 또는 모델은 명확한 설정 오류가 되며, 오류에 비밀값이 포함되지 않는다.
3. malformed 응답, HTTP 실패, 취소 신호를 안전하게 처리하고 원문 provider payload를 노출하지 않는다.
4. Codex SDK provider의 CR-002 fail-closed 동작과 기존 worker 계약은 유지된다.
5. 운영 문서에 명시적 활성화 방법과 비운영 범위가 기록된다.

## 위험과 완화

- 모델 출력이 잘못된 구조일 수 있으므로 API strict schema와 worker 검증을 함께 적용한다.
- API 키는 worker 프로세스의 외부 설정에만 존재한다. 요청/오류 본문을 로그에 기록하지 않는다.
- 이 어댑터는 운영 배포 승인이 아니다. 이후 story에서 별도 Compose rehearsal과 smoke test로 검증한다.

## 참고

- <https://platform.openai.com/docs/api-reference/responses/create>
- <https://platform.openai.com/docs/guides/structured-outputs>
