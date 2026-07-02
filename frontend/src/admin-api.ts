"use client";

import type {
  AdminPostDetail,
  AdminPostPage,
  AdminPostUpsertRequest,
  AuditPage,
  AutomationDiagnosticsResponse,
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationScheduleUpsertRequest,
  AutomationSourceResponse,
  AutomationSourceUpsertRequest,
  AutomationTopicResponse,
  AutomationTopicUpsertRequest,
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
  mockAutomationOutbox,
  mockAutomationDiagnostics,
  mockAutomationRunDetail,
  mockAutomationRuns,
  mockDeleteAutomationSchedule,
  mockDeleteAutomationSource,
  mockSaveAutomationSchedule,
  mockSaveAutomationSource,
  mockSaveAutomationTopic,
  mockAutomationSchedules,
  mockAutomationSources,
  mockAutomationTopics,
  mockProcessAutomationOutbox,
  mockTriggerAutomationRun,
  mockPostRevisions,
  mockRestoreRevision,
  mockSaveCategory,
  mockSaveTag,
  mockTags,
  mockUpdatePost,
} from "@/src/admin-mock";
import { applicationApiBaseUrl, isDevelopmentRuntime } from "@/src/site";

async function toApiError(response: Response, fallbackMessage: string) {
  try {
    const payload = (await response.json()) as { detail?: string; title?: string };
    if (payload.detail && payload.detail.trim().length > 0) {
      return new Error(payload.detail);
    }
    if (payload.title && payload.title.trim().length > 0) {
      return new Error(payload.title);
    }
  } catch {
    // fall through to fallback message
  }
  return new Error(fallbackMessage);
}

async function parseOrThrow<T>(response: Response, errorMessage: string) {
  if (!response.ok) {
    throw await toApiError(response, errorMessage);
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

export async function fetchAutomationTopics(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/topics`);
    return await parseOrThrow<AutomationTopicResponse[]>(response, "Failed to fetch automation topics.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationTopics();
    }
    throw new Error("Failed to fetch automation topics.");
  }
}

export async function saveAutomationTopic(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  editingId: number | null,
  request: AutomationTopicUpsertRequest,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/topics${editingId ? `/${editingId}` : ""}`, {
      method: editingId ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    return await parseOrThrow<AutomationTopicResponse>(response, "Failed to save automation topic.");
  } catch (error) {
    if (isDevelopmentRuntime()) {
      return mockSaveAutomationTopic(editingId, request);
    }
    throw error;
  }
}

export async function fetchAutomationSources(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  topicId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/topics/${topicId}/sources`);
    return await parseOrThrow<AutomationSourceResponse[]>(response, "Failed to fetch automation sources.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationSources(topicId);
    }
    throw new Error("Failed to fetch automation sources.");
  }
}

export async function saveAutomationSource(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  topicId: number,
  editingId: number | null,
  request: AutomationSourceUpsertRequest,
) {
  try {
    const response = await authenticatedFetch(
      `${applicationApiBaseUrl}/admin/automation/${editingId ? `sources/${editingId}` : `topics/${topicId}/sources`}`,
      {
        method: editingId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      },
    );
    return await parseOrThrow<AutomationSourceResponse>(response, "Failed to save automation source.");
  } catch (error) {
    if (isDevelopmentRuntime()) {
      return mockSaveAutomationSource(topicId, editingId, request);
    }
    throw error;
  }
}

export async function deleteAutomationSource(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  sourceId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/sources/${sourceId}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      throw await toApiError(response, "Failed to delete automation source.");
    }
    return;
  } catch (error) {
    if (isDevelopmentRuntime()) {
      mockDeleteAutomationSource(sourceId);
      return;
    }
    throw error;
  }
}

export async function fetchAutomationSchedules(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  topicId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/topics/${topicId}/schedules`);
    return await parseOrThrow<AutomationScheduleResponse[]>(response, "Failed to fetch automation schedules.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationSchedules(topicId);
    }
    throw new Error("Failed to fetch automation schedules.");
  }
}

export async function saveAutomationSchedule(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  topicId: number,
  editingId: number | null,
  request: AutomationScheduleUpsertRequest,
) {
  try {
    const response = await authenticatedFetch(
      `${applicationApiBaseUrl}/admin/automation/${editingId ? `schedules/${editingId}` : `topics/${topicId}/schedules`}`,
      {
        method: editingId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      },
    );
    return await parseOrThrow<AutomationScheduleResponse>(response, "Failed to save automation schedule.");
  } catch (error) {
    if (isDevelopmentRuntime()) {
      return mockSaveAutomationSchedule(topicId, editingId, request);
    }
    throw error;
  }
}

export async function deleteAutomationSchedule(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  scheduleId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/schedules/${scheduleId}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      throw await toApiError(response, "Failed to delete automation schedule.");
    }
    return;
  } catch (error) {
    if (isDevelopmentRuntime()) {
      mockDeleteAutomationSchedule(scheduleId);
      return;
    }
    throw error;
  }
}

export async function fetchAutomationRuns(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/runs`);
    return await parseOrThrow<AutomationRunResponse[]>(response, "Failed to fetch automation runs.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationRuns();
    }
    throw new Error("Failed to fetch automation runs.");
  }
}

export async function fetchAutomationDiagnostics(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/diagnostics`);
    return await parseOrThrow<AutomationDiagnosticsResponse>(response, "Failed to fetch automation diagnostics.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationDiagnostics();
    }
    throw new Error("Failed to fetch automation diagnostics.");
  }
}

export async function fetchAutomationRunDetail(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  runId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/runs/${runId}`);
    return await parseOrThrow<AutomationRunDetailResponse>(response, "Failed to fetch automation run detail.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationRunDetail(runId);
    }
    throw new Error("Failed to fetch automation run detail.");
  }
}

export async function triggerAutomationRun(
  authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>,
  topicId: number,
) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/topics/${topicId}/runs/manual`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    return await parseOrThrow<AutomationRunResponse>(response, "Failed to trigger automation run.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockTriggerAutomationRun(topicId);
    }
    throw new Error("Failed to trigger automation run.");
  }
}

export async function fetchAutomationOutbox(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/outbox`);
    return await parseOrThrow<AutomationOutboxResponse[]>(response, "Failed to fetch automation outbox.");
  } catch {
    if (isDevelopmentRuntime()) {
      return mockAutomationOutbox();
    }
    throw new Error("Failed to fetch automation outbox.");
  }
}

export async function processAutomationOutbox(authenticatedFetch: (input: string, init?: RequestInit) => Promise<Response>) {
  try {
    const response = await authenticatedFetch(`${applicationApiBaseUrl}/admin/automation/outbox/process`, {
      method: "POST",
    });
    if (!response.ok) {
      throw await toApiError(response, "Failed to process automation outbox.");
    }
    return;
  } catch {
    if (isDevelopmentRuntime()) {
      mockProcessAutomationOutbox();
      return;
    }
    throw new Error("Failed to process automation outbox.");
  }
}
