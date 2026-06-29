"use client";

import type {
  AdminPostDetail,
  AdminPostPage,
  AdminPostUpsertRequest,
  AuditPage,
  PostRevisionResponse,
  TaxonomyResponse,
} from "@/src/admin-types";
import {
  mockAdminPostDetail,
  mockAdminPosts,
  mockAuditEntries,
  mockCategories,
  mockChangePostState,
  mockCreatePost,
  mockDeleteCategory,
  mockDeleteTag,
  mockPostRevisions,
  mockRestoreRevision,
  mockSaveCategory,
  mockSaveTag,
  mockTags,
  mockUpdatePost,
} from "@/src/admin-mock";
import { applicationApiBaseUrl, isDevelopmentRuntime } from "@/src/site";

async function parseOrThrow<T>(response: Response, errorMessage: string) {
  if (!response.ok) {
    throw new Error(errorMessage);
  }

  return (await response.json()) as T;
}

export async function fetchAdminPosts(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts`);
    return await parseOrThrow<AdminPostPage>(response, "Failed to fetch admin posts.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAdminPosts();
    }
    throw new Error("Failed to fetch admin posts.");
  }
}

export async function fetchAdminPostDetail(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  postId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts/${postId}`);
    return await parseOrThrow<AdminPostDetail>(response, "Failed to fetch post detail.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAdminPostDetail(postId);
    }
    throw new Error("Failed to fetch post detail.");
  }
}

export async function createAdminPost(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  request: AdminPostUpsertRequest,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    return await parseOrThrow<AdminPostDetail>(response, "Failed to create post.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockCreatePost(request);
    }
    throw new Error("Failed to create post.");
  }
}

export async function updateAdminPost(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  postId: number,
  request: AdminPostUpsertRequest,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts/${postId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    return await parseOrThrow<AdminPostDetail>(response, "Failed to update post.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockUpdatePost(postId, request);
    }
    throw new Error("Failed to update post.");
  }
}

export async function changeAdminPostState(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  postId: number,
  action: "publish" | "archive" | "delete" | "restore",
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts/${postId}/${action}`, {
      method: "POST",
    });
    return await parseOrThrow<AdminPostDetail>(response, `Failed to ${action} post.`);
  } catch {
    if (isDevelopmentRuntime()) {
      return mockChangePostState(postId, action);
    }
    throw new Error(`Failed to ${action} post.`);
  }
}

export async function fetchPostRevisions(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  postId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/posts/${postId}/revisions`);
    return await parseOrThrow<PostRevisionResponse[]>(response, "Failed to fetch revisions.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockPostRevisions(postId);
    }
    throw new Error("Failed to fetch revisions.");
  }
}

export async function restorePostRevision(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  postId: number,
  revisionNumber: number,
) {
  try {
    const response = await authenticatedFetch(
      `${applicationApiBaseUrl}/admin/posts/${postId}/revisions/${revisionNumber}/restore`,
      { method: "POST" },
    );
    return await parseOrThrow<AdminPostDetail>(response, "Failed to restore revision.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockRestoreRevision(postId, revisionNumber);
    }
    throw new Error("Failed to restore revision.");
  }
}

export async function fetchCategories(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/taxonomy/categories`);
    return await parseOrThrow<TaxonomyResponse[]>(response, "Failed to fetch categories.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockCategories();
    }
    throw new Error("Failed to fetch categories.");
  }
}

export async function saveCategory(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  editingId: number | null,
  request: { name: string; slug?: string; description?: string },
) {
  try {
    const response = await authenticatedFetch(
      `${applicationApiBaseUrl}/admin/taxonomy/categories${editingId ? `/${editingId}` : ""}`,
      {
        method: editingId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      },
    );
    return await parseOrThrow<TaxonomyResponse>(response, "Failed to save category.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockSaveCategory(editingId, request);
    }
    throw new Error("Failed to save category.");
  }
}

export async function deleteCategory(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  id: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/taxonomy/categories/${id}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      throw new Error("Failed to delete category.");
    }
    return;
  } catch {
    if (isDevelopmentRuntime()) {
      mockDeleteCategory(id);
      return;
    }
    throw new Error("Failed to delete category.");
  }
}

export async function fetchTags(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/taxonomy/tags`);
    return await parseOrThrow<TaxonomyResponse[]>(response, "Failed to fetch tags.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockTags();
    }
    throw new Error("Failed to fetch tags.");
  }
}

export async function saveTag(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  editingId: number | null,
  request: { name: string; slug?: string; description?: string },
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/taxonomy/tags${editingId ? `/${editingId}` : ""}`, {
      method: editingId ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    return await parseOrThrow<TaxonomyResponse>(response, "Failed to save tag.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockSaveTag(editingId, request);
    }
    throw new Error("Failed to save tag.");
  }
}

export async function deleteTag(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  id: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/taxonomy/tags/${id}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      throw new Error("Failed to delete tag.");
    }
    return;
  } catch {
    if (isDevelopmentRuntime()) {
      mockDeleteTag(id);
      return;
    }
    throw new Error("Failed to delete tag.");
  }
}

export async function fetchAuditEntries(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/audit`);
    return await parseOrThrow<AuditPage>(response, "Failed to fetch audit entries.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAuditEntries();
    }
    throw new Error("Failed to fetch audit entries.");
  }
}
