"use client";

import { useEffect, useState } from "react";

import { fetchAuditEntries } from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, AuditTable, MessageCard } from "@/src/admin-ui";
import type { AuditPage } from "@/src/admin-types";

export default function AdminAuditPage() {
  const auth = useAdminAuth();
  const [page, setPage] = useState<AuditPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    void fetchAuditEntries(auth.authenticatedFetch).then(setPage).catch(() => setError("감사 로그를 불러올 수 없습니다."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="감사 로그"
        description="로그인, 분류 변경, 글 수정 이력을 세부 기록과 함께 확인합니다."
      />
      {error ? <MessageCard title="감사 로그를 불러올 수 없습니다" description={error} tone="error" /> : null}
      <AuditTable page={page} />
    </section>
  );
}
