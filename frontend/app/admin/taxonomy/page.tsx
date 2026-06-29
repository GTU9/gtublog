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
          setError("Unable to load taxonomy management data.");
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
    setMessage("Category changes saved.");
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
    setMessage("Tag changes saved.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  async function handleDeleteCategory(id: number) {
    await deleteCategory(auth.authenticatedFetch, id);
    setMessage("Category deleted.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  async function handleDeleteTag(id: number) {
    await deleteTag(auth.authenticatedFetch, id);
    setMessage("Tag deleted.");
    const [loadedCategories, loadedTags] = await reloadTaxonomy();
    setCategories(loadedCategories);
    setTags(loadedTags);
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title="Taxonomy"
        description="Create, edit, and prune categories and tags used by published content."
      />
      {message ? <MessageCard title="Taxonomy updated" description={message} tone="success" /> : null}
      {error ? <MessageCard title="Taxonomy unavailable" description={error} tone="error" /> : null}
      <div className="admin-grid">
        <TaxonomyManager
          title="Categories"
          description="Use categories for broad topical grouping across the blog."
          items={categories}
          form={{ ...categoryForm, editingId: categoryEditingId }}
          onSubmit={(event) => void handleCategorySubmit(event)}
          onEdit={editCategory}
          onDelete={(id) => void handleDeleteCategory(id)}
          submitLabel={categoryEditingId ? "Update category" : "Create category"}
        />
        <TaxonomyManager
          title="Tags"
          description="Use tags for narrower cross-cutting labels and search refinement."
          items={tags}
          form={{ ...tagForm, editingId: tagEditingId }}
          onSubmit={(event) => void handleTagSubmit(event)}
          onEdit={editTag}
          onDelete={(id) => void handleDeleteTag(id)}
          submitLabel={tagEditingId ? "Update tag" : "Create tag"}
        />
      </div>
    </section>
  );
}
