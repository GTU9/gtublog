import type {
  AdminPostDetail,
  AdminPostPage,
  AdminPostUpsertRequest,
  AdminProfile,
  AuditPage,
  AuditEntryResponse,
  AutomationDiagnosticsResponse,
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunOverridePublishResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationScheduleUpsertRequest,
  AutomationSourceResponse,
  AutomationSourceUpsertRequest,
  AutomationTopicResponse,
  AutomationTopicUpsertRequest,
  PostRevisionResponse,
  TaxonomyResponse,
} from "./admin-types";
import { mockPostDetails } from "./mock-content";

export const mockAdminProfile: AdminProfile = {
  id: 1,
  username: "admin",
  displayName: "GTU Administrator",
};

const initialCategories: TaxonomyResponse[] = [
  { id: 1, slug: "development", name: "Development", description: "Spring, React, and implementation notes" },
  { id: 2, slug: "automation", name: "Automation", description: "Collection and publication workflows" },
];

const initialTags: TaxonomyResponse[] = [
  { id: 10, slug: "spring-boot", name: "Spring Boot", description: "Spring Boot backend architecture" },
  { id: 11, slug: "react", name: "React", description: "React and App Router UI" },
  { id: 12, slug: "codex", name: "Codex", description: "Codex automation experiments" },
];

function cloneTaxonomy(items: TaxonomyResponse[]) {
  return items.map((item) => ({ ...item }));
}

function clonePostDetail(post: AdminPostDetail): AdminPostDetail {
  return {
    ...post,
    categories: cloneTaxonomy(post.categories),
    tags: cloneTaxonomy(post.tags),
    relatedPosts: post.relatedPosts.map((item) => ({
      ...item,
      categoryDetails: item.categoryDetails?.map((entry) => ({ ...entry })),
      tagDetails: item.tagDetails?.map((entry) => ({ ...entry })),
    })),
  };
}

function makeRevision(detail: AdminPostDetail, revisionNumber: number, revisionSource: string, revisionNote: string | null) {
  return {
    id: detail.id * 100 + revisionNumber,
    revisionNumber,
    title: detail.title,
    excerpt: detail.excerpt,
    contentMarkdown: detail.contentMarkdown,
    contentHtml: detail.contentHtml,
    revisionSource,
    revisionNote,
    createdAt: detail.updatedAt,
  } satisfies PostRevisionResponse;
}

function buildInitialRevisions(posts: AdminPostDetail[]) {
  const map = new Map<number, PostRevisionResponse[]>();
  for (const post of posts) {
    map.set(post.id, [makeRevision(post, 1, "MANUAL_CREATE", "Initial imported revision")]);
  }
  return map;
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9가-힣\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-");
}

