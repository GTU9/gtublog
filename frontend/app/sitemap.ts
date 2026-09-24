import type { MetadataRoute } from "next";

import { listSitemapPosts } from "@/src/public-api";
import { absoluteUrl } from "@/src/site";

export const dynamic = "force-dynamic";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const posts = await listSitemapPosts();

  const categorySlugs = new Set(posts.flatMap((post) => post.categoryDetails?.map((item) => item.slug) ?? []));
  const tagSlugs = new Set(posts.flatMap((post) => post.tagDetails?.map((item) => item.slug) ?? []));

  return [
    {
      url: absoluteUrl("/"),
      changeFrequency: "hourly",
      priority: 1,
      lastModified: new Date(),
    },
    {
      url: absoluteUrl("/archive"),
      changeFrequency: "daily",
      priority: 0.8,
      lastModified: new Date(),
    },
    ...posts.map((post) => ({
      url: absoluteUrl(`/posts/${post.slug}`),
      changeFrequency: "daily" as const,
      priority: 0.9,
      lastModified: post.firstPublishedAt ? new Date(post.firstPublishedAt) : new Date(),
    })),
    ...[...categorySlugs].map((slug) => ({
      url: absoluteUrl(`/categories/${slug}`),
      changeFrequency: "daily" as const,
      priority: 0.7,
      lastModified: new Date(),
    })),
    ...[...tagSlugs].map((slug) => ({
      url: absoluteUrl(`/tags/${slug}`),
      changeFrequency: "daily" as const,
      priority: 0.6,
      lastModified: new Date(),
    })),
  ];
}

