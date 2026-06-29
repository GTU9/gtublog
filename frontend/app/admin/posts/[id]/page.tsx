"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import {
  changeAdminPostState,
  fetchAdminPostDetail,
  fetchCategories,
  fetchPostRevisions,
  fetchTags,
  restorePostRevision,
  updateAdminPost,
} from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, LoadingCard, MessageCard, PostEditor } from "@/src/admin-ui";
import type { AdminPostDetail, AdminPostUpsertRequest, PostRevisionResponse, TaxonomyResponse } from "@/src/admin-types";
import { previewHtmlFromMarkdown } from "@/src/admin-mock";

export default function AdminPostDetailPage() {
  const auth = useAdminAuth();
  const params = useParams<{ id: string }>();
  const postId = Number(params.id);
  const [detail, setDetail] = useState<AdminPostDetail | null>(null);
  const [categories, setCategories] = useState<TaxonomyResponse[]>([]);
  const [tags, setTags] = useState<TaxonomyResponse[]>([]);
  const [revisions, setRevisions] = useState<PostRevisionResponse[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [previewHtml, setPreviewHtml] = useState("");

  const loadPage = useCallback(async () => {
    return Promise.all([
      fetchAdminPostDetail(auth.authenticatedFetch, postId),
      fetchCategories(auth.authenticatedFetch),
      fetchTags(auth.authenticatedFetch),
      fetchPostRevisions(auth.authenticatedFetch, postId),
    ]);
  }, [auth.authenticatedFetch, postId]);

  useEffect(() => {
    if (auth.status !== "authenticated" || Number.isNaN(postId)) {
      return;
    }

    let active = true;

    void (async () => {
      try {
        const [loadedDetail, loadedCategories, loadedTags, loadedRevisions] = await loadPage();
        if (!active) {
          return;
        }
        setDetail(loadedDetail);
        setCategories(loadedCategories);
        setTags(loadedTags);
        setRevisions(loadedRevisions);
        setPreviewHtml(loadedDetail?.contentHtml ?? "");
      } catch {
        if (active) {
          setError("Unable to load the selected post.");
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [auth.status, loadPage, postId]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    setError(null);

    try {
      const formData = new FormData(event.currentTarget);
      const contentMarkdown = String(formData.get("contentMarkdown") ?? "");
      const request: AdminPostUpsertRequest = {
        slug: String(formData.get("slug") ?? ""),
        title: String(formData.get("title") ?? ""),
        excerpt: String(formData.get("excerpt") ?? ""),
        contentMarkdown,
        contentHtml: previewHtmlFromMarkdown(contentMarkdown),
        sourceFingerprint: null,
        categoryIds: formData.getAll("categoryIds").map((value) => Number(value)),
        tagIds: formData.getAll("tagIds").map((value) => Number(value)),
        revisionNote: String(formData.get("revisionNote") ?? ""),
      };

      const updated = await updateAdminPost(auth.authenticatedFetch, postId, request);
      setDetail(updated);
      const [loadedDetail, loadedCategories, loadedTags, loadedRevisions] = await loadPage();
      setDetail(loadedDetail);
      setCategories(loadedCategories);
      setTags(loadedTags);
      setRevisions(loadedRevisions);
      setPreviewHtml(loadedDetail?.contentHtml ?? "");
      setMessage("Post changes saved.");
    } catch {
      setError("Unable to save post changes.");
    } finally {
      setSaving(false);
    }
  }

  async function handleAction(action: "publish" | "archive" | "delete" | "restore") {
    setMessage(null);
    setError(null);

    try {
      const updated = await changeAdminPostState(auth.authenticatedFetch, postId, action);
      setDetail(updated);
      const [loadedDetail, loadedCategories, loadedTags, loadedRevisions] = await loadPage();
      setDetail(loadedDetail);
      setCategories(loadedCategories);
      setTags(loadedTags);
      setRevisions(loadedRevisions);
      setPreviewHtml(loadedDetail?.contentHtml ?? "");
      setMessage(`Post ${action} action completed.`);
    } catch {
      setError(`Unable to ${action} this post.`);
    }
  }

  async function handleRestoreRevision(revisionNumber: number) {
    setMessage(null);
    setError(null);

    try {
      const updated = await restorePostRevision(auth.authenticatedFetch, postId, revisionNumber);
      setDetail(updated);
      const [loadedDetail, loadedCategories, loadedTags, loadedRevisions] = await loadPage();
      setDetail(loadedDetail);
      setCategories(loadedCategories);
      setTags(loadedTags);
      setRevisions(loadedRevisions);
      setPreviewHtml(loadedDetail?.contentHtml ?? "");
      setMessage(`Revision ${revisionNumber} restored.`);
    } catch {
      setError("Unable to restore the selected revision.");
    }
  }

  if (Number.isNaN(postId)) {
    return <MessageCard title="Invalid post" description="The selected post id is not valid." tone="error" />;
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title={detail ? detail.title : "Post editor"}
        description="Edit content, preview HTML, control publication state, and restore earlier revisions."
      />
      {message ? <MessageCard title="Editor update" description={message} tone="success" /> : null}
      {error ? <MessageCard title="Editor unavailable" description={error} tone="error" /> : null}
      {detail ? (
        <div
          onInput={(event) => {
            const target = event.target as HTMLTextAreaElement | null;
            if (target?.name === "contentMarkdown") {
              setPreviewHtml(previewHtmlFromMarkdown(target.value));
            }
          }}
        >
          <PostEditor
            detail={detail}
            revisions={revisions}
            categories={categories}
            tags={tags}
            mode="edit"
            saving={saving}
            previewHtml={previewHtml}
            message="The editor keeps access tokens in memory only and refreshes through the centralized auth client."
            onSubmit={handleSubmit}
            onAction={(action) => void handleAction(action)}
            onRestoreRevision={(revisionNumber) => void handleRestoreRevision(revisionNumber)}
          />
        </div>
      ) : (
        <LoadingCard message="Loading post editor..." />
      )}
    </section>
  );
}