function markdownToHtml(markdown: string) {
  const escapedMarkdown = markdown
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");

  return escapedMarkdown
    .split(/\n{2,}/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .map((paragraph) => `<p>${paragraph.replace(/\n/g, "<br />")}</p>`)
    .join("");
}

function createInitialPosts() {
  return mockPostDetails.map((post) => {
    const detail = clonePostDetail(post);
    if (!detail.contentMarkdown) {
      detail.contentMarkdown = detail.contentHtml.replace(/<[^>]+>/g, "").trim();
    }
    return detail;
  });
}

let categoriesStore = cloneTaxonomy(initialCategories);
let tagsStore = cloneTaxonomy(initialTags);
let postsStore = createInitialPosts();
let revisionsStore = buildInitialRevisions(postsStore);
let auditStore: AuditEntryResponse[] = [
  {
    id: 9001,
    actorType: "ADMIN",
    actorId: "1",
    targetType: "POST",
    targetId: "101",
    actionType: "POST_PUBLISHED",
    detailJson: "{\"slug\":\"spring-boot-automation-blog\"}",
    createdAt: "2026-06-29T06:25:00Z",
  },
  {
    id: 9002,
    actorType: "ADMIN",
    actorId: "1",
    targetType: "TAXONOMY",
    targetId: "10",
    actionType: "TAG_UPDATED",
    detailJson: "{\"slug\":\"spring-boot\"}",
    createdAt: "2026-06-28T10:55:00Z",
  },
];
let nextPostId = Math.max(...postsStore.map((post) => post.id), 100) + 1;
let nextTaxonomyId = Math.max(...categoriesStore.map((item) => item.id), ...tagsStore.map((item) => item.id), 12) + 1;
let nextAuditId = Math.max(...auditStore.map((entry) => entry.id), 9000) + 1;
const initialAutomationTopics: AutomationTopicResponse[] = [
  {
    id: 1,
    slug: "ai-daily-briefing",
    name: "AI Daily Briefing",
    promptTemplateVersion: "v1",
    publicationEnabled: true,
    createdAt: "2026-06-28T08:00:00Z",
    updatedAt: "2026-06-29T08:10:00Z",
  },
];
const initialAutomationSources: AutomationSourceResponse[] = [
  {
    id: 101,
    topicId: 1,
    sourceType: "RSS",
    sourceUrl: "https://example.com/feed.xml",
    enabled: true,
    createdAt: "2026-06-28T08:05:00Z",
    updatedAt: "2026-06-28T08:05:00Z",
  },
  {
    id: 102,
    topicId: 1,
    sourceType: "HTML",
    sourceUrl: "https://news.example.org/ai",
    enabled: true,
    createdAt: "2026-06-28T08:06:00Z",
    updatedAt: "2026-06-28T08:06:00Z",
  },
];
const initialAutomationSchedules: AutomationScheduleResponse[] = [
  {
    id: 201,
    topicId: 1,
    name: "Morning run",
    cronExpression: "0 0 9 * * *",
    timezone: "Asia/Seoul",
    status: "ACTIVE",
    misfirePolicy: "FIRE_ONCE_NOW",
    nextPlannedRunAt: "2026-06-30T00:00:00Z",
    syncStatus: "SYNCED",
    syncErrorMessage: null,
    lastSynchronizedAt: "2026-06-29T08:10:00Z",
    createdAt: "2026-06-28T08:07:00Z",
    updatedAt: "2026-06-29T08:10:00Z",
  },
  {
    id: 202,
    topicId: 1,
    name: "Evening retry",
    cronExpression: "0 0 20 * * *",
    timezone: "Asia/Seoul",
    status: "PAUSED",
    misfirePolicy: "DO_NOTHING",
    nextPlannedRunAt: "2026-06-30T11:00:00Z",
    syncStatus: "OUT_OF_SYNC",
    syncErrorMessage: "Quartz synchronization failed. Review the schedule and save it again after the scheduler recovers.",
    lastSynchronizedAt: null,
    createdAt: "2026-06-28T08:08:00Z",
    updatedAt: "2026-06-29T08:30:00Z",
  },
];
let automationTopicsStore = initialAutomationTopics.map((item) => ({ ...item }));
let automationSourcesStore = initialAutomationSources.map((item) => ({ ...item }));
let automationSchedulesStore = initialAutomationSchedules.map((item) => ({ ...item }));
let nextAutomationTopicId = Math.max(...initialAutomationTopics.map((item) => item.id), 1) + 1;
let nextAutomationSourceId = Math.max(...initialAutomationSources.map((item) => item.id), 100) + 1;
let nextAutomationScheduleId = Math.max(...initialAutomationSchedules.map((item) => item.id), 200) + 1;
let automationRunsStore: AutomationRunResponse[] = createInitialAutomationRuns();
let automationRunDetailsStore = createInitialAutomationRunDetails(automationRunsStore);
let automationOutboxStore: AutomationOutboxResponse[] = createInitialAutomationOutbox();

function createInitialAutomationRuns(): AutomationRunResponse[] {
  return [
    {
      id: 301,
      runKey: "run-301",
      topicId: 1,
      scheduleId: 201,
      retryOfRunId: null,
      triggerType: "SCHEDULED",
      status: "SUCCEEDED",
      idempotencyKey: "schedule:201:2026-06-29T00:00:00Z",
      holdReason: null,
      resolutionStatus: null,
      resolutionNote: null,
      resolvedPostId: null,
      snapshotCount: 2,
      startedAt: "2026-06-29T00:00:00Z",
      completedAt: "2026-06-29T00:00:11Z",
      createdAt: "2026-06-29T00:00:00Z",
      updatedAt: "2026-06-29T00:00:11Z",
    },
    {
      id: 302,
      runKey: "run-302",
      topicId: 1,
      scheduleId: null,
      retryOfRunId: null,
      triggerType: "MANUAL",
      status: "HELD",
      idempotencyKey: "manual:1:held",
      holdReason: "Material claims require corroboration across at least two independent origin hosts.",
      resolutionStatus: null,
      resolutionNote: null,
      resolvedPostId: null,
      snapshotCount: 1,
      startedAt: "2026-06-29T04:00:00Z",
      completedAt: "2026-06-29T04:00:05Z",
      createdAt: "2026-06-29T04:00:00Z",
      updatedAt: "2026-06-29T04:00:05Z",
    },
  ];
}

function createInitialAutomationRunDetails(runs: AutomationRunResponse[]) {
  return new Map<number, AutomationRunDetailResponse>([
    [
      301,
      {
        run: runs[0],
        generatedDraft: null,
        availableActions: {
          canRetry: false,
          canCancel: false,
          canOverridePublish: false,
        },
        snapshots: [
          {
            id: 5001,
            sourceUrl: "https://example.com/feed.xml",
            canonicalUrl: "https://example.com/posts/ai-update",
            originHost: "example.com",
            title: "AI update from source one",
            httpStatus: 200,
            policyResult: "ALLOWED",
            contentHash: "hash-1",
            retrievedAt: "2026-06-29T00:00:02Z",
          },
          {
            id: 5002,
            sourceUrl: "https://news.example.org/ai",
            canonicalUrl: "https://news.example.org/posts/ai-update",
            originHost: "news.example.org",
            title: "AI update from source two",
            httpStatus: 200,
            policyResult: "ALLOWED",
            contentHash: "hash-2",
            retrievedAt: "2026-06-29T00:00:03Z",
          },
        ],
      },
    ],
    [
      302,
      {
        run: runs[1],
        generatedDraft: {
          title: "보류된 자동 초안",
          excerpt: "출처가 부족해서 자동 발행이 보류된 초안입니다.",
          contentMarkdown: "# 보류된 자동 초안\n\n검토 후 수동 발행할 수 있습니다.",
          citationSnapshotIds: [5003],
        },
        availableActions: {
          canRetry: true,
          canCancel: false,
          canOverridePublish: true,
        },
        snapshots: [
          {
            id: 5003,
            sourceUrl: "https://example.com/feed.xml",
            canonicalUrl: "https://example.com/posts/duplicate-ai-update",
            originHost: "example.com",
            title: "Single-source duplicate candidate",
            httpStatus: 200,
            policyResult: "ALLOWED",
            contentHash: "hash-3",
            retrievedAt: "2026-06-29T04:00:01Z",
          },
        ],
      },
    ],
  ]);
}

function createInitialAutomationOutbox(): AutomationOutboxResponse[] {
  return [
    {
      id: 401,
      aggregateId: 101,
      deliveryStatus: "DELIVERED",
      payloadJson: "{\"postId\":101,\"slug\":\"spring-boot-automation-blog\"}",
      availableAt: "2026-06-29T00:00:11Z",
      processedAt: "2026-06-29T00:00:12Z",
      lastAttemptAt: "2026-06-29T00:00:12Z",
      createdAt: "2026-06-29T00:00:11Z",
    },
    {
      id: 402,
      aggregateId: 102,
      deliveryStatus: "PENDING",
      payloadJson: "{\"postId\":102,\"slug\":\"ai-daily-briefing-2\"}",
      availableAt: "2026-06-29T08:20:00Z",
      processedAt: null,
      lastAttemptAt: "2026-06-29T08:15:00Z",
      createdAt: "2026-06-29T08:10:00Z",
    },
  ];
}

function cloneAutomationRun(run: AutomationRunResponse): AutomationRunResponse {
  return { ...run };
}

function cloneAutomationRunDetail(detail: AutomationRunDetailResponse): AutomationRunDetailResponse {
  return {
    run: cloneAutomationRun(detail.run),
    generatedDraft: detail.generatedDraft
      ? {
          ...detail.generatedDraft,
          citationSnapshotIds: [...detail.generatedDraft.citationSnapshotIds],
        }
      : null,
    availableActions: { ...detail.availableActions },
    snapshots: detail.snapshots.map((snapshot) => ({ ...snapshot })),
  };
}

function summarize(post: AdminPostDetail) {
  return {
    id: post.id,
    slug: post.slug,
    title: post.title,
    excerpt: post.excerpt,
    status: post.status,
    firstPublishedAt: post.firstPublishedAt,
    viewCount: post.viewCount,
    categories: post.categories.map((item) => item.name),
    tags: post.tags.map((item) => item.name),
    categoryDetails: cloneTaxonomy(post.categories),
    tagDetails: cloneTaxonomy(post.tags),
  };
}

function recordAudit(targetType: string, targetId: string, actionType: string, detail: Record<string, unknown>) {
  auditStore = [
    {
      id: nextAuditId++,
      actorType: "ADMIN",
      actorId: "1",
      targetType,
      targetId,
      actionType,
      detailJson: JSON.stringify(detail),
      createdAt: new Date().toISOString(),
    },
    ...auditStore,
  ];
}

function postById(id: number) {
  const post = postsStore.find((item) => item.id === id);
  if (!post) {
    throw new Error(`Post ${id} not found`);
  }
  return post;
}

function selectedTaxonomy(ids: number[], store: TaxonomyResponse[]) {
  return ids.map((id) => store.find((item) => item.id === id)).filter(Boolean) as TaxonomyResponse[];
}

export function resetAdminMockState() {
  categoriesStore = cloneTaxonomy(initialCategories);
  tagsStore = cloneTaxonomy(initialTags);
  postsStore = createInitialPosts();
  revisionsStore = buildInitialRevisions(postsStore);
  auditStore = [
    {
      id: 9001,
      actorType: "ADMIN",
      actorId: "1",
      targetType: "POST",
      targetId: "101",
      actionType: "POST_PUBLISHED",
      detailJson: "{\"slug\":\"spring-boot-automation-blog\"}",
      createdAt: "2026-06-29T06:25:00Z",
    },
    {
      id: 9002,
      actorType: "ADMIN",
      actorId: "1",
      targetType: "TAXONOMY",
      targetId: "10",
      actionType: "TAG_UPDATED",
      detailJson: "{\"slug\":\"spring-boot\"}",
      createdAt: "2026-06-28T10:55:00Z",
    },
  ];
  nextPostId = Math.max(...postsStore.map((post) => post.id), 100) + 1;
  nextTaxonomyId = Math.max(...categoriesStore.map((item) => item.id), ...tagsStore.map((item) => item.id), 12) + 1;
  nextAuditId = Math.max(...auditStore.map((entry) => entry.id), 9000) + 1;
  automationTopicsStore = initialAutomationTopics.map((item) => ({ ...item }));
  automationSourcesStore = initialAutomationSources.map((item) => ({ ...item }));
  automationSchedulesStore = initialAutomationSchedules.map((item) => ({ ...item }));
  nextAutomationTopicId = Math.max(...initialAutomationTopics.map((item) => item.id), 1) + 1;
  nextAutomationSourceId = Math.max(...initialAutomationSources.map((item) => item.id), 100) + 1;
  nextAutomationScheduleId = Math.max(...initialAutomationSchedules.map((item) => item.id), 200) + 1;
  automationRunsStore = createInitialAutomationRuns();
  automationRunDetailsStore = createInitialAutomationRunDetails(automationRunsStore);
  automationOutboxStore = createInitialAutomationOutbox();
}

export function mockAutomationTopics() {
  return automationTopicsStore.map((item) => ({ ...item }));
}

export function mockSaveAutomationTopic(editingId: number | null, request: AutomationTopicUpsertRequest) {
  const now = new Date().toISOString();
  const record: AutomationTopicResponse = {
    id: editingId ?? nextAutomationTopicId++,
    slug: request.slug?.trim() || slugify(request.name),
    name: request.name.trim(),
    promptTemplateVersion: request.promptTemplateVersion.trim(),
    publicationEnabled: request.publicationEnabled,
    createdAt: editingId
      ? (automationTopicsStore.find((item) => item.id === editingId)?.createdAt ?? now)
      : now,
    updatedAt: now,
  };

  automationTopicsStore = editingId
    ? automationTopicsStore.map((item) => (item.id === editingId ? record : item))
    : [...automationTopicsStore, record];
  recordAudit("AUTOMATION", String(record.id), editingId ? "AUTOMATION_TOPIC_UPDATED" : "AUTOMATION_TOPIC_CREATED", {
    slug: record.slug,
  });
  return { ...record };
}

export function mockAutomationSources(topicId: number) {
  return automationSourcesStore.filter((item) => item.topicId === topicId).map((item) => ({ ...item }));
}

export function mockSaveAutomationSource(
  topicId: number,
  editingId: number | null,
  request: AutomationSourceUpsertRequest,
) {
  const now = new Date().toISOString();
  const existing = editingId ? automationSourcesStore.find((item) => item.id === editingId) : null;
  const record: AutomationSourceResponse = {
    id: editingId ?? nextAutomationSourceId++,
    topicId: existing?.topicId ?? topicId,
    sourceType: request.sourceType,
    sourceUrl: request.sourceUrl.trim(),
    enabled: request.enabled,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };

  automationSourcesStore = editingId
    ? automationSourcesStore.map((item) => (item.id === editingId ? record : item))
    : [...automationSourcesStore, record];
  recordAudit("AUTOMATION", String(record.id), editingId ? "AUTOMATION_SOURCE_UPDATED" : "AUTOMATION_SOURCE_CREATED", {
    topicId: record.topicId,
  });
  return { ...record };
}

export function mockDeleteAutomationSource(sourceId: number) {
  const source = automationSourcesStore.find((item) => item.id === sourceId);
  if (!source) {
    throw new Error("Automation source not found.");
  }
  const isReferenced = Array.from(automationRunDetailsStore.values()).some((detail) =>
    detail.snapshots.some((snapshot) => snapshot.sourceUrl === source.sourceUrl),
  );
  if (isReferenced) {
    throw new Error("This source is already referenced by collected evidence. Disable it instead of deleting it.");
  }
  automationSourcesStore = automationSourcesStore.filter((item) => item.id !== sourceId);
  recordAudit("AUTOMATION", String(sourceId), "AUTOMATION_SOURCE_DELETED", {});
}

export function mockAutomationSchedules(topicId: number) {
  return automationSchedulesStore.filter((item) => item.topicId === topicId).map((item) => ({ ...item }));
}

export function mockSaveAutomationSchedule(
  topicId: number,
  editingId: number | null,
  request: AutomationScheduleUpsertRequest,
) {
  const now = new Date().toISOString();
  const existing = editingId ? automationSchedulesStore.find((item) => item.id === editingId) : null;
  const record: AutomationScheduleResponse = {
    id: editingId ?? nextAutomationScheduleId++,
    topicId: existing?.topicId ?? topicId,
    name: request.name.trim(),
    cronExpression: request.cronExpression.trim(),
    timezone: request.timezone.trim(),
    status: request.status,
    misfirePolicy: request.misfirePolicy,
    nextPlannedRunAt: existing?.status === "DISABLED" && request.status === "DISABLED" ? null : now,
    syncStatus: "SYNCED",
    syncErrorMessage: null,
    lastSynchronizedAt: now,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };

  automationSchedulesStore = editingId
    ? automationSchedulesStore.map((item) => (item.id === editingId ? record : item))
    : [...automationSchedulesStore, record];
  recordAudit(
    "AUTOMATION",
    String(record.id),
    editingId ? "AUTOMATION_SCHEDULE_UPDATED" : "AUTOMATION_SCHEDULE_CREATED",
    { topicId: record.topicId },
  );
  return { ...record };
}

export function mockDeleteAutomationSchedule(scheduleId: number) {
  const schedule = automationSchedulesStore.find((item) => item.id === scheduleId);
  if (!schedule) {
    throw new Error("Automation schedule not found.");
  }
  const hasHistory = automationRunsStore.some((item) => item.scheduleId === scheduleId);
  if (hasHistory) {
    throw new Error("This schedule already has run history. Disable it instead of deleting it.");
  }
  automationSchedulesStore = automationSchedulesStore.filter((item) => item.id !== scheduleId);
  recordAudit("AUTOMATION", String(scheduleId), "AUTOMATION_SCHEDULE_DELETED", {});
}

export function mockAutomationRuns() {
  return automationRunsStore.map(cloneAutomationRun);
}

export function mockAutomationRunDetail(runId: number) {
  const detail = automationRunDetailsStore.get(runId);
  return detail ? cloneAutomationRunDetail(detail) : null;
}

export function mockTriggerAutomationRun(topicId: number) {
  const now = new Date().toISOString();
  const run: AutomationRunResponse = {
    id: Math.max(...automationRunsStore.map((item) => item.id), 300) + 1,
    runKey: `run-${Date.now()}`,
    topicId,
    scheduleId: null,
    retryOfRunId: null,
    triggerType: "MANUAL",
    status: "RUNNING",
    idempotencyKey: `manual:${topicId}:${Date.now()}`,
    holdReason: null,
    resolutionStatus: null,
    resolutionNote: null,
    resolvedPostId: null,
    snapshotCount: 2,
    startedAt: now,
    completedAt: null,
    createdAt: now,
    updatedAt: now,
  };
  automationRunsStore = [run, ...automationRunsStore];
  automationRunDetailsStore.set(run.id, {
    run,
    generatedDraft: null,
    availableActions: {
      canRetry: false,
      canCancel: true,
      canOverridePublish: false,
    },
    snapshots: [
      {
        id: Date.now(),
        sourceUrl: "https://example.com/feed.xml",
        canonicalUrl: "https://example.com/posts/latest",
        originHost: "example.com",
        title: "Manual automation source",
        httpStatus: 200,
        policyResult: "ALLOWED",
        contentHash: "hash-latest-1",
        retrievedAt: now,
      },
      {
        id: Date.now() + 1,
        sourceUrl: "https://news.example.org/ai",
        canonicalUrl: "https://news.example.org/posts/latest",
        originHost: "news.example.org",
        title: "Manual corroborating source",
        httpStatus: 200,
        policyResult: "ALLOWED",
        contentHash: "hash-latest-2",
        retrievedAt: now,
      },
    ],
  });
  recordAudit("AUTOMATION", String(run.id), "AUTOMATION_RUN_STARTED", { topicId, triggerType: "MANUAL" });
  return { ...run };
}

export function mockAutomationRunRetry(runId: number) {
  const original = automationRunsStore.find((item) => item.id === runId);
  const originalDetail = automationRunDetailsStore.get(runId);
  if (!original || !originalDetail || original.status !== "HELD" || original.resolutionStatus) {
    throw new Error("Only unresolved held automation runs can be retried.");
  }

  const now = new Date().toISOString();
  const retriedRun: AutomationRunResponse = {
    ...cloneAutomationRun(original),
    id: Math.max(...automationRunsStore.map((item) => item.id), 300) + 1,
    runKey: `run-${Date.now()}`,
    retryOfRunId: original.id,
    idempotencyKey: `retry:${original.id}:${Date.now()}`,
    resolutionStatus: null,
    resolutionNote: null,
    resolvedPostId: null,
    createdAt: now,
    updatedAt: now,
  };
  const updatedOriginal: AutomationRunResponse = {
    ...original,
    resolutionStatus: "RETRIED",
    resolutionNote: `Retried as run ${retriedRun.id}`,
    updatedAt: now,
  };

  automationRunsStore = [retriedRun, ...automationRunsStore.map((item) => (item.id === runId ? updatedOriginal : item))];
  automationRunDetailsStore.set(runId, {
    ...cloneAutomationRunDetail(originalDetail),
    run: updatedOriginal,
    availableActions: {
      canRetry: false,
      canCancel: false,
      canOverridePublish: false,
    },
  });
  automationRunDetailsStore.set(retriedRun.id, {
    ...cloneAutomationRunDetail(originalDetail),
    run: retriedRun,
  });
  recordAudit("AUTOMATION", String(runId), "AUTOMATION_RUN_RETRIED", { retryRunId: retriedRun.id });
  return cloneAutomationRun(retriedRun);
}

export function mockAutomationRunCancel(runId: number) {
  const run = automationRunsStore.find((item) => item.id === runId);
  const detail = automationRunDetailsStore.get(runId);
  if (!run || !detail || run.status !== "RUNNING") {
    throw new Error("Only active automation runs can be cancelled.");
  }

  const now = new Date().toISOString();
  const cancelledRun: AutomationRunResponse = {
    ...run,
    status: "FAILED",
    holdReason: "Cancelled by the administrator.",
    resolutionStatus: "CANCELLED",
    resolutionNote: "Cancelled by the administrator.",
    completedAt: now,
    updatedAt: now,
  };

  automationRunsStore = automationRunsStore.map((item) => (item.id === runId ? cancelledRun : item));
  automationRunDetailsStore.set(runId, {
    ...cloneAutomationRunDetail(detail),
    run: cancelledRun,
    availableActions: {
      canRetry: false,
      canCancel: false,
      canOverridePublish: false,
    },
  });
  recordAudit("AUTOMATION", String(runId), "AUTOMATION_RUN_CANCELLED", {});
  return cloneAutomationRun(cancelledRun);
}

export function mockAutomationRunOverridePublish(runId: number): AutomationRunOverridePublishResponse {
  const run = automationRunsStore.find((item) => item.id === runId);
  const detail = automationRunDetailsStore.get(runId);
  if (!run || !detail || !detail.availableActions.canOverridePublish || !detail.generatedDraft) {
    throw new Error("Only approved held automation runs with a stored draft can be published manually.");
  }

  const now = new Date().toISOString();
  const post = mockCreatePost({
    slug: "",
    title: detail.generatedDraft.title,
    excerpt: detail.generatedDraft.excerpt,
    contentMarkdown: detail.generatedDraft.contentMarkdown,
    contentHtml: previewHtmlFromMarkdown(detail.generatedDraft.contentMarkdown),
    categoryIds: [2],
    tagIds: [12],
    revisionNote: "자동화 보류 초안을 관리자가 수동 발행",
  });
  mockChangePostState(post.id, "publish");

  const publishedRun: AutomationRunResponse = {
    ...run,
    resolutionStatus: "OVERRIDE_PUBLISHED",
    resolutionNote: `Published manually as post ${post.id}`,
    resolvedPostId: post.id,
    updatedAt: now,
  };
  automationRunsStore = automationRunsStore.map((item) => (item.id === runId ? publishedRun : item));
  automationRunDetailsStore.set(runId, {
    ...cloneAutomationRunDetail(detail),
    run: publishedRun,
    availableActions: {
      canRetry: false,
      canCancel: false,
      canOverridePublish: false,
    },
  });
  recordAudit("AUTOMATION", String(runId), "AUTOMATION_RUN_OVERRIDE_PUBLISHED", { postId: post.id, slug: post.slug });
  return { runId, postId: post.id, slug: post.slug };
}

export function mockAutomationOutbox() {
  return automationOutboxStore.map((item) => ({ ...item }));
}

export function mockAutomationDiagnostics(): AutomationDiagnosticsResponse {
  return {
    runCounts: {
      running: 0,
      succeeded: automationRunsStore.filter((item) => item.status === "SUCCEEDED").length,
      held: automationRunsStore.filter((item) => item.status === "HELD").length,
      failed: automationRunsStore.filter((item) => item.status === "FAILED").length,
    },
    jobCounts: {
      pending: 1,
      claimed: 0,
      submitted: 2,
      failed: 0,
    },
    outboxCounts: {
      pending: automationOutboxStore.filter((item) => item.deliveryStatus === "PENDING").length,
      delivered: automationOutboxStore.filter((item) => item.deliveryStatus === "DELIVERED").length,
    },
    heldSnapshotCount: 1,
    recentHoldReasons: automationRunsStore.map((item) => item.holdReason).filter((item): item is string => Boolean(item)),
    generatedAt: new Date().toISOString(),
  };
}

export function mockProcessAutomationOutbox() {
  const now = new Date().toISOString();
  automationOutboxStore = automationOutboxStore.map((item) =>
    item.deliveryStatus === "PENDING"
      ? {
          ...item,
          deliveryStatus: "DELIVERED",
          processedAt: now,
          lastAttemptAt: now,
        }
      : item,
  );
  recordAudit("AUTOMATION", "outbox", "AUTOMATION_OUTBOX_REPLAYED", {});
}

export function mockAdminPosts(): AdminPostPage {
  const items = postsStore
    .slice()
    .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
    .map(summarize);
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
  };
}

