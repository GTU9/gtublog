"use client";

import { useEffect, useState } from "react";

import { fetchAdminPosts, fetchAuditEntries, fetchCategories, fetchTags } from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, DashboardStats, LoadingCard, MessageCard } from "@/src/admin-ui";
import type { AdminPostPage, AuditPage, TaxonomyResponse } from "@/src/admin-types";

export default function AdminDashboardPage() {
  const auth = useAdminAuth();
  const [posts, setPosts] = useState<AdminPostPage | null>(null);
  const [categories, setCategories] = useState<TaxonomyResponse[]>([]);
  const [tags, setTags] = useState<TaxonomyResponse[]>([]);
  const [audit, setAudit] = useState<AuditPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    void Promise.all([
      fetchAdminPosts(auth.authenticatedFetch).then(setPosts),
      fetchCategories(auth.authenticatedFetch).then(setCategories),
      fetchTags(auth.authenticatedFetch).then(setTags),
      fetchAuditEntries(auth.authenticatedFetch).then(setAudit),
    ]).catch(() => setError("Unable to load the administrator dashboard right now."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="Dashboard"
        description="Review the current content inventory, taxonomy health, and recent administrative activity."
      />
      {error ? <MessageCard title="Dashboard unavailable" description={error} tone="error" /> : null}
      {posts || audit ? (
        <DashboardStats posts={posts} categories={categories} tags={tags} audit={audit} />
      ) : (
        <LoadingCard message="Loading administrator dashboard..." />
      )}
    </section>
  );
}
