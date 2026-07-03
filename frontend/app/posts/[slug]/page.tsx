import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BlogSidebar } from "@/src/blog-sidebar";
import { EmptyState, Shell, TaxonomyList } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getPost, listAllPosts } from "@/src/public-api";
import { siteDescription } from "@/src/site";

type Props = {
  params: Promise<{ slug: string }>;
};

export const revalidate = 300;

export async function generateStaticParams() {
  const posts = await listAllPosts();
  return posts.map((post) => ({ slug: post.slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const post = await getPost(slug);

  if (!post) {
    return buildMetadata({
      title: "글을 찾을 수 없음",
      description: siteDescription,
      pathname: `/posts/${slug}`,
    });
  }

  return buildMetadata({
    title: `${post.title} | 지튜 블로그`,
    description: post.excerpt,
    pathname: `/posts/${post.slug}`,
  });
}

export default async function PostDetailPage({ params }: Props) {
  const { slug } = await params;
  const post = await getPost(slug);

  if (!post) {
    notFound();
  }

  return (
    <Shell title={post.title} description={post.excerpt} aside={<BlogSidebar />}>
      <article className="post-detail">
        <div className="card-meta">
          <span>{post.firstPublishedAt ? new Date(post.firstPublishedAt).toLocaleDateString("ko-KR") : "발행 예정"}</span>
          <span>조회 {post.viewCount}</span>
        </div>
        <TaxonomyList items={post.categories} label="카테고리" getHref={(item) => `/categories/${item.slug}`} />
        <TaxonomyList items={post.tags} label="태그" getHref={(item) => `/tags/${item.slug}`} />
        <section
          className="content-render"
          aria-label="글 본문"
          dangerouslySetInnerHTML={{ __html: post.contentHtml }}
        />
        <section className="stack">
          <h2>출처 링크</h2>
          <EmptyState
            title="아직 연결된 출처 링크가 없습니다."
            description="출처 기반 발행 흐름이 연결되면 검증된 링크가 이곳에 함께 표시됩니다."
          />
        </section>
        <section className="stack">
          <h2>함께 보면 좋은 글</h2>
          {post.relatedPosts.length > 0 ? (
            <ul className="related-list">
              {post.relatedPosts.map((related) => (
                <li key={related.id}>
                  <Link href={`/posts/${related.slug}`}>{related.title}</Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">아직 함께 보여줄 관련 글이 없습니다.</p>
          )}
        </section>
      </article>
    </Shell>
  );
}