export function mockAdminPostDetail(id: number) {
  const post = postsStore.find((item) => item.id === id);
  return post ? clonePostDetail(post) : null;
}

export function mockCategories() {
  return cloneTaxonomy(categoriesStore);
}

export function mockTags() {
  return cloneTaxonomy(tagsStore);
}

export function mockAuditEntries(): AuditPage {
  return {
    items: auditStore.map((entry) => ({ ...entry })),
    page: 0,
    size: 20,
    totalElements: auditStore.length,
    totalPages: 1,
  };
}

export function mockPostRevisions(postId: number) {
  return (revisionsStore.get(postId) ?? []).map((revision) => ({ ...revision }));
}

export function mockCreatePost(request: AdminPostUpsertRequest) {
  const now = new Date().toISOString();
  const title = request.title.trim();
  const slug = request.slug?.trim() || slugify(title);
  const detail: AdminPostDetail = {
    id: nextPostId++,
    slug,
    title,
    excerpt: request.excerpt.trim(),
    contentMarkdown: request.contentMarkdown,
    contentHtml: request.contentHtml || markdownToHtml(request.contentMarkdown),
    status: "DRAFT",
    firstPublishedAt: null,
    createdAt: now,
    updatedAt: now,
    viewCount: 0,
    categories: selectedTaxonomy(request.categoryIds, categoriesStore),
    tags: selectedTaxonomy(request.tagIds, tagsStore),
    relatedPosts: [],
  };

  postsStore = [detail, ...postsStore];
  revisionsStore.set(detail.id, [makeRevision(detail, 1, "MANUAL_CREATE", request.revisionNote ?? null)]);
  recordAudit("POST", String(detail.id), "POST_CREATED", { slug: detail.slug });
  return clonePostDetail(detail);
}

