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
      setError("Unable to sign in with the provided credentials.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="admin-login-page">
      <form className="admin-card" onSubmit={handleSubmit}>
        <p className="eyebrow">ADMIN ACCESS</p>
        <h1>Sign in</h1>
        <p className="muted">Use the configured administrator account to manage posts and taxonomy.</p>
        <label className="field">
          <span>Username</span>
          <input value={username} onChange={(event) => setUsername(event.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        {error ? <p className="error-text">{error}</p> : null}
        <button type="submit" disabled={submitting}>
          {submitting ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </main>
  );
}

export default function AdminLoginPage() {
  return <LoginForm />;
}
