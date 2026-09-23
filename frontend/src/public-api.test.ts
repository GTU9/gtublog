import { afterEach, describe, expect, it, vi } from "vitest";
import { listSitemapPosts } from "./public-api";

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe("complete sitemap enumeration", () => {
  it("includes posts beyond 500 and deduplicates boundary entries", async () => {
    const fetch = vi.fn(async (url: string) => {
      const page = Number(new URL(url).searchParams.get("page"));
      const items = Array.from({ length: 50 }, (_, index) => ({ id: page * 50 + index, slug: `post-${page * 50 + index}` }));
      if (page === 11) items.push({ id: 499, slug: "post-499" });
      return Response.json({ page, size: 50, totalPages: 12, totalElements: 600, items });
    });
    vi.stubGlobal("fetch", fetch);
    const posts = await listSitemapPosts();
    expect(posts).toHaveLength(600);
    expect(posts.at(-1)?.id).toBe(599);
    expect(fetch).toHaveBeenCalledTimes(12);
  });
  it("does not silently publish partial results on later-page failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ page: 0, totalPages: 2, totalElements: 2, items: [{ id: 1 }] })).mockResolvedValueOnce(new Response(null, { status: 503 })));
    await expect(listSitemapPosts()).rejects.toThrow("unavailable");
  });
  it("does not substitute mock posts on production failure", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    await expect(listSitemapPosts()).rejects.toThrow("offline");
  });
  it("rejects a changed count during export", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(Response.json({ page: 0, totalPages: 2, totalElements: 2, items: [{ id: 1 }] }))
      .mockResolvedValueOnce(Response.json({ page: 1, totalPages: 2, totalElements: 3, items: [{ id: 2 }] })));
    await expect(listSitemapPosts()).rejects.toThrow("changed during export");
  });
  it("rejects offset drift that repeats a post and skips another", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(Response.json({ page: 0, totalPages: 2, totalElements: 2, items: [{ id: 1 }] }))
      .mockResolvedValueOnce(Response.json({ page: 1, totalPages: 2, totalElements: 2, items: [{ id: 1 }] })));
    await expect(listSitemapPosts()).rejects.toThrow("changed during export");
  });
  it("terminates for an empty site", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ page: 0, totalPages: 0, totalElements: 0, items: [] }));
    vi.stubGlobal("fetch", fetch);
    expect(await listSitemapPosts()).toEqual([]);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
