import Link from "next/link";
import type { ReactNode } from "react";

import type { ArchiveEntry, PostPage, PostSummary, TaxonomyItem } from "@/src/content-types";

function formatDate(value: string | null) {
  if (!value) {
    return "발행 예정";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "numeric",
  }).format(new Date(value));
}

export function Shell({
  title,
  description,
  aside,
  children,
}: {
  title?: string;
  description?: string;
  aside?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="shell">
      <header className="site-header">
        <div className="site-branding">
          <span className="site-badge">자동화 큐레이션 블로그</span>
          <Link href="/" className="brand">
            지튜 블로그
          </Link>
          <p className="site-copy">자동 수집한 정보를 검토하고 정리해 한국어로 기록하는 개인 운영 블로그입니다.</p>
        </div>
        <nav aria-label="공개 블로그 내비게이션" className="site-nav">
          <Link href="/">홈</Link>
          <Link href="/archive">아카이브</Link>
          <Link href="/search">검색</Link>
          <Link href="/rss.xml">RSS</Link>
        </nav>
      </header>

      <main id="main-content" className="page">
        {(title || description) && (
          <section className="hero">
            {title ? <h1>{title}</h1> : null}
            {description ? <p>{description}</p> : null}
          </section>
        )}

        {aside ? (
          <div className="content-layout">
            <section className="content-main">{children}</section>
            <aside className="content-aside" aria-label="블로그 부가 정보">
              {aside}
            </aside>
          </div>
        ) : (
          children
        )}
      </main>

      <footer className="site-footer">
        <p>자동 수집과 수동 검토를 함께 사용하는 기록형 블로그입니다.</p>
      </footer>
    </div>
  );
}

export function SearchForm({ defaultValue = "" }: { defaultValue?: string }) {
  return (
    <form action="/search" method="get" className="search-form" role="search" aria-label="글 검색">
      <label htmlFor="search-query" className="sr-only">
        검색어
      </label>
      <input id="search-query" name="q" type="search" placeholder="제목, 카테고리, 태그, 키워드로 검색" defaultValue={defaultValue} />
      <button type="submit">검색</button>
    </form>
  );
}

export function PostGrid({
  page,
  emptyTitle,
  emptyDescription,
}: {
  page: PostPage<PostSummary>;
  emptyTitle: string;
  emptyDescription: string;
}) {
  if (page.items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <>
      <section className="card-grid" aria-label="글 목록">
        {page.items.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </section>
      <Pagination currentPage={page.page} totalPages={page.totalPages} />
    </>
  );
}

export function PostCard({ post }: { post: PostSummary }) {
  return (
    <article className="post-card">
      <div className="card-meta">
        <span>{formatDate(post.firstPublishedAt)}</span>
        <span>조회 {post.viewCount}</span>
      </div>
      <h2>
        <Link href={`/posts/${post.slug}`}>{post.title}</Link>
      </h2>
      <p>{post.excerpt}</p>
      <TaxonomyList items={post.categoryDetails ?? []} label="카테고리" getHref={(item) => `/categories/${item.slug}`} />
      <TaxonomyList items={post.tagDetails ?? []} label="태그" getHref={(item) => `/tags/${item.slug}`} />
      <div className="post-card-action">
        <Link href={`/posts/${post.slug}`}>자세히 읽기</Link>
      </div>
    </article>
  );
}

export function TaxonomyList({
  items,
  label,
  getHref,
}: {
  items: TaxonomyItem[];
  label: string;
  getHref: (item: TaxonomyItem) => string;
}) {
  if (items.length === 0) {
    return null;
  }

  return (
    <div className="taxonomy-block">
      <span className="taxonomy-label">{label}</span>
      <ul className="chip-list">
        {items.map((item) => (
          <li key={`${label}-${item.id}`}>
            <Link href={getHref(item)} className="chip">
              {item.name}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Pagination({
  currentPage,
  totalPages,
}: {
  currentPage: number;
  totalPages: number;
}) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav className="pagination" aria-label="페이지 이동">
      <span>
        {currentPage + 1} / {totalPages} 페이지
      </span>
    </nav>
  );
}

export function ArchiveList({ entries }: { entries: ArchiveEntry[] }) {
  if (entries.length === 0) {
    return <EmptyState title="아직 아카이브가 없습니다." description="발행된 글이 쌓이면 월별 아카이브가 이곳에 표시됩니다." />;
  }

  return (
    <section className="stack" aria-label="월별 아카이브">
      {entries.map((entry) => (
        <article className="archive-card" key={`${entry.year}-${entry.month}`}>
          <h2>
            {entry.year}년 {entry.month}월
          </h2>
          <p>발행 글 {entry.count}개</p>
        </article>
      ))}
    </section>
  );
}

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <section className="empty-state" aria-live="polite">
      <h2>{title}</h2>
      <p>{description}</p>
    </section>
  );
}