export function mockUpdatePost(postId: number, request: AdminPostUpsertRequest) {
  const post = postById(postId);
  const updated = {
    ...post,
    slug: request.slug?.trim() || slugify(request.title),
    title: request.title.trim(),
    excerpt: request.excerpt.trim(),
    contentMarkdown: request.contentMarkdown,
    contentHtml: request.contentHtml || markdownToHtml(request.contentMarkdown),
    updatedAt: new Date().toISOString(),
    categories: selectedTaxonomy(request.categoryIds, categoriesStore),
    tags: selectedTaxonomy(request.tagIds, tagsStore),
  } satisfies AdminPostDetail;

  postsStore = postsStore.map((item) => (item.id === postId ? updated : item));
  const revisions = revisionsStore.get(postId) ?? [];
  revisionsStore.set(postId, [
    makeRevision(updated, revisions.length + 1, "MANUAL_EDIT", request.revisionNote ?? null),
    ...revisions,
  ]);
  recordAudit("POST", String(postId), "POST_UPDATED", { slug: updated.slug });
  return clonePostDetail(updated);
}

export function mockChangePostState(postId: number, action: "publish" | "archive" | "delete" | "restore") {
  const post = postById(postId);
  const now = new Date().toISOString();
  const updated = { ...post };

  if (action === "publish") {
    updated.status = "PUBLISHED";
    updated.firstPublishedAt = updated.firstPublishedAt ?? now;
    updated.updatedAt = now;
    recordAudit("POST", String(postId), "POST_PUBLISHED", { slug: updated.slug });
  }

  if (action === "archive") {
    updated.status = "ARCHIVED";
    updated.updatedAt = now;
    recordAudit("POST", String(postId), "POST_ARCHIVED", { slug: updated.slug });
  }

  if (action === "delete") {
    updated.status = "DELETED";
    updated.updatedAt = now;
    recordAudit("POST", String(postId), "POST_DELETED", { slug: updated.slug });
  }

  if (action === "restore") {
    updated.status = "DRAFT";
    updated.updatedAt = now;
    recordAudit("POST", String(postId), "POST_RESTORED", { slug: updated.slug });
  }

  postsStore = postsStore.map((item) => (item.id === postId ? updated : item));
  return clonePostDetail(updated);
}

