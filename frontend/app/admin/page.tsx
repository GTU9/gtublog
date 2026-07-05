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
    ]).catch(() => setError("관리자 대시보드를 지금 불러올 수 없습니다."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="대시보드"
        description="현재 글 현황, 분류 구성, 최근 운영 활동을 한눈에 확인합니다."
      />
      {error ? <MessageCard title="대시보드를 불러올 수 없습니다" description={error} tone="error" /> : null}
      {posts || audit ? (
        <DashboardStats posts={posts} categories={categories} tags={tags} audit={audit} />
      ) : (
        <LoadingCard message="관리자 대시보드를 불러오는 중입니다..." />
      )}
    </section>
  );
}
