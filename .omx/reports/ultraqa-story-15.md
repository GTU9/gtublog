# Story 15 UltraQA 결과

결론: 통과

## 적대적 시나리오

- 개발 mock fallback이 실제 인증·CORS·영속성 실패를 성공으로 위장
- 호출자 `SPRING_DATASOURCE_*` 환경이 폐기 MySQL을 우회
- 테스트 실패 또는 SIGINT/SIGTERM 뒤 MySQL 컨테이너와 Spring/Next 프로세스 잔존
- 프론트엔드가 build-time API 주소 대신 기본 8080 주소를 사용
- 분류 생성은 성공하지만 게시물 연관 관계가 MySQL에 저장되지 않는 회귀

## 증거

- `pnpm quality`: backend check/MySQL, frontend 및 worker lint/typecheck/test/build, mock Playwright 6건 통과
- `pnpm e2e:fullstack`: 폐기 MySQL 8.4, 실제 Spring Boot, production Next.js에서 로그인·분류·초안·게시·공개 API·SSR 1건 통과
- 종료 후 `gtublog-e2e-mysql` 컨테이너와 13001/18080 listener가 0건임을 확인
- 명시적인 E2E datasource override, ephemeral RSA 키, mock 비활성화, 프로세스 트리 종료 경로 확인
- `git diff --check`: 통과
- 독립 구현 리뷰: 최초 수명주기 결함 반려 후 수정 재검토 APPROVE
- 독립 테스트 검증: 최초 datasource/신호 정리 결함 반려 후 수정 재검토 APPROVE

## 잔여 위험

- Next standalone 산출물의 실제 배포 실행 방식은 생산 토폴로지 story에서 확정한다.
- 자동화 관리 전이, worker 자동 게시, outbox 장애 주입과 운영 복구 훈련은 후속 G011 story에서 계속 검증한다.
