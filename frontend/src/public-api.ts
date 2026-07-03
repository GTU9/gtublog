import type { ArchiveEntry, PostDetail, PostPage, PostSummary } from "@/src/content-types";
import {
  mockArchiveEntries,
  mockCategoryPage,
  mockPostBySlug,
  mockPostsPage,
  mockPostSummaries,
  mockSearchPage,
  mockTagPage,
} from "@/src/mock-content";
import { defaultRevalidateSeconds, isDevelopmentRuntime, publicApiBaseUrl } from "@/src/site";

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

