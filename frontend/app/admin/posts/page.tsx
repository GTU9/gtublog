"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { fetchAdminPosts } from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, AdminPostsTable, MessageCard } from "@/src/admin-ui";
import type { AdminPostPage } from "@/src/admin-types";

export default function AdminPostsPage() {
  const auth = useAdminAuth();
  const [page, setPage] = useState<AdminPostPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    void fetchAdminPosts(auth.authenticatedFetch).then(setPage).catch(() => setError("Unable to load post inventory."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="Posts"
        description="Inspect current state, open the editor, and manage publication transitions from one place."
        action={
          <Link href="/admin/posts/new" className="primary-link-button">
            New draft
          </Link>
        }
      />
      {error ? <MessageCard title="Posts unavailable" description={error} tone="error" /> : null}
      <AdminPostsTable page={page} />
    </section>
  );
}
