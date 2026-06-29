"use client";

import Link from "next/link";
import { useMemo } from "react";

import type {
  AdminPostDetail,
  AdminPostPage,
  AuditPage,
  AuditEntryResponse,
  PostRevisionResponse,
  TaxonomyResponse,
} from "@/src/admin-types";

function formatDateTime(value: string | null) {
  if (!value) {
    return "Not scheduled";
  }

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function AdminPageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="admin-hero">
      <div>
        <h2>{title}</h2>
        <p className="muted">{description}</p>
      </div>
      {action ? <div className="admin-hero-action">{action}</div> : null}
    </div>
  );
}

export function LoadingCard({ message }: { message: string }) {
  return (
    <section className="admin-card">
      <p className="muted">{message}</p>
    </section>
  );
}

export function MessageCard({
  title,
  description,
  tone = "normal",
}: {
  title: string;
  description: string;
  tone?: "normal" | "error" | "success";
}) {
  return (
    <section className={`admin-card ${tone === "error" ? "admin-card-error" : ""} ${tone === "success" ? "admin-card-success" : ""}`}>
      <h3>{title}</h3>
      <p className="muted">{description}</p>
    </section>
  );
}

export function DashboardStats({
  posts,
  categories,
  tags,
  audit,
}: {
  posts: AdminPostPage | null;
  categories: TaxonomyResponse[];
  tags: TaxonomyResponse[];
  audit: AuditPage | null;
}) {
  const publishedCount = posts?.items.filter((post) => post.status === "PUBLISHED").length ?? 0;

  return (
    <>
      <div className="admin-stats">
        <article className="admin-card">
          <h3>Posts</h3>
          <p className="stat-value">{posts?.totalElements ?? 0}</p>
        </article>
        <article className="admin-card">
          <h3>Published</h3>
          <p className="stat-value">{publishedCount}</p>
        </article>
        <article className="admin-card">
          <h3>Categories</h3>
          <p className="stat-value">{categories.length}</p>
        </article>
        <article className="admin-card">
          <h3>Audit rows</h3>
          <p className="stat-value">{audit?.totalElements ?? 0}</p>
        </article>
      </div>

      <div className="admin-grid">
        <article className="admin-card">
          <h3>Recent posts</h3>
          <ul className="admin-list">
            {posts?.items.slice(0, 5).map((post) => (
              <li key={post.id}>
                <Link href={`/admin/posts/${post.id}`}>{post.title}</Link>
                <span className="status-pill">{post.status}</span>
              </li>
            )) ?? <li className="muted">No posts loaded yet.</li>}
          </ul>
        </article>

        <article className="admin-card">
          <h3>Recent audit actions</h3>
          <ul className="admin-list">
            {audit?.items.slice(0, 5).map((entry) => (
              <li key={entry.id}>
                <strong>{entry.actionType}</strong>
                <span className="muted">{formatDateTime(entry.createdAt)}</span>
              </li>
            )) ?? <li className="muted">No audit rows loaded yet.</li>}
          </ul>
        </article>

        <article className="admin-card">
          <h3>Taxonomy coverage</h3>
          <ul className="admin-list">
            <li>
              <strong>Categories</strong>
              <span className="muted">{categories.map((item) => item.name).join(", ") || "None"}</span>
            </li>
            <li>
              <strong>Tags</strong>
              <span className="muted">{tags.map((item) => item.name).join(", ") || "None"}</span>
            </li>
          </ul>
        </article>
      </div>
    </>
  );
}

