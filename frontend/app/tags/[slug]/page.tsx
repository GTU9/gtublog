import type { Metadata } from "next";

import { BlogSidebar } from "@/src/blog-sidebar";
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
    title: `${slug} 태그 | 지튜 블로그`,
    description: `${slug} 태그와 연결된 공개 글을 모아봅니다.`,
    pathname: `/tags/${slug}`,
  });
}

export default async function TagPage({ params }: Props) {
  const { slug } = await params;
  const page = await getTagPosts(slug, 0, 12);
  const label = page.items[0]?.tagDetails?.find((item) => item.slug === slug)?.name ?? slug;

  return (
    <Shell title={`${label} 태그`} description="비슷한 주제의 글을 태그 기준으로 빠르게 찾아볼 수 있습니다." aside={<BlogSidebar />}>
      <PostGrid
        page={page}
        emptyTitle="이 태그에는 아직 글이 없습니다."
        emptyDescription="관련 글이 발행되면 이곳에서 태그별로 모아볼 수 있습니다."
      />
    </Shell>
  );
}
