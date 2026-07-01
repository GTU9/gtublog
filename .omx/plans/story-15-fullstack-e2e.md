# Story 15 - 실제 서비스 경계 Full-stack E2E 기반

## 목적

개발 mock이 인증·CORS·API·영속성 결함을 숨기지 못하도록 임시 MySQL 8.4, 실제 Spring Boot, production Next.js를 연결한 릴리스 차단 E2E를 추가한다.

## 완료 기준

- mock E2E와 full-stack E2E 구성을 분리하고 full-stack 실행에서 fallback을 비활성화한다.
- 실행마다 폐기되는 MySQL과 실행 중 생성되는 RSA 키를 사용한다.
- 실제 로그인, 분류 생성, 초안 생성, 게시, 공개 API 및 SSR 조회를 브라우저 네트워크 응답으로 증명한다.
- Windows와 Linux에서 같은 root 명령을 사용하며 실패·신호 종료 시 테스트 DB를 제거한다.
- GitHub CI가 full-stack 흐름을 독립 gate로 실행한다.

## 후속 분해

이 story는 G011의 실제 서비스 테스트 기반을 만든다. 승인된 릴리스 범위를 축소하지 않으며, 자동화 관리 전이·outbox 장애 주입·운영 복구 훈련은 이 기반 위의 다음 story에서 계속한다.
