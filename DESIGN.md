# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-07-03
- Primary product surfaces:
  - 공개 블로그 홈, 검색, 분류, 아카이브, 글 상세
  - 관리자 화면은 기능 우선으로 유지하되 공개 블로그와 문체만 분리 유지
- Evidence reviewed:
  - [frontend/src/blog-ui.tsx](D:/study/project/spring/gtublog/frontend/src/blog-ui.tsx)
  - [frontend/app/globals.css](D:/study/project/spring/gtublog/frontend/app/globals.css)
  - [frontend/app/page.tsx](D:/study/project/spring/gtublog/frontend/app/page.tsx)
  - [frontend/app/archive/page.tsx](D:/study/project/spring/gtublog/frontend/app/archive/page.tsx)
  - [frontend/app/search/page.tsx](D:/study/project/spring/gtublog/frontend/app/search/page.tsx)
  - [frontend/app/categories/[slug]/page.tsx](D:/study/project/spring/gtublog/frontend/app/categories/[slug]/page.tsx)
  - [frontend/app/tags/[slug]/page.tsx](D:/study/project/spring/gtublog/frontend/app/tags/[slug]/page.tsx)
  - [frontend/app/posts/[slug]/page.tsx](D:/study/project/spring/gtublog/frontend/app/posts/[slug]/page.tsx)
  - [frontend/src/site.ts](D:/study/project/spring/gtublog/frontend/src/site.ts)

## Brand
- Personality:
  - 차분하고 신뢰감 있는 한국어 정보형 블로그
  - 자동화 기반이지만 사람이 정리한 느낌이 나는 편집형 톤
- Trust signals:
  - 발행일, 조회수, 분류 정보, 검증/출처 안내 문구
  - 과장 없는 소개 문구와 읽기 쉬운 본문 중심 레이아웃
- Avoid:
  - 영어 중심 프로토타입 문구
  - 과도한 스타트업 랜딩페이지 스타일
  - 지나치게 화려한 네온/다크 UI

## Product goals
- Goals:
  - 1인 운영자가 수집·정리한 글을 한국어 사용자에게 익숙한 방식으로 읽히게 한다
  - 홈에서 최신 글을 빠르게 훑고, 검색/분류/아카이브로 자연스럽게 이동하게 한다
  - “자동화 + 검토 + 발행” 성격이 보이되 글 읽는 흐름을 방해하지 않는다
- Non-goals:
  - 커뮤니티형 상호작용
  - 대형 미디어 포털형 복합 레이아웃
  - 화려한 브랜딩 애니메이션
- Success signals:
  - 한국어 UI 일관성
  - 홈/상세/검색/분류 페이지의 빠른 정보 탐색
  - 모바일에서도 블로그 글 읽기 경험이 자연스러움

## Personas and jobs
- Primary personas:
  - 운영자 본인: 자동 수집 결과를 읽고 검토하며 정리된 기록을 남기고 싶음
  - 공개 독자: 기술/정보 글을 빠르게 훑고 필요한 글을 다시 찾고 싶음
- User jobs:
  - 최신 글 확인
  - 키워드 검색
  - 카테고리/태그 기준 탐색
  - 상세 글 집중 읽기
- Key contexts of use:
  - 데스크톱 브라우저의 장시간 읽기
  - 모바일에서 짧은 탐색과 재방문

## Information architecture
- Primary navigation:
  - 홈
  - 아카이브
  - 검색
  - RSS
- Core routes/screens:
  - 홈 피드
  - 검색 결과
  - 카테고리/태그 목록
  - 글 상세
  - 월별 아카이브
- Content hierarchy:
  - 사이트 소개
  - 최신 글/탐색 도구
  - 글 카드 핵심 정보
  - 글 본문
  - 출처/관련 글 보조 정보

## Design principles
- Principle 1:
  - “읽기 쉬움 우선” — 카드와 본문은 장식보다 가독성을 우선한다
- Principle 2:
  - “한국 블로그의 익숙함” — 밝은 배경, 단정한 경계선, 여백 중심 정보 배치
- Tradeoffs:
  - 시각 임팩트보다 장기 독서 피로 감소를 우선
  - 관리자 화면과 공개 화면의 톤은 분리하되 컴포넌트 구조는 최대한 공유

