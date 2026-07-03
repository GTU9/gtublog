import type { Metadata } from "next";

import { BlogSidebar } from "@/src/blog-sidebar";
import { PostGrid, SearchForm, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { searchPosts } from "@/src/public-api";

type Props = {
  searchParams: Promise<{ q?: string }>;
};

export const revalidate = 300;

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const { q = "" } = await searchParams;

  return buildMetadata({
    title: q ? `"${q}" 검색 결과 | 지튜 블로그` : "검색 | 지튜 블로그",
    description: q ? `"${q}"와 관련된 공개 글 검색 결과입니다.` : "공개된 글을 검색합니다.",
    pathname: q ? `/search?q=${encodeURIComponent(q)}` : "/search",
  });
}

export default async function SearchPage({ searchParams }: Props) {
  const { q = "" } = await searchParams;
  const page = q.trim() ? await searchPosts(q, 0, 12) : { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 };

  return (
    <Shell title="검색" description="키워드, 카테고리, 태그로 원하는 글을 찾아보세요." aside={<BlogSidebar />}>
      <div className="stack">
        <SearchForm defaultValue={q} />
        <PostGrid
          page={page}
          emptyTitle={q ? "검색 결과가 없습니다." : "검색어를 입력해 주세요."}
          emptyDescription={
            q ? "다른 키워드나 카테고리, 태그 이름으로 다시 찾아보세요." : "위 검색창에서 궁금한 주제를 입력해 글을 찾아볼 수 있습니다."
          }
        />
      </div>
    </Shell>
  );
}
