"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { AuthProvider, useAdminAuth } from "@/src/admin-auth";

function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const auth = useAdminAuth();
  const navigationItems = [
    { href: "/admin", label: "대시보드" },
    { href: "/admin/automation", label: "자동화 운영" },
    { href: "/admin/posts", label: "글 관리" },
    { href: "/admin/taxonomy", label: "분류 관리" },
    { href: "/admin/audit", label: "감사 로그" },
  ];

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
    <div className="admin-app-shell">
      <div className="admin-shell">
        <aside className="admin-sidebar">
          <div>
            <p className="eyebrow">ADMIN</p>
            <h1>{auth.admin?.displayName ?? "관리자"}</h1>
            <p className="muted">{auth.admin?.username}</p>
          </div>
          <nav className="admin-nav" aria-label="관리자 탐색">
            {navigationItems.map((item) => {
              const isActive =
                item.href === "/admin"
                  ? pathname === "/admin"
                  : pathname === item.href || pathname.startsWith(`${item.href}/`);

              return (
                <Link key={item.href} href={item.href} className={isActive ? "admin-nav-link is-active" : "admin-nav-link"}>
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <button type="button" className="secondary-button" onClick={() => void auth.logout()}>
            로그아웃
          </button>
        </aside>
        <main className="admin-panel">{children}</main>
      </div>
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

