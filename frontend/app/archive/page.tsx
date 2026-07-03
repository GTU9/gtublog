import { BlogSidebar } from "@/src/blog-sidebar";
import { ArchiveList, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getFreshArchiveEntries } from "@/src/public-api";

export const metadata = buildMetadata({
  title: "아카이브 | 지튜 블로그",
  description: "발행된 글을 월별로 모아보는 아카이브입니다.",
  pathname: "/archive",
});

export default async function ArchivePage() {
  const entries = await getFreshArchiveEntries();

  return (
    <Shell title="아카이브" description="발행된 글을 월별로 차분하게 다시 훑어볼 수 있습니다." aside={<BlogSidebar />}>
      <ArchiveList entries={entries} />
    </Shell>
  );
}
