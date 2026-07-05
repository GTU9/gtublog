"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { useAdminAuth } from "@/src/admin-auth";

function LoginForm() {
  const router = useRouter();
  const auth = useAdminAuth();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin-test-password");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (auth.status === "authenticated") {
      router.replace("/admin");
    }
  }, [auth.status, router]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      await auth.login(username, password);
      router.replace("/admin");
    } catch {
      setError("입력한 관리자 계정으로 로그인할 수 없습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="admin-login-page">
      <form className="admin-card" onSubmit={handleSubmit}>
        <p className="eyebrow">ADMIN ACCESS</p>
        <h1>관리자 로그인</h1>
        <p className="muted">설정된 관리자 계정으로 글, 분류, 자동화 운영을 관리합니다.</p>
        <label className="field">
          <span>아이디</span>
          <input value={username} onChange={(event) => setUsername(event.target.value)} />
        </label>
        <label className="field">
          <span>비밀번호</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        {error ? <p className="error-text">{error}</p> : null}
        <button type="submit" disabled={submitting}>
          {submitting ? "로그인 중..." : "로그인"}
        </button>
      </form>
    </main>
  );
}

export default function AdminLoginPage() {
  return <LoginForm />;
}
