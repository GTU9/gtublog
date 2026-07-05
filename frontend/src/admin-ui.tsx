"use client";

import Link from "next/link";
import { useMemo } from "react";

import type {
  AdminPostDetail,
  AdminPostPage,
  AuditPage,
  AuditEntryResponse,
  AutomationDiagnosticsResponse,
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationSourceResponse,
  AutomationTopicResponse,
  PostRevisionResponse,
  TaxonomyResponse,
} from "@/src/admin-types";

function formatDateTime(value: string | null) {
  if (!value) {
    return "없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
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
          <h3>전체 글</h3>
          <p className="stat-value">{posts?.totalElements ?? 0}</p>
        </article>
        <article className="admin-card">
          <h3>발행 완료</h3>
          <p className="stat-value">{publishedCount}</p>
        </article>
        <article className="admin-card">
          <h3>카테고리</h3>
          <p className="stat-value">{categories.length}</p>
        </article>
        <article className="admin-card">
          <h3>감사 로그</h3>
          <p className="stat-value">{audit?.totalElements ?? 0}</p>
        </article>
      </div>

      <div className="admin-grid">
        <article className="admin-card">
          <h3>최근 글</h3>
          <ul className="admin-list">
            {posts?.items.slice(0, 5).map((post) => (
              <li key={post.id}>
                <Link href={`/admin/posts/${post.id}`}>{post.title}</Link>
                <span className="status-pill">{post.status}</span>
              </li>
            )) ?? <li className="muted">아직 불러온 글이 없습니다.</li>}
          </ul>
        </article>

        <article className="admin-card">
          <h3>최근 운영 기록</h3>
          <ul className="admin-list">
            {audit?.items.slice(0, 5).map((entry) => (
              <li key={entry.id}>
                <strong>{entry.actionType}</strong>
                <span className="muted">{formatDateTime(entry.createdAt)}</span>
              </li>
            )) ?? <li className="muted">최근 감사 로그가 없습니다.</li>}
          </ul>
        </article>

        <article className="admin-card">
          <h3>분류 현황</h3>
          <ul className="admin-list">
            <li>
              <strong>카테고리</strong>
              <span className="muted">{categories.map((item) => item.name).join(", ") || "없음"}</span>
            </li>
            <li>
              <strong>태그</strong>
              <span className="muted">{tags.map((item) => item.name).join(", ") || "없음"}</span>
            </li>
          </ul>
        </article>
      </div>
    </>
  );
}

