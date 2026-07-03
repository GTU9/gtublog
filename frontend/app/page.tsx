import { BlogSidebar } from "@/src/blog-sidebar";
import { PostGrid, SearchForm, Shell } from "@/src/blog-ui";
import { defaultMetadata } from "@/src/metadata";
import { listFreshPosts } from "@/src/public-api";
import { siteDescription, siteName, siteTagline } from "@/src/site";

export const metadata = defaultMetadata();

export default async function HomePage() {
  const page = await listFreshPosts(0, 12);

  return (
    <Shell title={siteName} description={siteDescription} aside={<BlogSidebar />}>
      <section className="stack">
        <div className="intro-panel">
          <p className="eyebrow">최신 글</p>
          <h2>{siteTagline}</h2>
          <p>자동으로 수집한 정보에 직접 검토와 정리를 더해, 읽기 쉬운 한국어 글로 쌓아가는 블로그입니다.</p>
        </div>
        <SearchForm />
        <PostGrid
          page={page}
          emptyTitle="아직 공개된 글이 없습니다."
          emptyDescription="첫 발행 글이 올라오면 이곳에서 최신 글을 바로 볼 수 있습니다."
        />
      </section>
    </Shell>
  );
}