export function AdminPostsTable({ page }: { page: AdminPostPage | null }) {
  if (!page) {
    return <LoadingCard message="Loading post inventory..." />;
  }

  if (page.items.length === 0) {
    return <MessageCard title="No posts yet" description="Create the first draft to start managing content." />;
  }

  return (
    <div className="admin-card">
      <table className="admin-table">
        <thead>
          <tr>
            <th>Title</th>
            <th>Status</th>
            <th>Published</th>
            <th>Categories</th>
            <th>Tags</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {page.items.map((post) => (
            <tr key={post.id}>
              <td>{post.title}</td>
              <td>
                <span className="status-pill">{post.status}</span>
              </td>
              <td>{formatDateTime(post.firstPublishedAt)}</td>
              <td>{post.categories.join(", ") || "-"}</td>
              <td>{post.tags.join(", ") || "-"}</td>
              <td>
                <Link href={`/admin/posts/${post.id}`}>Open editor</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function TaxonomyManager({
  title,
  description,
  items,
  form,
  onSubmit,
  onEdit,
  onDelete,
  submitLabel,
}: {
  title: string;
  description: string;
  items: TaxonomyResponse[];
  form: {
    name: string;
    slug: string;
    description: string;
    editingId: number | null;
  };
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onEdit: (item: TaxonomyResponse) => void;
  onDelete: (id: number) => void;
  submitLabel: string;
}) {
  return (
    <article className="admin-card stack">
      <div>
        <h3>{title}</h3>
        <p className="muted">{description}</p>
      </div>

      <form className="stack" onSubmit={onSubmit}>
        <label className="field">
          <span>Name</span>
          <input name="name" defaultValue={form.name} key={`name-${form.editingId ?? "new"}-${form.name}`} required />
        </label>
        <label className="field">
          <span>Slug</span>
          <input name="slug" defaultValue={form.slug} key={`slug-${form.editingId ?? "new"}-${form.slug}`} />
        </label>
        <label className="field">
          <span>Description</span>
          <textarea
            name="description"
            defaultValue={form.description}
            key={`description-${form.editingId ?? "new"}-${form.description}`}
            rows={3}
          />
        </label>
        <button type="submit">{submitLabel}</button>
      </form>

      <ul className="admin-list">
        {items.map((item) => (
          <li key={item.id}>
            <div>
              <strong>{item.name}</strong>
              <p className="muted">
                {item.slug}
                {item.description ? ` · ${item.description}` : ""}
              </p>
            </div>
            <div className="inline-actions">
              <button type="button" className="secondary-button" onClick={() => onEdit(item)}>
                Edit
              </button>
              <button type="button" className="danger-button" onClick={() => onDelete(item.id)}>
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>
    </article>
  );
}

export function AuditTable({ page }: { page: AuditPage | null }) {
  if (!page) {
    return <LoadingCard message="Loading audit trail..." />;
  }

  return (
    <div className="admin-card">
      <table className="admin-table">
        <thead>
          <tr>
            <th>When</th>
            <th>Actor</th>
            <th>Target</th>
            <th>Action</th>
            <th>Details</th>
          </tr>
        </thead>
        <tbody>
          {page.items.map((entry) => (
            <AuditRow key={entry.id} entry={entry} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditRow({ entry }: { entry: AuditEntryResponse }) {
  return (
    <tr>
      <td>{formatDateTime(entry.createdAt)}</td>
      <td>
        {entry.actorType}:{entry.actorId}
      </td>
      <td>
        {entry.targetType}:{entry.targetId}
      </td>
      <td>{entry.actionType}</td>
      <td>
        <code>{entry.detailJson}</code>
      </td>
    </tr>
  );
}

export function PostEditor({
  detail,
  revisions,
  categories,
  tags,
  mode,
  saving,
  previewHtml,
  message,
  onSubmit,
  onAction,
  onRestoreRevision,
}: {
  detail: AdminPostDetail | null;
  revisions: PostRevisionResponse[];
  categories: TaxonomyResponse[];
  tags: TaxonomyResponse[];
  mode: "create" | "edit";
  saving: boolean;
  previewHtml: string;
  message: string | null;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onAction: (action: "publish" | "archive" | "delete" | "restore") => void;
  onRestoreRevision: (revisionNumber: number) => void;
}) {
  const categoryIds = useMemo(() => new Set(detail?.categories.map((item) => item.id) ?? []), [detail]);
  const tagIds = useMemo(() => new Set(detail?.tags.map((item) => item.id) ?? []), [detail]);

  return (
    <div className="stack">
      <div className="admin-grid admin-grid-wide">
        <form
          key={`${mode}-${detail?.id ?? "new"}-${detail?.updatedAt ?? "create"}-${detail?.status ?? "draft"}`}
          className="admin-card stack"
          onSubmit={onSubmit}
        >
          <div className="inline-actions">
            <h3>{mode === "create" ? "Create draft" : "Edit post"}</h3>
            {detail ? <span className="status-pill">{detail.status}</span> : null}
          </div>

          <label className="field">
            <span>Slug</span>
            <input name="slug" defaultValue={detail?.slug ?? ""} placeholder="leave blank to auto-generate" />
          </label>

          <label className="field">
            <span>Title</span>
            <input name="title" defaultValue={detail?.title ?? ""} required />
          </label>

          <label className="field">
            <span>Excerpt</span>
            <textarea name="excerpt" defaultValue={detail?.excerpt ?? ""} rows={3} required />
          </label>

          <label className="field">
            <span>Markdown</span>
            <textarea name="contentMarkdown" defaultValue={detail?.contentMarkdown ?? ""} rows={14} required />
          </label>

          <label className="field">
            <span>Revision note</span>
            <input name="revisionNote" defaultValue="" placeholder="What changed in this revision?" />
          </label>

          <fieldset className="field-group">
            <legend>Categories</legend>
            <div className="checkbox-grid">
              {categories.map((item) => (
                <label key={item.id} className="checkbox-item">
                  <input type="checkbox" name="categoryIds" value={item.id} defaultChecked={categoryIds.has(item.id)} />
                  <span>{item.name}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <fieldset className="field-group">
            <legend>Tags</legend>
            <div className="checkbox-grid">
              {tags.map((item) => (
                <label key={item.id} className="checkbox-item">
                  <input type="checkbox" name="tagIds" value={item.id} defaultChecked={tagIds.has(item.id)} />
                  <span>{item.name}</span>
                </label>
              ))}
            </div>
          </fieldset>

          {message ? <p className="muted">{message}</p> : null}

          <div className="inline-actions">
            <button type="submit" disabled={saving}>
              {saving ? "Saving..." : mode === "create" ? "Create draft" : "Save changes"}
            </button>

            {mode === "edit" ? (
              <>
                <button type="button" className="secondary-button" onClick={() => onAction("publish")}>
                  Publish
                </button>
                <button type="button" className="secondary-button" onClick={() => onAction("archive")}>
                  Archive
                </button>
                <button type="button" className="danger-button" onClick={() => onAction("delete")}>
                  Delete
                </button>
                <button type="button" className="secondary-button" onClick={() => onAction("restore")}>
                  Restore draft
                </button>
              </>
            ) : null}
          </div>
        </form>

        <article className="admin-card stack">
          <h3>Preview</h3>
          <div className="post-detail">
            <div
              className="content-render"
              dangerouslySetInnerHTML={{
                __html: previewHtml || "<p>Start typing markdown to preview the article content.</p>",
              }}
            />
          </div>
        </article>
      </div>

      {mode === "edit" ? (
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>Revision history</h3>
            <span className="muted">{revisions.length} revisions</span>
          </div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Revision</th>
                <th>Title</th>
                <th>Source</th>
                <th>Note</th>
                <th>When</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {revisions.map((revision) => (
                <tr key={revision.id}>
                  <td>{revision.revisionNumber}</td>
                  <td>{revision.title}</td>
                  <td>{revision.revisionSource}</td>
                  <td>{revision.revisionNote ?? "-"}</td>
                  <td>{formatDateTime(revision.createdAt)}</td>
                  <td>
                    <button type="button" className="secondary-button" onClick={() => onRestoreRevision(revision.revisionNumber)}>
                      Restore this revision
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>
      ) : null}
    </div>
  );
}
