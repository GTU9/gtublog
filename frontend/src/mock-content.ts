import type { ArchiveEntry, PostDetail, PostPage, PostSummary, TaxonomyItem } from "@/src/content-types";

const developmentCategory: TaxonomyItem = {
  id: 1,
  slug: "development",
  name: "개발",
  description: "스프링, 리액트, 구현 메모",
};

const automationCategory: TaxonomyItem = {
  id: 2,
  slug: "automation",
  name: "자동화",
  description: "수집, 검증, 발행 자동화",
};

const springTag: TaxonomyItem = {
  id: 10,
  slug: "spring-boot",
  name: "Spring Boot",
  description: "Spring Boot 백엔드 아키텍처",
};

const reactTag: TaxonomyItem = {
  id: 11,
  slug: "react",
  name: "React",
  description: "React와 App Router UI",
};

const codexTag: TaxonomyItem = {
  id: 12,
  slug: "codex",
  name: "Codex",
  description: "Codex 기반 자동화 실험",
};

function summaryOf(detail: PostDetail): PostSummary {
  return {
    id: detail.id,
    slug: detail.slug,
    title: detail.title,
    excerpt: detail.excerpt,
    status: detail.status,
    firstPublishedAt: detail.firstPublishedAt,
    viewCount: detail.viewCount,
    categories: detail.categories.map((item) => item.name),
    tags: detail.tags.map((item) => item.name),
    categoryDetails: detail.categories,
    tagDetails: detail.tags,
  };
}

export const mockPostDetails: PostDetail[] = [
  {
    id: 101,
    slug: "spring-boot-automation-blog",
    title: "자동 발행 블로그에 Spring Boot를 선택한 이유",
    excerpt: "자동화, 편집 검토, 공개 발행을 하나의 흐름으로 묶기 위해 설계한 방식을 정리했습니다.",
    contentMarkdown: "",
    contentHtml: `
      <p>이 블로그는 자동 수집과 사람의 편집 검토를 함께 쓰는 개인 발행 시스템으로 설계했습니다.</p>
      <p>백엔드는 Spring Boot, 공개 프론트엔드는 Next.js App Router, 데이터 모델은 MySQL을 중심으로 구성했습니다.</p>
      <p>공개 페이지는 SSR과 주기적 재검증을 사용해 검색 노출성과 최신성을 함께 확보합니다.</p>
    `,
    status: "PUBLISHED",
    firstPublishedAt: "2026-06-29T06:30:00Z",
    createdAt: "2026-06-29T06:00:00Z",
    updatedAt: "2026-06-29T06:25:00Z",
    viewCount: 128,
    categories: [developmentCategory, automationCategory],
    tags: [springTag, codexTag],
    relatedPosts: [],
  },
  {
    id: 102,
    slug: "nextjs-public-blog-experience",
    title: "Next.js App Router로 공개 블로그 경험 만들기",
    excerpt: "피드, 상세, 검색, RSS, 사이트맵까지 공개면 전체를 App Router 흐름에 맞춰 구성했습니다.",
    contentMarkdown: "",
    contentHtml: `
      <p>공개 블로그는 클라이언트 자바스크립트가 실행되기 전에도 의미 있는 HTML을 반환해야 합니다.</p>
      <p>그래서 피드, 상세, 카테고리, 태그, 검색 페이지를 모두 서버 우선으로 유지했습니다.</p>
      <p>메타데이터와 Open Graph 값도 라우트 단위에서 함께 생성합니다.</p>
    `,
    status: "PUBLISHED",
    firstPublishedAt: "2026-06-28T11:00:00Z",
    createdAt: "2026-06-28T10:20:00Z",
    updatedAt: "2026-06-28T10:55:00Z",
    viewCount: 76,
    categories: [developmentCategory],
    tags: [reactTag],
    relatedPosts: [],
  },
  {
    id: 103,
    slug: "automation-source-validation",
    title: "자동 발행보다 먼저 필요한 것은 출처 검증",
    excerpt: "접근 가능성, 중복, 교차 검증을 먼저 통과시켜야 자동화된 발행 품질을 지킬 수 있습니다.",
    contentMarkdown: "",
    contentHtml: `
      <p>자동 콘텐츠 파이프라인은 모델 고도화보다 출처 검증 체계가 먼저 필요합니다.</p>
      <p>단일 출처 주장, 접근 불가 참고문서, 중복 문서는 모두 발행 차단 사유가 되어야 합니다.</p>
      <p>이 원칙이 있어야 제품 수명주기 전반에서 신뢰성, 관측성, 복구 가능성을 높일 수 있습니다.</p>
    `,
    status: "PUBLISHED",
    firstPublishedAt: "2026-06-27T08:10:00Z",
    createdAt: "2026-06-27T07:20:00Z",
    updatedAt: "2026-06-27T08:00:00Z",
    viewCount: 52,
    categories: [automationCategory],
    tags: [codexTag, springTag],
    relatedPosts: [],
  },
];

mockPostDetails[0].relatedPosts = [summaryOf(mockPostDetails[1]), summaryOf(mockPostDetails[2])];
mockPostDetails[1].relatedPosts = [summaryOf(mockPostDetails[0])];
mockPostDetails[2].relatedPosts = [summaryOf(mockPostDetails[0])];

export const mockPostSummaries = mockPostDetails.map(summaryOf);

export function mockPostsPage(page = 0, size = 12): PostPage<PostSummary> {
  const start = page * size;
  const items = mockPostSummaries.slice(start, start + size);
  return {
    items,
    page,
    size,
    totalElements: mockPostSummaries.length,
    totalPages: Math.max(1, Math.ceil(mockPostSummaries.length / size)),
  };
}

export function mockSearchPage(query: string, page = 0, size = 12): PostPage<PostSummary> {
  const lowered = query.trim().toLowerCase();
  const filtered = mockPostSummaries.filter((post) =>
    [post.title, post.excerpt, ...post.categories, ...post.tags].join(" ").toLowerCase().includes(lowered),
  );
  const start = page * size;
  return {
    items: filtered.slice(start, start + size),
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.max(1, Math.ceil(Math.max(filtered.length, 1) / size)),
  };
}

export function mockCategoryPage(slug: string, page = 0, size = 12): PostPage<PostSummary> {
  const filtered = mockPostSummaries.filter((post) =>
    post.categoryDetails?.some((item) => item.slug === slug),
  );
  const start = page * size;
  return {
    items: filtered.slice(start, start + size),
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.max(1, Math.ceil(Math.max(filtered.length, 1) / size)),
  };
}

export function mockTagPage(slug: string, page = 0, size = 12): PostPage<PostSummary> {
  const filtered = mockPostSummaries.filter((post) => post.tagDetails?.some((item) => item.slug === slug));
  const start = page * size;
  return {
    items: filtered.slice(start, start + size),
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.max(1, Math.ceil(Math.max(filtered.length, 1) / size)),
  };
}

export function mockArchiveEntries(): ArchiveEntry[] {
  return [
    { year: 2026, month: 6, count: 3 },
    { year: 2026, month: 5, count: 2 },
  ];
}

export function mockPostBySlug(slug: string) {
  return mockPostDetails.find((post) => post.slug === slug) ?? null;
}
