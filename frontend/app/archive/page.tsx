import { ArchiveList, Shell } from "@/src/blog-ui";
import { buildMetadata } from "@/src/metadata";
import { getArchiveEntries } from "@/src/public-api";

export const metadata = buildMetadata({
  title: "Archive | GTU BLOG",
  description: "Browse published posts grouped into monthly archive buckets.",
  pathname: "/archive",
});

export const revalidate = 300;

export default async function ArchivePage() {
  const entries = await getArchiveEntries();

  return (
    <Shell title="Archive" description="Review publishing activity across monthly archive groups.">
      <ArchiveList entries={entries} />
    </Shell>
  );
}
