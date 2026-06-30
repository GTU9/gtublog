# Story 14 UltraQA 결과

결론: 통과

## 적대적 시나리오

- 수동 게시물의 `script`, 이벤트 핸들러, `javascript:` URL 저장 및 노출
- DB에 남아 있는 레거시 악성 HTML의 공개 API 노출
- 관리자 Markdown 미리보기를 통한 DOM XSS
- CSP가 개발/스테이징 API origin을 누락해 관리자 요청을 차단하는 회귀
- 취약한 Node 생산 의존성, 저장소 비밀, 파일시스템 및 worker 이미지 취약점의 CI 누락

## 증거

- `pnpm quality`: backend check/MySQL, frontend 및 worker lint/typecheck/test/build, Playwright 6건 통과
- `pnpm audit --prod --audit-level moderate`: 알려진 취약점 없음
- MySQL `ContentApiIntegrationTests`: 레거시 행을 직접 오염시킨 공개 응답 정화 포함 통과
- Playwright: 보안 헤더와 정확한 기본 API origin CSP 확인
- `git diff --check`: 통과
- 독립 코드 리뷰: APPROVE
- 독립 검증: APPROVE

## 잔여 위험

- CSP는 Next.js 동작 때문에 인라인 스크립트를 아직 허용한다. nonce 기반 정책은 별도 강화 항목이다.
- 실제 운영 토폴로지, 백업/복구 훈련, 전 서비스 실연동 E2E는 후속 릴리스 검증 story에서 다룬다.
