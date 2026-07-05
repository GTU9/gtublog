"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { createAdminPost, fetchCategories, fetchTags } from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, MessageCard, PostEditor } from "@/src/admin-ui";
import type { AdminPostUpsertRequest, TaxonomyResponse } from "@/src/admin-types";
import { previewHtmlFromMarkdown } from "@/src/admin-mock";

export default function AdminCreatePostPage() {
  const auth = useAdminAuth();
  const router = useRouter();
  const [categories, setCategories] = useState<TaxonomyResponse[]>([]);
  const [tags, setTags] = useState<TaxonomyResponse[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [markdownPreview, setMarkdownPreview] = useState("");

  useEffect(() => {
    if (auth.status === "authenticated" && categories.length === 0 && tags.length === 0) {
      void fetchCategories(auth.authenticatedFetch).then(setCategories);
      void fetchTags(auth.authenticatedFetch).then(setTags);
    }
  }, [auth, categories.length, tags.length]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);

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

      const detail = await createAdminPost(auth.authenticatedFetch, request);
      router.replace(`/admin/posts/${detail.id}`);
    } catch {
      setMessage("지금은 새 초안을 생성할 수 없습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title="새 글 작성"
        description="수동 초안을 만들고 분류를 연결한 뒤 즉시 미리보기까지 확인합니다."
      />
      {message ? <MessageCard title="초안 생성에 실패했습니다" description={message} tone="error" /> : null}
      <div
        onInput={(event) => {
          const target = event.target as HTMLTextAreaElement | null;
          if (target?.name === "contentMarkdown") {
            setMarkdownPreview(target.value);
          }
        }}
      >
        <PostEditor
          detail={null}
          revisions={[]}
          categories={categories}
          tags={tags}
          mode="create"
          saving={saving}
          previewHtml={previewHtmlFromMarkdown(markdownPreview)}
          message="새 글은 초안 상태로 저장되며, 상세 편집기에서 발행할 수 있습니다."
          onSubmit={handleSubmit}
          onAction={() => undefined}
          onRestoreRevision={() => undefined}
        />
      </div>
    </section>
  );
}
