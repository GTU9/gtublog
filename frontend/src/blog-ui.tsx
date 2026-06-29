import Link from "next/link";
import type { ReactNode } from "react";

import type { ArchiveEntry, PostPage, PostSummary, TaxonomyItem } from "@/src/content-types";

function formatDate(value: string | null) {
  if (!value) {
    return "Scheduled";
  }

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

export function Shell({
  title,
  description,
  children,
}: {
  title?: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <div className="shell">
      <header className="site-header">
        <div>
          <Link href="/" className="brand">
            GTU BLOG
          </Link>
          <p className="site-copy">A public knowledge blog for automated collection and curated editing.</p>
        </div>
        <nav aria-label="Public blog navigation" className="site-nav">
          <Link href="/">Home</Link>
          <Link href="/archive">Archive</Link>
          <Link href="/search">Search</Link>
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
        {children}
      </main>

      <footer className="site-footer">
        <p>Public blog prototype for verified content and automation-ready publishing.</p>
      </footer>
    </div>
  );
}

export function SearchForm({ defaultValue = "" }: { defaultValue?: string }) {
  return (
    <form action="/search" method="get" className="search-form" role="search" aria-label="Search posts">
      <label htmlFor="search-query" className="sr-only">
        Search query
      </label>
      <input
        id="search-query"
        name="q"
        type="search"
        placeholder="Search by topic, tag, or keyword"
        defaultValue={defaultValue}
      />
      <button type="submit">Search</button>
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
      <section className="card-grid" aria-label="Post list">
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
        <span>{post.viewCount} views</span>
      </div>
      <h2>
        <Link href={`/posts/${post.slug}`}>{post.title}</Link>
      </h2>
      <p>{post.excerpt}</p>
      <TaxonomyList
        items={post.categoryDetails ?? []}
        label="Categories"
        getHref={(item) => `/categories/${item.slug}`}
      />
      <TaxonomyList items={post.tagDetails ?? []} label="Tags" getHref={(item) => `/tags/${item.slug}`} />
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
    <nav className="pagination" aria-label="Pagination">
      <span>
        Page {currentPage + 1} of {totalPages}
      </span>
    </nav>
  );
}

export function ArchiveList({ entries }: { entries: ArchiveEntry[] }) {
  if (entries.length === 0) {
    return (
      <EmptyState
        title="No public archive is available yet."
        description="Archive groups will appear here once public posts are published."
      />
    );
  }

  return (
    <section className="stack" aria-label="Monthly archive">
      {entries.map((entry) => (
        <article className="archive-card" key={`${entry.year}-${entry.month}`}>
          <h2>
            {entry.year} / {entry.month}
          </h2>
          <p>{entry.count} published posts</p>
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
