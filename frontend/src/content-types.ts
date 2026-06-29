export type TaxonomyItem = {
  id: number;
  slug: string;
  name: string;
  description: string | null;
};

export type PostSummary = {
  id: number;
  slug: string;
  title: string;
  excerpt: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED" | "DELETED";
  firstPublishedAt: string | null;
  viewCount: number;
  categories: string[];
  tags: string[];
  categoryDetails?: TaxonomyItem[];
  tagDetails?: TaxonomyItem[];
};

export type PostDetail = {
  id: number;
  slug: string;
  title: string;
  excerpt: string;
  contentMarkdown: string;
  contentHtml: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED" | "DELETED";
  firstPublishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  viewCount: number;
  categories: TaxonomyItem[];
  tags: TaxonomyItem[];
  relatedPosts: PostSummary[];
};

export type PostPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ArchiveEntry = {
  year: number;
  month: number;
  count: number;
};

