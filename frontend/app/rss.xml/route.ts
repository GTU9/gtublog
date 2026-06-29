import { listAllPosts } from "@/src/public-api";
import { absoluteUrl, siteDescription, siteName, siteUrl } from "@/src/site";

function xmlEscape(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

export async function GET() {
  const posts = await listAllPosts();

  const items = posts
    .map(
      (post) => `
        <item>
          <title>${xmlEscape(post.title)}</title>
          <description>${xmlEscape(post.excerpt)}</description>
          <link>${absoluteUrl(`/posts/${post.slug}`)}</link>
          <guid>${absoluteUrl(`/posts/${post.slug}`)}</guid>
          <pubDate>${post.firstPublishedAt ? new Date(post.firstPublishedAt).toUTCString() : new Date().toUTCString()}</pubDate>
        </item>`,
    )
    .join("");

  const rss = `<?xml version="1.0" encoding="UTF-8" ?>
<rss version="2.0">
  <channel>
    <title>${xmlEscape(siteName)}</title>
    <description>${xmlEscape(siteDescription)}</description>
    <link>${siteUrl}</link>
    ${items}
  </channel>
</rss>`;

  return new Response(rss, {
    headers: {
      "Content-Type": "application/xml; charset=utf-8",
    },
  });
}

