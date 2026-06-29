"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { AuthProvider, useAdminAuth } from "@/src/admin-auth";

function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const auth = useAdminAuth();

  useEffect(() => {
    if (auth.status === "unauthenticated" && pathname !== "/admin/login") {
      router.replace("/admin/login");
    }
  }, [auth.status, pathname, router]);

  if (pathname === "/admin/login") {
    return <>{children}</>;
  }

  if (auth.status !== "authenticated") {
    return <main className="admin-panel">Loading administrator session...</main>;
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div>
          <p className="eyebrow">ADMIN</p>
          <h1>{auth.admin?.displayName ?? "Administrator"}</h1>
          <p className="muted">{auth.admin?.username}</p>
        </div>
        <nav className="admin-nav" aria-label="Administrator navigation">
          <Link href="/admin">Dashboard</Link>
          <Link href="/admin/automation">Automation</Link>
          <Link href="/admin/posts">Posts</Link>
          <Link href="/admin/taxonomy">Taxonomy</Link>
          <Link href="/admin/audit">Audit</Link>
        </nav>
        <button type="button" className="secondary-button" onClick={() => void auth.logout()}>
          Sign out
        </button>
      </aside>
      <main className="admin-panel">{children}</main>
    </div>
  );
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <AdminShell>{children}</AdminShell>
    </AuthProvider>
  );
}

