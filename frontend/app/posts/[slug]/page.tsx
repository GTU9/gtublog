import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

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
      title: "Post not found",
      description: siteDescription,
      pathname: `/posts/${slug}`,
    });
  }

  return buildMetadata({
    title: `${post.title} | GTU BLOG`,
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
    <Shell title={post.title} description={post.excerpt}>
      <article className="post-detail">
        <div className="card-meta">
          <span>{post.firstPublishedAt ? new Date(post.firstPublishedAt).toLocaleDateString("en-US") : "Scheduled"}</span>
          <span>{post.viewCount} views</span>
        </div>
        <TaxonomyList items={post.categories} label="Categories" getHref={(item) => `/categories/${item.slug}`} />
        <TaxonomyList items={post.tags} label="Tags" getHref={(item) => `/tags/${item.slug}`} />
        <section
          className="content-render"
          aria-label="Post content"
          dangerouslySetInnerHTML={{ __html: post.contentHtml }}
        />
        <section className="stack">
          <h2>Source links</h2>
          <EmptyState
            title="No source links are available for this post yet."
            description="Verified source links will be shown here once source-backed publication is connected."
          />
        </section>
        <section className="stack">
          <h2>Related posts</h2>
          {post.relatedPosts.length > 0 ? (
            <ul className="related-list">
              {post.relatedPosts.map((related) => (
                <li key={related.id}>
                  <Link href={`/posts/${related.slug}`}>{related.title}</Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No related posts are available yet.</p>
          )}
        </section>
      </article>
    </Shell>
  );
}
