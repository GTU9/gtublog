import type { Metadata } from "next";

import { PostGrid, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getTagPosts, listAllPosts } from "@/src/public-api";

type Props = {
  params: Promise<{ slug: string }>;
};

export const revalidate = 300;

export async function generateStaticParams() {
  const posts = await listAllPosts();
  const slugs = new Set(posts.flatMap((post) => post.tagDetails?.map((item) => item.slug) ?? []));
  return [...slugs].map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  return buildMetadata({
    title: `${slug} tag | GTU BLOG`,
    description: `Browse public posts connected to the ${slug} tag.`,
    pathname: `/tags/${slug}`,
  });
}

export default async function TagPage({ params }: Props) {
  const { slug } = await params;
  const page = await getTagPosts(slug, 0, 12);
  const label = page.items[0]?.tagDetails?.find((item) => item.slug === slug)?.name ?? slug;

  return (
    <Shell title={`${label} tag`} description="Public posts connected to the selected tag.">
      <PostGrid
        page={page}
        emptyTitle="There are no public posts for this tag yet."
        emptyDescription="Tag results will appear here after public posts are published."
      />
    </Shell>
  );
}
