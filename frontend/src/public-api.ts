import type { ArchiveEntry, PostDetail, PostPage, PostSummary } from "@/src/content-types";
import {
  mockArchiveEntries,
  mockCategoryPage,
  mockPostBySlug,
  mockPostsPage,
  mockPostSummaries,
  mockSearchPage,
  mockTagPage,
} from "./mock-content";
import { defaultRevalidateSeconds, isDevelopmentRuntime, publicApiBaseUrl } from "./site";

type RequestOptions<T> = {
  fallback: T;
  devFallback?: T;
  fresh?: boolean;
};

async function requestJson<T>(path: string, { fallback, devFallback, fresh }: RequestOptions<T>): Promise<T> {
  const url = `${publicApiBaseUrl}${path}`;

  try {
    const response = await fetch(url, {
      ...(fresh ? { cache: "no-store" as const } : { next: { revalidate: defaultRevalidateSeconds } }),
      signal: AbortSignal.timeout(1500),
    });

    if (!response.ok) {
      return fallback;
    }

    return (await response.json()) as T;
  } catch {
    if (isDevelopmentRuntime() && devFallback !== undefined) {
      return devFallback;
    }

    return fallback;
  }
}

export async function listPosts(page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/posts?page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: mockPostsPage(page, size),
  });
}

export async function listFreshPosts(page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/posts?page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: mockPostsPage(page, size),
    fresh: true,
  });
}

export async function getPost(slug: string) {
  return requestJson<PostDetail | null>(`/posts/${encodeURIComponent(slug)}`, {
    fallback: null,
    devFallback: mockPostBySlug(slug),
  });
}

export async function searchPosts(query: string, page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: mockSearchPage(query, page, size),
  });
}

export async function getCategoryPosts(slug: string, page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/categories/${encodeURIComponent(slug)}?page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: mockCategoryPage(slug, page, size),
  });
}

export async function getTagPosts(slug: string, page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/tags/${encodeURIComponent(slug)}?page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: mockTagPage(slug, page, size),
  });
}

export async function getArchiveEntries() {
  return requestJson<ArchiveEntry[]>("/archive", {
    fallback: [],
    devFallback: mockArchiveEntries(),
  });
}

export async function getFreshArchiveEntries() {
  return requestJson<ArchiveEntry[]>("/archive", {
    fallback: [],
    devFallback: mockArchiveEntries(),
    fresh: true,
  });
}

export async function getArchivePosts(year: number, month: number, page = 0, size = 12) {
  return requestJson<PostPage<PostSummary>>(`/archive/${year}/${month}?page=${page}&size=${size}`, {
    fallback: { items: [], page, size, totalElements: 0, totalPages: 0 },
    devFallback: (() => {
      const matches = mockPostSummaries.filter((post) => post.firstPublishedAt?.startsWith(`${year}-${String(month).padStart(2, "0")}-`));
      return { items: matches.slice(page * size, (page + 1) * size), page, size, totalElements: matches.length, totalPages: Math.ceil(matches.length / size) };
    })(),
  });
}

// Sitemap export must fail on unavailable pages rather than silently publish a partial index.
export async function listSitemapPosts() {
  async function readPage(page: number): Promise<PostPage<PostSummary>> {
    const response = await fetch(`${publicApiBaseUrl}/posts?page=${page}&size=50`, {
      next: { revalidate: defaultRevalidateSeconds }, signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error("Sitemap source is unavailable.");
    const result = await response.json() as PostPage<PostSummary>;
    if (!Number.isSafeInteger(result.totalPages) || result.totalPages < 0 || !Number.isSafeInteger(result.totalElements) || result.totalElements < 0 || !Array.isArray(result.items) || result.page !== page) {
      throw new Error("Sitemap source returned an invalid page.");
    }
    return result;
  }
  let first: PostPage<PostSummary>;
  try { first = await readPage(0); }
  catch (error) {
    if (isDevelopmentRuntime()) return mockPostSummaries;
    throw error;
  }
  const posts = new Map(first.items.map((post) => [post.id, post]));
  for (let index = 1; index < first.totalPages; index++) {
    const page = await readPage(index);
    if (page.items.length === 0 || page.totalElements !== first.totalElements || page.totalPages !== first.totalPages) throw new Error("Sitemap source changed during export; retry required.");
    for (const post of page.items) posts.set(post.id, post);
  }
  if (posts.size !== first.totalElements) throw new Error("Sitemap source changed during export; retry required.");
  return [...posts.values()];
}

export async function listAllPosts(maxPages = 10, size = 50) {
  const firstPage = await listPosts(0, size);
  const pages = [firstPage];
  const totalPages = Math.min(Math.max(firstPage.totalPages, 1), maxPages);

  for (let page = 1; page < totalPages; page += 1) {
    pages.push(await listPosts(page, size));
  }

  return pages.flatMap((page) => page.items);
}

export async function listMockOrFetchedSummaries() {
  const posts = await listAllPosts();
  return posts.length > 0 ? posts : mockPostSummaries;
}

