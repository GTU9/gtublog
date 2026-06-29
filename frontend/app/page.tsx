import { PostGrid, SearchForm, Shell } from "@/src/blog-ui";
import { defaultMetadata } from "@/src/metadata";
import { listPosts } from "@/src/public-api";
import { siteDescription, siteName, siteTagline } from "@/src/site";

export const metadata = defaultMetadata();
export const revalidate = 300;

export default async function HomePage() {
  const page = await listPosts(0, 12);

  return (
    <Shell title={siteName} description={siteDescription}>
      <section className="stack">
        <div className="intro-panel">
          <p className="eyebrow">PUBLIC FEED</p>
          <h2>{siteTagline}</h2>
          <p>Browse public posts that combine automated collection with human review and publishing control.</p>
        </div>
        <SearchForm />
        <PostGrid
          page={page}
          emptyTitle="No public posts are available yet."
          emptyDescription="Recent public posts will appear here once the backend public API is available."
        />
      </section>
    </Shell>
  );
}
