# Story 23 검증 기록

2026-09-24. 공개 탐색·출처·집계 복구의 검증이며 후속 자동화 기능 완료를 의미하지 않는다.

## 동적 QA 시나리오

| ID | 의도/사용자 모델 | 설정 및 실행 | 기대 신호 | 실제 결과/수정 | 증거 및 정리 |
|---|---|---|---|---|---|
| Q1 | 정상 독자의 공개 탐색 | Playwright public-blog: 출처 링크, 월 이동 | 실제 링크/월 목록 렌더링 | 통과 | 전체 브라우저 8/8 통과, 서버 종료 |
| Q2 | 잘못된 페이지를 입력하는 독자 | page=2 빈 검색 결과에서 이전 이동 | 검색어 보존 및 복구 가능 | 통과 | public-blog 회귀 테스트 유지 |
| Q3 | 대규모 사이트 운영자 | Vitest 600개 글, 12페이지 | 500개 초과 포함 | 통과 | public-api 단위 테스트 유지 |
| Q4 | 수집 중 데이터 변경/장애 | 페이지 실패, 총 개수 변경, 중복 경계로 누락 | 부분 사이트맵 대신 실패 | 통과 | 프런트 29개 테스트 통과 |
| Q5 | 실제 관리자/독자 | 임시 MySQL + Spring + 프로덕션 Next, 21개 발행 | 전체 통계/2페이지/분류명 검색/월 목록 | 최초 category 정적 생성 오류 재현, 동적 SSR 수정 후 통과 | fullstack 1/1, 임시 컨테이너/서버 정리 |
| Q6 | 느린 콜드 시작 | 브라우저 관리자 작성/복구 | 정상 완료 | 최초 30초 초과, 60초 한도로 전체 재실행 8/8 통과 | 명령 종료코드 0, 초기 멈춘 실행 트리만 종료 |
| Q7 | 테스트 중 네트워크 서버 실패 | PinnedSourceHttpClientTests 자원 종료 | 테스트 프로세스 종료 | 기존 executor/socket 종료 순서 교착 수정 | 백엔드 테스트 담당 검증 기록으로 통합 |

검증 대상이 읽기 API/UI인 이번 범위에서 프롬프트 주입 및 OMX 상태 변경은 기능 경로에 없으므로 추가하지 않았다. 기존 dirty 작업은 이번 수정과 함께 보존했고 임의 삭제/초기화하지 않았다. 테스트 성공 문구뿐 아니라 종료코드와 실패 수를 확인했다.

## 완료된 게이트

- 프런트 lint/typecheck 및 Vitest 29개 통과.
- 프로덕션 Next 빌드 및 실제 서비스 fullstack 테스트 통과.
- 브라우저 일반 e2e 8개 통과 (`node node_modules/@playwright/test/cli.js test --timeout=60000`). 설치된 Browser 플러그인 스킬이 없어 저장소 Playwright CLI를 사용했다.
- 워커 lint/typecheck/build 및 Vitest 76개 통과.
- `docker compose config --quiet` 통과. 배포하지 않았다.
- 독립 아키텍처 검토 CLEAR. 최초 OpenAPI 필드/nullability 불일치 수정 후 재검토했다.
- `backend\gradlew.bat check --no-configuration-cache` 전체 통과 (`BUILD SUCCESSFUL in 2m 57s`). MySQL 호환성 테스트 포함.
- 독립 코드 리뷰 APPROVE. WireMock 응답 fixture 보정 후에도 유효·초과·오류 청크 경계를 직접 테스트함을 재확인했다.

## 해결한 검증 장애

- 최초 전체 `backend\gradlew.bat check`에서 MySQL 자동화 회귀 48개 중 19개가 실패했고, 단독 실행에서도 수집 오류로 `RUNNING` 대신 `HELD`가 재현됐다.
- WireMock의 기본 청크 응답이 이 테스트 환경에서 완료 표지를 보내지 않아 수집기가 안전하게 보류했다. 직접 지정 본문 fixture를 `BODY_FILE` 정책으로 바꾸자 단독 자동화 테스트가 통과했고, 이후 전체 check도 통과했다. 생산 코드의 조기 종료 거부는 유지했다.
- PinnedSourceHttpClient의 청크 본문에 대해 정상 트레일러, 초과 크기, 잘못된 크기·구분자를 직접 검사해 fixture 변경의 맹점을 막았다.
- 독립 content API MySQL 테스트 9개도 통과했다.

남은 Story23 차단 조건은 없다. 자동화 전반의 후속 미구현 범위는 다음 스토리에서 별도 다룬다.
