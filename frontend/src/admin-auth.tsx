"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import type { AdminProfile, AuthSessionResponse, AuthState } from "@/src/admin-types";
import { mockAdminProfile } from "@/src/admin-mock";
import { applicationApiBaseUrl, isDevelopmentRuntime } from "@/src/site";

type AuthContextValue = AuthState & {
  accessToken: string | null;
  csrfToken: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

let inMemoryAccessToken: string | null = null;
let inMemoryCsrfToken: string | null = null;
let refreshPromise: Promise<void> | null = null;

function readCookie(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

function storeSession(session: AuthSessionResponse) {
  inMemoryAccessToken = session.accessToken;
  inMemoryCsrfToken = session.csrfToken;
}

function clearSession() {
  inMemoryAccessToken = null;
  inMemoryCsrfToken = null;
}

async function refreshSession() {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const csrfToken = inMemoryCsrfToken ?? readCookie("gtublog_csrf");

    if (!csrfToken) {
      if (isDevelopmentRuntime()) {
        inMemoryAccessToken = "dev-access-token";
        inMemoryCsrfToken = "dev-csrf-token";
        return;
      }

      throw new Error("Missing CSRF token for refresh.");
    }

    try {
      const response = await fetch(`${applicationApiBaseUrl}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
          "X-CSRF-Token": csrfToken,
        },
      });

      if (!response.ok) {
        throw new Error("Refresh failed.");
      }

      const session = (await response.json()) as AuthSessionResponse;
      storeSession(session);
    } catch (error) {
      if (isDevelopmentRuntime()) {
        inMemoryAccessToken = "dev-access-token";
        inMemoryCsrfToken = "dev-csrf-token";
        return;
      }

      throw error;
    }
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}

async function fetchAdminProfile(): Promise<AdminProfile | null> {
  if (!inMemoryAccessToken) {
    return isDevelopmentRuntime() ? mockAdminProfile : null;
  }

  try {
    const response = await fetch(`${applicationApiBaseUrl}/auth/session`, {
      headers: {
        Authorization: `Bearer ${inMemoryAccessToken}`,
      },
    });

    if (!response.ok) {
      return isDevelopmentRuntime() ? mockAdminProfile : null;
    }

    return (await response.json()) as AdminProfile;
  } catch {
    return isDevelopmentRuntime() ? mockAdminProfile : null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading", admin: null });
  const mounted = useRef(false);

  const syncProfile = useCallback(async () => {
    const admin = await fetchAdminProfile();
    setState(admin ? { status: "authenticated", admin } : { status: "unauthenticated", admin: null });
  }, []);

  useEffect(() => {
    if (mounted.current) {
      return;
    }

    mounted.current = true;

    void (async () => {
      try {
        await refreshSession();
      } catch {
        clearSession();
      }

      await syncProfile();
    })();
  }, [syncProfile]);

  const login = useCallback(async (username: string, password: string) => {
    try {
      const response = await fetch(`${applicationApiBaseUrl}/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        throw new Error("Login failed.");
      }

      const session = (await response.json()) as AuthSessionResponse;
      storeSession(session);
      setState({ status: "authenticated", admin: session.admin });
    } catch (error) {
      if (isDevelopmentRuntime() && username === "admin" && password === "admin-test-password") {
        inMemoryAccessToken = "dev-access-token";
        inMemoryCsrfToken = "dev-csrf-token";
        setState({ status: "authenticated", admin: mockAdminProfile });
        return;
      }

      throw error;
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await fetch(`${applicationApiBaseUrl}/auth/logout`, {
        method: "POST",
        credentials: "include",
        headers: inMemoryCsrfToken
          ? {
              "X-CSRF-Token": inMemoryCsrfToken,
            }
          : undefined,
      });
    } finally {
      clearSession();
      setState({ status: "unauthenticated", admin: null });
    }
  }, []);

  const authenticatedFetch = useCallback(async (input: string, init?: RequestInit) => {
    if (!inMemoryAccessToken) {
      await refreshSession();
    }

    const perform = async () =>
      fetch(input, {
        ...init,
        credentials: "include",
        headers: {
          ...(init?.headers ?? {}),
          Authorization: inMemoryAccessToken ? `Bearer ${inMemoryAccessToken}` : "",
          ...(inMemoryCsrfToken ? { "X-CSRF-Token": inMemoryCsrfToken } : {}),
        },
      });

    let response = await perform();
    if (response.status === 401) {
      await refreshSession();
      response = await perform();
    }

    return response;
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      accessToken: inMemoryAccessToken,
      csrfToken: inMemoryCsrfToken,
      login,
      logout,
      authenticatedFetch,
    }),
    [authenticatedFetch, login, logout, state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAdminAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAdminAuth must be used within AuthProvider.");
  }
  return context;
}

