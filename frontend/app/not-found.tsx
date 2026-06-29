import { Shell } from "@/src/blog-ui";

export default function NotFound() {
  return (
    <Shell title="Page not found" description="The requested post or public route does not exist.">
      <p className="muted">Check the URL again or return to the home feed to browse recent posts.</p>
    </Shell>
  );
}
