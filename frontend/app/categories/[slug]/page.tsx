import type { Metadata } from "next";

import { BlogSidebar } from "@/src/blog-sidebar";
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
    title: `${slug} 카테고리 | 지튜 블로그`,
    description: `${slug} 카테고리에 속한 공개 글을 모아봅니다.`,
    pathname: `/categories/${slug}`,
  });
}

export default async function CategoryPage({ params }: Props) {
  const { slug } = await params;
  const page = await getCategoryPosts(slug, 0, 12);
  const label = page.items[0]?.categoryDetails?.find((item) => item.slug === slug)?.name ?? slug;

  return (
    <Shell title={`${label} 카테고리`} description="선택한 카테고리에 포함된 글을 한 번에 둘러볼 수 있습니다." aside={<BlogSidebar />}>
      <PostGrid
        page={page}
        emptyTitle="이 카테고리에는 아직 글이 없습니다."
        emptyDescription="관련 글이 발행되면 이곳에서 카테고리별로 모아볼 수 있습니다."
      />
    </Shell>
  );
}
