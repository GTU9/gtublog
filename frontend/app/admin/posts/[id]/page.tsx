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
          setError("선택한 글을 불러올 수 없습니다.");
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
      setMessage("글 변경 사항을 저장했습니다.");
    } catch {
      setError("글 변경 사항을 저장할 수 없습니다.");
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
      const actionLabels: Record<typeof action, string> = {
        publish: "발행",
        archive: "보관",
        delete: "삭제",
        restore: "초안 복원",
      };
      setMessage(`${actionLabels[action]} 작업을 완료했습니다.`);
    } catch {
      const actionLabels: Record<typeof action, string> = {
        publish: "발행",
        archive: "보관",
        delete: "삭제",
        restore: "초안 복원",
      };
      setError(`글 ${actionLabels[action]} 작업을 진행할 수 없습니다.`);
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
      setMessage(`${revisionNumber}번 리비전을 복원했습니다.`);
    } catch {
      setError("선택한 리비전을 복원할 수 없습니다.");
    }
  }

  if (Number.isNaN(postId)) {
    return <MessageCard title="잘못된 글입니다" description="선택한 글 ID가 올바르지 않습니다." tone="error" />;
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title={detail ? detail.title : "글 편집기"}
        description="본문 편집, HTML 미리보기, 발행 상태 제어, 이전 리비전 복원을 한 화면에서 처리합니다."
      />
      {message ? <MessageCard title="편집 내용을 반영했습니다" description={message} tone="success" /> : null}
      {error ? <MessageCard title="글 편집기를 사용할 수 없습니다" description={error} tone="error" /> : null}
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
            message="관리자 인증은 메모리 기반 액세스 토큰과 중앙화된 갱신 흐름으로 유지됩니다."
            onSubmit={handleSubmit}
            onAction={(action) => void handleAction(action)}
            onRestoreRevision={(revisionNumber) => void handleRestoreRevision(revisionNumber)}
          />
        </div>
      ) : (
        <LoadingCard message="글 편집기를 불러오는 중입니다..." />
      )}
    </section>
  );
}
