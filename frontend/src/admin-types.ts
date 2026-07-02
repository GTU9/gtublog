import type { PostDetail, PostPage, PostSummary, TaxonomyItem } from "./content-types";

export type AdminProfile = {
  id: number;
  username: string;
  displayName: string;
};

export type AuthSessionResponse = {
  accessToken: string;
  csrfToken: string;
  tokenType: string;
  expiresInSeconds: number;
  admin: AdminProfile;
};

export type AuthState = {
  status: "loading" | "authenticated" | "unauthenticated";
  admin: AdminProfile | null;
};

export type TaxonomyResponse = TaxonomyItem;

export type AuditEntryResponse = {
  id: number;
  actorType: string;
  actorId: string;
  targetType: string;
  targetId: string;
  actionType: string;
  detailJson: string;
  createdAt: string;
};

export type PostRevisionResponse = {
  id: number;
  revisionNumber: number;
  title: string;
  excerpt: string;
  contentMarkdown: string;
  contentHtml: string;
  revisionSource: string;
  revisionNote: string | null;
  createdAt: string;
};

export type AdminPostUpsertRequest = {
  slug?: string;
  title: string;
  excerpt: string;
  contentMarkdown: string;
  contentHtml: string;
  sourceFingerprint?: string | null;
  categoryIds: number[];
  tagIds: number[];
  revisionNote?: string | null;
};

export type AuditPage = PostPage<AuditEntryResponse>;
export type AdminPostPage = PostPage<PostSummary>;
export type AdminPostDetail = PostDetail;

export type AutomationTopicResponse = {
  id: number;
  slug: string;
  name: string;
  promptTemplateVersion: string;
  publicationEnabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AutomationTopicUpsertRequest = {
  slug?: string;
  name: string;
  promptTemplateVersion: string;
  publicationEnabled: boolean;
};

export type AutomationSourceResponse = {
  id: number;
  topicId: number;
  sourceType: string;
  sourceUrl: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AutomationSourceUpsertRequest = {
  sourceType: string;
  sourceUrl: string;
  enabled: boolean;
};

export type AutomationScheduleResponse = {
  id: number;
  topicId: number;
  name: string;
  cronExpression: string;
  timezone: string;
  status: string;
  misfirePolicy: string;
  nextPlannedRunAt: string | null;
  syncStatus: string;
  syncErrorMessage: string | null;
  lastSynchronizedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AutomationScheduleUpsertRequest = {
  name: string;
  cronExpression: string;
  timezone: string;
  status: string;
  misfirePolicy: string;
};

export type AutomationRunResponse = {
  id: number;
  runKey: string;
  topicId: number;
  scheduleId: number | null;
  triggerType: string;
  status: string;
  idempotencyKey: string;
  holdReason: string | null;
  snapshotCount: number;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AutomationRunDetailResponse = {
  run: AutomationRunResponse;
  snapshots: {
    id: number;
    sourceUrl: string;
    canonicalUrl: string;
    originHost: string;
    title: string;
    httpStatus: number;
    policyResult: string;
    contentHash: string;
    retrievedAt: string;
  }[];
};

export type AutomationOutboxResponse = {
  id: number;
  aggregateId: number;
  deliveryStatus: string;
  payloadJson: string;
  availableAt: string | null;
  processedAt: string | null;
  lastAttemptAt: string | null;
  createdAt: string;
};

export type AutomationDiagnosticsResponse = {
  runCounts: {
    running: number;
    succeeded: number;
    held: number;
    failed: number;
  };
  jobCounts: {
    pending: number;
    claimed: number;
    submitted: number;
    failed: number;
  };
  outboxCounts: {
    pending: number;
    delivered: number;
  };
  heldSnapshotCount: number;
  recentHoldReasons: string[];
  generatedAt: string;
};