export function mockRestoreRevision(postId: number, revisionNumber: number) {
  const post = postById(postId);
  const revision = (revisionsStore.get(postId) ?? []).find((item) => item.revisionNumber === revisionNumber);
  if (!revision) {
    throw new Error(`Revision ${revisionNumber} not found`);
  }

  const restored: AdminPostDetail = {
    ...post,
    title: revision.title,
    excerpt: revision.excerpt,
    contentMarkdown: revision.contentMarkdown,
    contentHtml: revision.contentHtml,
    updatedAt: new Date().toISOString(),
  };

  postsStore = postsStore.map((item) => (item.id === postId ? restored : item));
  const revisions = revisionsStore.get(postId) ?? [];
  revisionsStore.set(postId, [
    makeRevision(restored, revisions.length + 1, "MANUAL_RESTORE", `Revision ${revisionNumber} restored`),
    ...revisions,
  ]);
  recordAudit("POST", String(postId), "POST_REVISION_RESTORED", { revisionNumber });
  return clonePostDetail(restored);
}

function saveTaxonomy(kind: "category" | "tag", editingId: number | null, request: { name: string; slug?: string; description?: string }) {
  const store = kind === "category" ? categoriesStore : tagsStore;
  const slug = request.slug?.trim() || slugify(request.name);
  const record: TaxonomyResponse = {
    id: editingId ?? nextTaxonomyId++,
    name: request.name.trim(),
    slug,
    description: request.description?.trim() || null,
  };

  if (editingId) {
    const next = store.map((item) => (item.id === editingId ? record : item));
    if (kind === "category") {
      categoriesStore = next;
      postsStore = postsStore.map((post) => ({
        ...post,
        categories: post.categories.map((item) => (item.id === editingId ? record : item)),
      }));
    } else {
      tagsStore = next;
      postsStore = postsStore.map((post) => ({
        ...post,
        tags: post.tags.map((item) => (item.id === editingId ? record : item)),
      }));
    }
    recordAudit("TAXONOMY", String(record.id), `${kind.toUpperCase()}_UPDATED`, { slug: record.slug });
  } else if (kind === "category") {
    categoriesStore = [record, ...categoriesStore];
    recordAudit("TAXONOMY", String(record.id), "CATEGORY_CREATED", { slug: record.slug });
  } else {
    tagsStore = [record, ...tagsStore];
    recordAudit("TAXONOMY", String(record.id), "TAG_CREATED", { slug: record.slug });
  }

  return { ...record };
}

export function mockSaveCategory(editingId: number | null, request: { name: string; slug?: string; description?: string }) {
  return saveTaxonomy("category", editingId, request);
}

export function mockSaveTag(editingId: number | null, request: { name: string; slug?: string; description?: string }) {
  return saveTaxonomy("tag", editingId, request);
}

export function mockDeleteCategory(id: number) {
  categoriesStore = categoriesStore.filter((item) => item.id !== id);
  postsStore = postsStore.map((post) => ({ ...post, categories: post.categories.filter((item) => item.id !== id) }));
  recordAudit("TAXONOMY", String(id), "CATEGORY_DELETED", {});
}

export function mockDeleteTag(id: number) {
  tagsStore = tagsStore.filter((item) => item.id !== id);
  postsStore = postsStore.map((post) => ({ ...post, tags: post.tags.filter((item) => item.id !== id) }));
  recordAudit("TAXONOMY", String(id), "TAG_DELETED", {});
}

export function previewHtmlFromMarkdown(markdown: string) {
  return markdownToHtml(markdown);
}
