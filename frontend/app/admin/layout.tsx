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
    return <main className="admin-panel">관리자 세션을 불러오는 중입니다...</main>;
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div>
          <p className="eyebrow">ADMIN</p>
          <h1>{auth.admin?.displayName ?? "관리자"}</h1>
          <p className="muted">{auth.admin?.username}</p>
        </div>
        <nav className="admin-nav" aria-label="관리자 탐색">
          <Link href="/admin">대시보드</Link>
          <Link href="/admin/automation">자동화 운영</Link>
          <Link href="/admin/posts">글 관리</Link>
          <Link href="/admin/taxonomy">분류 관리</Link>
          <Link href="/admin/audit">감사 로그</Link>
        </nav>
        <button type="button" className="secondary-button" onClick={() => void auth.logout()}>
          로그아웃
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

