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

    void fetchAdminPosts(auth.authenticatedFetch).then(setPage).catch(() => setError("글 목록을 불러올 수 없습니다."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="글 관리"
        description="현재 글 상태를 확인하고 편집기 진입과 발행 상태 전환을 한곳에서 처리합니다."
        action={
          <Link href="/admin/posts/new" className="primary-link-button">
            새 글 작성
          </Link>
        }
      />
      {error ? <MessageCard title="글 목록을 불러올 수 없습니다" description={error} tone="error" /> : null}
      <AdminPostsTable page={page} />
    </section>
  );
}
