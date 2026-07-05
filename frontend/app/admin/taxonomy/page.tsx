"use client";

import { useCallback, useEffect, useState } from "react";

import { deleteCategory, deleteTag, fetchCategories, fetchTags, saveCategory, saveTag } from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, MessageCard, TaxonomyManager } from "@/src/admin-ui";
import type { TaxonomyResponse } from "@/src/admin-types";

export default function AdminTaxonomyPage() {
  const auth = useAdminAuth();
  const [categories, setCategories] = useState<TaxonomyResponse[]>([]);
  const [tags, setTags] = useState<TaxonomyResponse[]>([]);
  const [categoryEditingId, setCategoryEditingId] = useState<number | null>(null);
  const [tagEditingId, setTagEditingId] = useState<number | null>(null);
  const [categoryForm, setCategoryForm] = useState({ name: "", slug: "", description: "" });
  const [tagForm, setTagForm] = useState({ name: "", slug: "", description: "" });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reloadTaxonomy = useCallback(async () => {
    return Promise.all([
      fetchCategories(auth.authenticatedFetch),
      fetchTags(auth.authenticatedFetch),
    ]);
  }, [auth.authenticatedFetch]);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    let active = true;

    void (async () => {
      try {
        const [loadedCategories, loadedTags] = await reloadTaxonomy();
        if (!active) {
          return;
        }
        setCategories(loadedCategories);
        setTags(loadedTags);
      } catch {
        if (active) {
          setError("분류 관리 데이터를 불러올 수 없습니다.");
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [auth.status, reloadTaxonomy]);

  function editCategory(item: TaxonomyResponse) {
    setCategoryEditingId(item.id);
    setCategoryForm({
      name: item.name,
      slug: item.slug,
      description: item.description ?? "",
    });
  }

  function editTag(item: TaxonomyResponse) {
    setTagEditingId(item.id);
    setTagForm({
      name: item.name,
      slug: item.slug,
      description: item.description ?? "",
    });
  }

  async function handleCategorySubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    await saveCategory(auth.authenticatedFetch, categoryEditingId, {
      name: String(formData.get("name") ?? ""),
      slug: String(formData.get("slug") ?? ""),
      description: String(formData.get("description") ?? ""),
    });

    setCategoryEditingId(null);
    setCategoryForm({ name: "", slug: "", description: "" });
    setMessage("카테고리 변경 사항을 저장했습니다.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  async function handleTagSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    await saveTag(auth.authenticatedFetch, tagEditingId, {
      name: String(formData.get("name") ?? ""),
      slug: String(formData.get("slug") ?? ""),
      description: String(formData.get("description") ?? ""),
    });

    setTagEditingId(null);
    setTagForm({ name: "", slug: "", description: "" });
    setMessage("태그 변경 사항을 저장했습니다.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  async function handleDeleteCategory(id: number) {
    await deleteCategory(auth.authenticatedFetch, id);
    setMessage("카테고리를 삭제했습니다.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  async function handleDeleteTag(id: number) {
    await deleteTag(auth.authenticatedFetch, id);
    setMessage("태그를 삭제했습니다.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title="분류 관리"
        description="공개 글에 사용하는 카테고리와 태그를 생성, 수정, 정리합니다."
      />
      {message ? <MessageCard title="분류 구성을 업데이트했습니다" description={message} tone="success" /> : null}
      {error ? <MessageCard title="분류 관리 화면을 불러올 수 없습니다" description={error} tone="error" /> : null}
      <div className="admin-grid">
        <TaxonomyManager
          title="카테고리"
          description="블로그 전체에서 큰 주제 축을 나누는 분류입니다."
          items={categories}
          form={{ ...categoryForm, editingId: categoryEditingId }}
          onSubmit={(event) => void handleCategorySubmit(event)}
          onEdit={editCategory}
          onDelete={(id) => void handleDeleteCategory(id)}
          submitLabel={categoryEditingId ? "카테고리 수정" : "카테고리 추가"}
        />
        <TaxonomyManager
          title="태그"
          description="세부 키워드와 교차 주제를 묶어 탐색성을 높입니다."
          items={tags}
          form={{ ...tagForm, editingId: tagEditingId }}
          onSubmit={(event) => void handleTagSubmit(event)}
          onEdit={editTag}
          onDelete={(id) => void handleDeleteTag(id)}
          submitLabel={tagEditingId ? "태그 수정" : "태그 추가"}
        />
      </div>
    </section>
  );
}
