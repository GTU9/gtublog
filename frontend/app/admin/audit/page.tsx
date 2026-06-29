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

    void fetchAuditEntries(auth.authenticatedFetch).then(setPage).catch(() => setError("Unable to load audit entries."));
  }, [auth]);

  return (
    <section className="stack">
      <AdminPageHeader
        title="Audit log"
        description="Inspect recent authentication, taxonomy, and post mutations with recorded details."
      />
      {error ? <MessageCard title="Audit unavailable" description={error} tone="error" /> : null}
      <AuditTable page={page} />
    </section>
  );
}