export function AutomationOverview({
  diagnostics,
  topics,
  selectedTopicId,
  sources,
  schedules,
  runs,
  runDetail,
  outbox,
  running,
  processingOutbox,
  onSelectTopic,
  onTriggerRun,
  onSelectRun,
  onProcessOutbox,
}: {
  diagnostics: AutomationDiagnosticsResponse | null;
  topics: AutomationTopicResponse[];
  selectedTopicId: number | null;
  sources: AutomationSourceResponse[];
  schedules: AutomationScheduleResponse[];
  runs: AutomationRunResponse[];
  runDetail: AutomationRunDetailResponse | null;
  outbox: AutomationOutboxResponse[];
  running: boolean;
  processingOutbox: boolean;
  onSelectTopic: (topicId: number) => void;
  onTriggerRun: (topicId: number) => void;
  onSelectRun: (runId: number) => void;
  onProcessOutbox: () => void;
}) {
  return (
    <div className="stack">
      {diagnostics ? (
        <div className="admin-stats">
          <article className="admin-card">
            <h3>Runs succeeded</h3>
            <p className="stat-value">{diagnostics.runCounts.succeeded}</p>
          </article>
          <article className="admin-card">
            <h3>Runs held</h3>
            <p className="stat-value">{diagnostics.runCounts.held}</p>
          </article>
          <article className="admin-card">
            <h3>Jobs pending</h3>
            <p className="stat-value">{diagnostics.jobCounts.pending}</p>
          </article>
          <article className="admin-card">
            <h3>Outbox pending</h3>
            <p className="stat-value">{diagnostics.outboxCounts.pending}</p>
          </article>
        </div>
      ) : null}

      <div className="admin-grid">
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>Automation topics</h3>
            {selectedTopicId ? (
              <button type="button" onClick={() => onTriggerRun(selectedTopicId)} disabled={running}>
                {running ? "Running..." : "Run now"}
              </button>
            ) : null}
          </div>
          <ul className="admin-list">
            {topics.map((topic) => (
              <li key={topic.id}>
                <button type="button" className="link-button" onClick={() => onSelectTopic(topic.id)}>
                  {topic.name}
                </button>
                <span className="muted">
                  {topic.slug} · {topic.publicationEnabled ? "publication on" : "publication off"}
                </span>
              </li>
            ))}
          </ul>
        </article>

        <article className="admin-card stack">
          <h3>Sources</h3>
          <ul className="admin-list">
            {sources.map((source) => (
              <li key={source.id}>
                <strong>{source.sourceType}</strong>
                <span className="muted">
                  {source.sourceUrl} · {source.enabled ? "enabled" : "disabled"}
                </span>
              </li>
            ))}
            {sources.length === 0 ? <li className="muted">No sources for this topic.</li> : null}
          </ul>
        </article>

        <article className="admin-card stack">
          <h3>Schedules</h3>
          <ul className="admin-list">
            {schedules.map((schedule) => (
              <li key={schedule.id}>
                <strong>{schedule.name}</strong>
                <span className="muted">
                  {schedule.cronExpression} · {schedule.timezone} · next {formatDateTime(schedule.nextPlannedRunAt)}
                </span>
              </li>
            ))}
            {schedules.length === 0 ? <li className="muted">No schedules for this topic.</li> : null}
          </ul>
        </article>
      </div>

      <div className="admin-grid admin-grid-wide">
        <article className="admin-card stack">
          <h3>Recent runs</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Run</th>
                <th>Status</th>
                <th>Trigger</th>
                <th>Snapshots</th>
                <th>Completed</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id}>
                  <td>{run.runKey}</td>
                  <td>
                    <span className="status-pill">{run.status}</span>
                  </td>
                  <td>{run.triggerType}</td>
                  <td>{run.snapshotCount}</td>
                  <td>{formatDateTime(run.completedAt)}</td>
                  <td>
                    <button type="button" className="secondary-button" onClick={() => onSelectRun(run.id)}>
                      Inspect
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>

        <article className="admin-card stack">
          <h3>Selected run detail</h3>
          {runDetail ? (
            <>
              <p className="muted">
                {runDetail.run.status}
                {runDetail.run.holdReason ? ` · ${runDetail.run.holdReason}` : ""}
              </p>
              <ul className="admin-list">
                {runDetail.snapshots.map((snapshot) => (
                  <li key={snapshot.id}>
                    <strong>{snapshot.title}</strong>
                    <span className="muted">
                      {snapshot.originHost} · {snapshot.policyResult} · {snapshot.canonicalUrl}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted">Select a run to inspect collected sources and hold reasons.</p>
          )}
        </article>
      </div>

      {diagnostics ? (
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>Operational diagnostics</h3>
            <span className="muted">Generated {formatDateTime(diagnostics.generatedAt)}</span>
          </div>
          <ul className="admin-list">
            <li>
              <strong>Held source snapshots</strong>
              <span className="muted">{diagnostics.heldSnapshotCount}</span>
            </li>
            <li>
              <strong>Jobs submitted</strong>
              <span className="muted">{diagnostics.jobCounts.submitted}</span>
            </li>
            <li>
              <strong>Jobs failed</strong>
              <span className="muted">{diagnostics.jobCounts.failed}</span>
            </li>
            <li>
              <strong>Outbox delivered</strong>
              <span className="muted">{diagnostics.outboxCounts.delivered}</span>
            </li>
          </ul>
          <div>
            <h4>Recent hold reasons</h4>
            <ul className="admin-list">
              {diagnostics.recentHoldReasons.length > 0 ? (
                diagnostics.recentHoldReasons.map((reason, index) => <li key={`${reason}-${index}`}>{reason}</li>)
              ) : (
                <li className="muted">No recent hold reasons.</li>
              )}
            </ul>
          </div>
        </article>
      ) : null}

      <article className="admin-card stack">
        <div className="inline-actions">
          <h3>Publication recovery outbox</h3>
          <button type="button" onClick={onProcessOutbox} disabled={processingOutbox}>
            {processingOutbox ? "Processing..." : "Replay pending events"}
          </button>
        </div>
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Post</th>
              <th>Status</th>
              <th>Available</th>
              <th>Last attempt</th>
              <th>Processed</th>
            </tr>
          </thead>
          <tbody>
            {outbox.map((event) => (
              <tr key={event.id}>
                <td>{event.id}</td>
                <td>{event.aggregateId}</td>
                <td>
                  <span className="status-pill">{event.deliveryStatus}</span>
                </td>
                <td>{formatDateTime(event.availableAt)}</td>
                <td>{formatDateTime(event.lastAttemptAt)}</td>
                <td>{formatDateTime(event.processedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
    </div>
  );
}

export function AdminPostsTable({ page }: { page: AdminPostPage | null }) {
  if (!page) {
    return <LoadingCard message="글 목록을 불러오는 중입니다..." />;
  }

  if (page.items.length === 0) {
    return <MessageCard title="아직 글이 없습니다" description="첫 번째 초안을 만들어 글 관리를 시작하세요." />;
  }

  return (
    <div className="admin-card">
      <table className="admin-table">
        <thead>
          <tr>
            <th>제목</th>
            <th>상태</th>
            <th>발행 일시</th>
            <th>카테고리</th>
            <th>태그</th>
            <th>작업</th>
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
                <Link href={`/admin/posts/${post.id}`}>편집 열기</Link>
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
          <span>이름</span>
          <input name="name" defaultValue={form.name} key={`name-${form.editingId ?? "new"}-${form.name}`} required />
        </label>
        <label className="field">
          <span>Slug</span>
          <input name="slug" defaultValue={form.slug} key={`slug-${form.editingId ?? "new"}-${form.slug}`} />
        </label>
        <label className="field">
          <span>설명</span>
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
                수정
              </button>
              <button type="button" className="danger-button" onClick={() => onDelete(item.id)}>
                삭제
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
    return <LoadingCard message="감사 로그를 불러오는 중입니다..." />;
  }

  return (
    <div className="admin-card">
      <table className="admin-table">
        <thead>
          <tr>
            <th>시각</th>
            <th>행위자</th>
            <th>대상</th>
            <th>작업</th>
            <th>세부 정보</th>
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
            <h3>{mode === "create" ? "초안 작성" : "글 편집"}</h3>
            {detail ? <span className="status-pill">{detail.status}</span> : null}
          </div>

          <label className="field">
            <span>Slug</span>
            <input name="slug" defaultValue={detail?.slug ?? ""} placeholder="비워두면 자동 생성됩니다" />
          </label>

          <label className="field">
            <span>제목</span>
            <input name="title" defaultValue={detail?.title ?? ""} required />
          </label>

          <label className="field">
            <span>요약</span>
            <textarea name="excerpt" defaultValue={detail?.excerpt ?? ""} rows={3} required />
          </label>

          <label className="field">
            <span>Markdown</span>
            <textarea name="contentMarkdown" defaultValue={detail?.contentMarkdown ?? ""} rows={14} required />
          </label>

          <label className="field">
            <span>리비전 메모</span>
            <input name="revisionNote" defaultValue="" placeholder="이번 수정에서 달라진 점을 적어 주세요" />
          </label>

          <fieldset className="field-group">
            <legend>카테고리</legend>
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
            <legend>태그</legend>
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
              {saving ? "저장 중..." : mode === "create" ? "초안 저장" : "변경 저장"}
            </button>

            {mode === "edit" ? (
              <>
                <button type="button" className="secondary-button" onClick={() => onAction("publish")}>
                  발행
                </button>
                <button type="button" className="secondary-button" onClick={() => onAction("archive")}>
                  보관
                </button>
                <button type="button" className="danger-button" onClick={() => onAction("delete")}>
                  삭제
                </button>
                <button type="button" className="secondary-button" onClick={() => onAction("restore")}>
                  초안 복원
                </button>
              </>
            ) : null}
          </div>
        </form>

        <article className="admin-card stack">
          <h3>미리보기</h3>
          <div className="post-detail">
            <div
              className="content-render"
              dangerouslySetInnerHTML={{
                __html: previewHtml || "<p>마크다운을 입력하면 글 미리보기가 여기에 표시됩니다.</p>",
              }}
            />
          </div>
        </article>
      </div>

      {mode === "edit" ? (
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>리비전 기록</h3>
            <span className="muted">{revisions.length}건</span>
          </div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>리비전</th>
                <th>제목</th>
                <th>출처</th>
                <th>메모</th>
                <th>시각</th>
                <th>작업</th>
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
                      이 리비전 복원
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
