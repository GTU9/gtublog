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
