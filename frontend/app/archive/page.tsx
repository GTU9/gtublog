import { BlogSidebar } from "@/src/blog-sidebar";
import { ArchiveList, PostGrid, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getArchivePosts, getFreshArchiveEntries } from "@/src/public-api";
import { pageIndex } from "@/src/pagination";
import { notFound } from "next/navigation";

export const metadata = buildMetadata({
  title: "아카이브 | 지튜 블로그",
  description: "발행된 글을 월별로 모아보는 아카이브입니다.",
  pathname: "/archive",
});

export default async function ArchivePage({ searchParams }: { searchParams: Promise<{ year?: string; month?: string; page?: string | string[] }> }) {
  const query = await searchParams;
  if (query.year !== undefined || query.month !== undefined) {
    if (!/^\d{4}$/.test(query.year ?? "") || !/^(?:[1-9]|1[0-2])$/.test(query.month ?? "")) notFound();
    const year = Number(query.year);
    const month = Number(query.month);
    if (year < 1000 || year > 9998) notFound();
    const posts = await getArchivePosts(year, month, pageIndex(query.page));
    return <Shell title={`${year}년 ${month}월 아카이브`} description="선택한 달에 발행된 글입니다." aside={<BlogSidebar />}>
      <PostGrid page={posts} basePath={`/archive?year=${year}&month=${month}`} emptyTitle="이 달에는 공개된 글이 없습니다." emptyDescription="다른 달의 아카이브를 확인해 주세요." />
    </Shell>;
  }
  const entries = await getFreshArchiveEntries();

  return (
    <Shell title="아카이브" description="발행된 글을 월별로 차분하게 다시 훑어볼 수 있습니다." aside={<BlogSidebar />}>
      <ArchiveList entries={entries} />
    </Shell>
  );
}
