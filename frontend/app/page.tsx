import { siteDescription, siteName } from "@/src/site";

export default function HomePage() {
  return (
    <main>
      <p className="eyebrow">GTU BLOG</p>
      <h1>{siteName}</h1>
      <p>{siteDescription}</p>
      <p className="status">Phase 0 workspace is ready.</p>
    </main>
  );
}
