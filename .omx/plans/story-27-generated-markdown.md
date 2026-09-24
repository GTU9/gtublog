# Story 27 — 자동 생성 Markdown을 안전한 HTML로 렌더링

기준: `prototype` 병합 헤드 `d619d51`, Story 23 잔여 범위 중 Markdown. 자동 생성 초안은 현재 Markdown 기호를 일반 문단 안에 이스케이프해 저장하므로 공개 글의 제목·목록·인용·코드·링크가 독자가 기대하는 구조로 보이지 않는다.

## 범위와 결정

- Spring의 자동 발행 경계에서 CommonMark 0.30.0으로 Markdown을 HTML로 렌더링한 뒤 기존 `PostHtmlSanitizer`로 정화한다. `HtmlRenderer.escapeHtml(true)`로 raw HTML을 이스케이프하고 `sanitizeUrls(true)`를 예비 필터로 사용한다. CommonMark의 기본 필터는 `data:` 등을 허용하므로 URL 안전성을 이 단계에 의존하지 않는다. jsoup 허용 목록을 최종 신뢰 경계로 삼아 HTTP(S)/`mailto:` 외 링크를 제거한다. Next는 저장된 안전한 `contentHtml`만 표시한다.
- 기존 자동 발행과 관리자가 보류 초안을 승인하는 경로가 같은 렌더러를 사용한다. 원본 `contentMarkdown`, 출처, 분류, revision, outbox의 원자성을 유지한다. 관리자 수동 글의 `contentHtml` 입력 계약은 변경하지 않는다.
- 관리자 편집 화면의 기존 미리보기 변환기는 Markdown 구조를 표현하지 못하므로, 자동 생성 글의 수정 저장은 Spring이 최종 소유한다. 최초 리비전이 `AUTOMATION`인 글은 Markdown이 그대로면 저장된 HTML을 유지하고, Markdown이 달라지면 같은 안전 렌더러로 다시 만든다. 클라이언트가 함께 보낸 HTML을 이 경로에서 신뢰하지 않는다. 일반 수동 글의 기존 HTML 입력 계약은 유지한다.
- 승인되는 구조는 제목, 문단, 강조, 목록, 인용, 코드 블록, 수평선, HTTP(S)/`mailto:` 링크다. 생성 Markdown의 이미지는 공개 HTML에서 제거하고 alt 텍스트만 남긴다. 이는 현재 공용 정화기가 허용하는 외부 이미지 URL을 통한 독자 추적·혼합 콘텐츠를 막는다. 링크의 rel 정책은 기존 정화기를 따른다. 스크립트·이벤트 핸들러·`javascript:`/`data:` URL, 엔티티·공백으로 변형한 위험 스킴을 차단한다.
- 공개 글 스타일에는 필요한 블록 요소만 보강한다. 이전에 발행된 글은 기존 문단 형태로 계속 표시되고 새 자동 발행/승인 글만 구조화된다. 운영자는 저장된 `contentHtml`과 `contentMarkdown`을 비교해 이전 표현을 식별할 수 있다. 기존 글을 암묵적으로 재작성하지 않으며, 운영 재처리는 별도 승인·마이그레이션 계획이 필요한 범위다.
- 신규 의존성은 Markdown 문법 구현이라는 구체적 기능에 한정한다. BSD-2-Clause 라이선스와 유지 상태를 확인하고, CommonMark 및 전이 의존성을 `backend/gradle.lockfile`에 고정한다. 인프라·스키마는 변경하지 않는다.

## 검증

- 자동 발행과 실제 승인 가능한 보류 초안 override에 대한 MySQL 통합 테스트로 동일한 적대적 Markdown의 구조·정화·저장 본문/revision 일치·공개 조회를 확인한다.
- 자동 생성 글을 관리자가 제목만 수정할 때 HTML 구조가 보존되는지, Markdown을 수정할 때 Spring이 재렌더링하고 리비전·공개 조회에 일치하게 반영하는지 확인한다.
- raw HTML 주입, 위험 링크/이미지 URL, 일반 HTTP(S)/`mailto:` 링크, 변형된 위험 스킴, 코드 리터럴을 테스트한다. 최종 HTML DOM의 태그·속성·프로토콜과 이미지 alt 텍스트를 확인한다.
- 백엔드 check 및 Linux Java 25 CI/보안 스캔, 프런트엔드 lint/typecheck/test/build, worker lint/typecheck/test/build, Playwright, full-stack e2e, Compose 검사를 실행한다. 독립 검증·코드 리뷰 후 한국어 이슈→커밋→PR→CI/보안 검사→`prototype` 병합 순서를 따른다.