## Visual language
- Color:
  - 기본은 밝은 배경, 흰 카드, 회색 경계선, 녹색/청록 계열 포인트
- Typography:
  - 시스템 한글 친화 폰트 스택 사용
  - 제목은 선명하고, 본문은 16~18px 읽기 편한 줄간격 유지
- Spacing/layout rhythm:
  - 카드형 섹션 간 16~24px 간격
  - 본문 폭은 과도하게 넓지 않게 제한
- Shape/radius/elevation:
  - 라운드는 과하지 않게 12~18px
  - 그림자는 옅게, 경계선으로 구조 인지
- Motion:
  - 기본 hover/focus만 사용, 강한 모션 지양
- Imagery/iconography:
  - 아이콘 없이도 정보 구조가 읽히는 텍스트 중심 UI

## Components
- Existing components to reuse:
  - Shell
  - SearchForm
  - PostGrid / PostCard
  - TaxonomyList
  - ArchiveList
  - EmptyState
- New/changed components:
  - 공개 블로그 헤더를 한국형 블로그 헤더로 개편
  - 홈 소개 패널을 “블로그 소개 + 탐색 가이드” 성격으로 조정
  - 포스트 카드를 목록형 읽기 카드 톤으로 조정
- Variants and states:
  - 빈 상태 문구는 모두 한국어
  - 카테고리/태그는 칩형으로 유지하되 대비 완화
- Token/component ownership:
  - 전역 스타일은 [frontend/app/globals.css](D:/study/project/spring/gtublog/frontend/app/globals.css)에서 관리

## Accessibility
- Target standard:
  - WCAG AA에 준하는 대비와 키보드 접근성
- Keyboard/focus behavior:
  - 스킵 링크 유지
  - 링크/버튼 포커스 링 명확화
- Contrast/readability:
  - 밝은 배경에서 충분한 텍스트 대비 확보
- Screen-reader semantics:
  - 내비게이션, 검색, 본문, 보조 섹션 라벨 유지
- Reduced motion and sensory considerations:
  - 비필수 애니메이션 최소화

## Responsive behavior
- Supported breakpoints/devices:
  - 모바일 우선, 태블릿/데스크톱 확장
- Layout adaptations:
  - 좁은 화면에서는 단일 컬럼
  - 넓은 화면에서도 본문 폭은 과도하게 확장하지 않음
- Touch/hover differences:
  - 칩/버튼 터치 영역 40px 이상 확보

## Interaction states
- Loading:
  - 현재는 SSR 중심으로 별도 로딩 장식 최소화
- Empty:
  - “아직 글이 없습니다” 식의 자연스러운 한국어 안내
- Error:
  - 찾을 수 없음, 검색 결과 없음 등을 친절한 한국어로 표기
- Success:
  - 직접 성공 배너보다 콘텐츠 노출 자체로 결과를 보여줌
- Disabled:
  - 공개 화면에서는 사용 빈도 낮음
- Offline/slow network, if applicable:
  - 공개 API 실패 시 빈 상태라도 한국어 안내 유지

## Content voice
- Tone:
  - 설명형, 차분함, 과장 없음
- Terminology:
  - Home -> 홈
  - Archive -> 아카이브
  - Search -> 검색
  - Categories -> 카테고리
  - Tags -> 태그
  - Related posts -> 함께 보면 좋은 글
- Microcopy rules:
  - 짧고 자연스러운 한국어
  - “public”, “prototype” 같은 내부 관점 단어 지양

## Implementation constraints
- Framework/styling system:
  - Next.js App Router + 전역 CSS
- Design-token constraints:
  - 새 의존성 추가 없이 기존 CSS 구조 확장
- Performance constraints:
  - 이미지/애니메이션 없이 가벼운 렌더링 유지
- Compatibility constraints:
  - SSR/SEO 메타데이터 유지
- Test/screenshot expectations:
  - 공개 홈, 검색, 분류, 상세, 아카이브를 로컬 브라우저로 확인

## Open questions
- [ ] 추후 운영자 프로필/소개 섹션을 홈에 추가할지 결정 필요 / owner: 운영자 / impact: 홈 정보 구조 확장
- [ ] 썸네일 기반 카드형 목록이 필요한지 검토 / owner: 운영자 / impact: 콘텐츠 탐색 방식 변화
