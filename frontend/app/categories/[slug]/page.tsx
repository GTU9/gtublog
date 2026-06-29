import type { Metadata } from "next";

import { PostGrid, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getCategoryPosts, listAllPosts } from "@/src/public-api";

type Props = {
  params: Promise<{ slug: string }>;
};

export const revalidate = 300;

export async function generateStaticParams() {
  const posts = await listAllPosts();
  const slugs = new Set(posts.flatMap((post) => post.categoryDetails?.map((item) => item.slug) ?? []));
  return [...slugs].map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  return buildMetadata({
    title: `${slug} category | GTU BLOG`,
    description: `Browse public posts filed under the ${slug} category.`,
    pathname: `/categories/${slug}`,
  });
}

export default async function CategoryPage({ params }: Props) {
  const { slug } = await params;
  const page = await getCategoryPosts(slug, 0, 12);
  const label = page.items[0]?.categoryDetails?.find((item) => item.slug === slug)?.name ?? slug;

  return (
    <Shell title={`${label} category`} description="Public posts filed under the selected category.">
      <PostGrid
        page={page}
        emptyTitle="There are no public posts in this category yet."
        emptyDescription="Category results will appear here after public posts are published."
      />
    </Shell>
  );
}
