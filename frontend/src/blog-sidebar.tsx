import Link from "next/link";

import { getFreshArchiveEntries, listFreshPosts } from "@/src/public-api";

export async function BlogSidebar() {
  const [recentPage, archiveEntries] = await Promise.all([
    listFreshPosts(0, 5),
    getFreshArchiveEntries(),
  ]);

  const categories = new Map<string, { slug: string; name: string }>();
  const tags = new Map<string, { slug: string; name: string }>();

  for (const post of recentPage.items) {
    for (const category of post.categoryDetails ?? []) {
      categories.set(category.slug, { slug: category.slug, name: category.name });
    }
    for (const tag of post.tagDetails ?? []) {
      tags.set(tag.slug, { slug: tag.slug, name: tag.name });
    }
  }

  const categoryItems = [...categories.values()].slice(0, 8);
  const tagItems = [...tags.values()].slice(0, 10);
  const archiveItems = archiveEntries.slice(0, 6);

  return (
    <div className="sidebar-stack">
      <section className="sidebar-card">
        <h2>블로그 소개</h2>
        <p>
          자동 수집한 자료를 그대로 쌓지 않고, 직접 검토와 정리를 거쳐 다시 읽기 쉬운 글로 남기는 개인 정보
          블로그입니다.
        </p>
      </section>

      <section className="sidebar-card">
        <h2>최근 글</h2>
        {recentPage.items.length > 0 ? (
          <ul className="sidebar-list">
            {recentPage.items.map((post) => (
              <li key={post.id}>
                <Link href={`/posts/${post.slug}`}>{post.title}</Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">아직 표시할 최근 글이 없습니다.</p>
        )}
      </section>

      <section className="sidebar-card">
        <h2>카테고리</h2>
        {categoryItems.length > 0 ? (
          <ul className="sidebar-list sidebar-list-compact">
            {categoryItems.map((category) => (
              <li key={category.slug}>
                <Link href={`/categories/${category.slug}`}>{category.name}</Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">아직 카테고리가 없습니다.</p>
        )}
      </section>

      <section className="sidebar-card">
        <h2>태그</h2>
        {tagItems.length > 0 ? (
          <div className="sidebar-chip-list">
            {tagItems.map((tag) => (
              <Link key={tag.slug} href={`/tags/${tag.slug}`} className="chip">
                {tag.name}
              </Link>
            ))}
          </div>
        ) : (
          <p className="muted">아직 태그가 없습니다.</p>
        )}
      </section>

      <section className="sidebar-card">
        <h2>아카이브</h2>
        {archiveItems.length > 0 ? (
          <ul className="sidebar-list sidebar-list-compact">
            {archiveItems.map((entry) => (
              <li key={`${entry.year}-${entry.month}`}>
                <Link href="/archive">
                  {entry.year}년 {entry.month}월 · {entry.count}개
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">아직 아카이브가 없습니다.</p>
        )}
      </section>
    </div>
  );
}
