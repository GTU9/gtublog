import type { Metadata } from "next";

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
    title: q ? `"${q}" search results | GTU BLOG` : "Search | GTU BLOG",
    description: q ? `Public search results related to "${q}".` : "Search public posts.",
    pathname: q ? `/search?q=${encodeURIComponent(q)}` : "/search",
  });
}

export default async function SearchPage({ searchParams }: Props) {
  const { q = "" } = await searchParams;
  const page = q.trim() ? await searchPosts(q, 0, 12) : { items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 };

  return (
    <Shell title="Search" description="Find public posts by keyword, category, or tag.">
      <div className="stack">
        <SearchForm defaultValue={q} />
        <PostGrid
          page={page}
          emptyTitle={q ? "No search results were found." : "Enter a query to begin searching."}
          emptyDescription={
            q ? "Try a different keyword, category, or tag name." : "Use the search form above to look for public topics."
          }
        />
      </div>
    </Shell>
  );
}
