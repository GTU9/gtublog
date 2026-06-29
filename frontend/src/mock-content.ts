import type { ArchiveEntry, PostDetail, PostPage, PostSummary, TaxonomyItem } from "@/src/content-types";

const developmentCategory: TaxonomyItem = {
  id: 1,
  slug: "development",
  name: "Development",
  description: "Spring, React, and implementation notes",
};

const automationCategory: TaxonomyItem = {
  id: 2,
  slug: "automation",
  name: "Automation",
  description: "Collection, validation, and publishing automation",
};

const springTag: TaxonomyItem = {
  id: 10,
  slug: "spring-boot",
  name: "Spring Boot",
  description: "Spring Boot backend architecture",
};

const reactTag: TaxonomyItem = {
  id: 11,
  slug: "react",
  name: "React",
  description: "React and App Router UI",
};

const codexTag: TaxonomyItem = {
  id: 12,
  slug: "codex",
  name: "Codex",
  description: "Codex-based automation experiments",
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
    title: "Why this blog uses Spring Boot for automated publishing",
    excerpt: "A practical overview of combining automation, editorial review, and public delivery.",
    contentMarkdown: "",
    contentHtml: `
      <p>This blog is designed as a personal publishing system that combines automated collection with deliberate editorial review.</p>
      <p>The backend uses Spring Boot, the public frontend uses Next.js App Router, and the data model is grounded in MySQL.</p>
      <p>Public pages use SSR and timed revalidation so that search visibility and freshness can coexist.</p>
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
    title: "Building the public blog experience with Next.js App Router",
    excerpt: "The public feed, post detail, search, RSS, and sitemap all follow App Router conventions.",
    contentMarkdown: "",
    contentHtml: `
      <p>The public blog should return meaningful HTML even before client-side JavaScript runs.</p>
      <p>That means the feed, detail, category, tag, and search pages should remain server-first.</p>
      <p>Metadata and Open Graph values are also generated at the route level.</p>
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
    title: "Why source validation comes before automatic publication",
    excerpt: "Accessibility, duplication, and corroboration checks protect publishing quality before generation ever ships.",
    contentMarkdown: "",
    contentHtml: `
      <p>Automated content pipelines need source validation before they need model sophistication.</p>
      <p>Single-source claims, inaccessible references, and duplicate documents should all block publication.</p>
      <p>That discipline improves trust, observability, and recovery later in the product lifecycle.</p>
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
