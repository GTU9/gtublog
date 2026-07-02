import { beforeEach, describe, expect, it } from "vitest";

import {
  mockAdminPostDetail,
  mockAdminPosts,
  mockChangePostState,
  mockCreatePost,
  mockDeleteAutomationSchedule,
  mockDeleteAutomationSource,
  mockDeleteCategory,
  mockPostRevisions,
  mockRestoreRevision,
  mockSaveAutomationSchedule,
  mockSaveAutomationSource,
  mockSaveAutomationTopic,
  mockSaveCategory,
  previewHtmlFromMarkdown,
  resetAdminMockState,
} from "./admin-mock";

describe("admin mock state", () => {
  beforeEach(() => {
    resetAdminMockState();
  });

  it("escapes active HTML in the Markdown preview", () => {
    const preview = previewHtmlFromMarkdown('<img src=x onerror="alert(1)">\n\n<script>alert(2)</script>');

    expect(preview).toContain("&lt;img");
    expect(preview).toContain("&lt;script&gt;");
    expect(preview).not.toContain("<img");
    expect(preview).not.toContain("<script");
  });

  it("creates drafts and tracks them in the admin listing", () => {
    const created = mockCreatePost({
      title: "Admin authored draft",
      excerpt: "Draft excerpt",
      contentMarkdown: "First paragraph\n\nSecond paragraph",
      contentHtml: previewHtmlFromMarkdown("First paragraph\n\nSecond paragraph"),
      categoryIds: [1],
      tagIds: [10],
      revisionNote: "Initial draft",
    });

    expect(created.status).toBe("DRAFT");
    expect(mockAdminPosts().items[0]?.title).toBe("Admin authored draft");
    expect(mockPostRevisions(created.id)).toHaveLength(1);
  });

  it("restores an older revision while keeping the post accessible", () => {
    const detail = mockAdminPostDetail(101);
    expect(detail).not.toBeNull();
    if (!detail) {
      return;
    }

    mockChangePostState(detail.id, "archive");
    const restored = mockRestoreRevision(detail.id, 1);

    expect(restored.title).toBe(detail.title);
    expect(mockPostRevisions(detail.id)[0]?.revisionSource).toBe("MANUAL_RESTORE");
  });

  it("updates taxonomy and removes deleted categories from linked posts", () => {
    const category = mockSaveCategory(null, {
      name: "AI News",
      slug: "ai-news",
      description: "Fresh automation coverage",
    });

    const created = mockCreatePost({
      title: "AI update",
      excerpt: "Category link check",
      contentMarkdown: "AI article",
      contentHtml: "<p>AI article</p>",
      categoryIds: [category.id],
      tagIds: [],
      revisionNote: "Created with new category",
    });

    expect(created.categories[0]?.slug).toBe("ai-news");
    mockDeleteCategory(category.id);
    expect(mockAdminPostDetail(created.id)?.categories).toHaveLength(0);
  });

  it("creates automation configuration records and blocks deletion when history exists", () => {
    const topic = mockSaveAutomationTopic(null, {
      name: "Daily roundup",
      slug: "daily-roundup",
      promptTemplateVersion: "v2",
      publicationEnabled: true,
    });
    const source = mockSaveAutomationSource(topic.id, null, {
      sourceType: "RSS",
      sourceUrl: "https://fresh.example.com/feed.xml",
      enabled: true,
    });
    const schedule = mockSaveAutomationSchedule(topic.id, null, {
      name: "Lunch run",
      cronExpression: "0 0 12 * * *",
      timezone: "Asia/Seoul",
      status: "ACTIVE",
      misfirePolicy: "FIRE_ONCE_NOW",
    });

    expect(topic.slug).toBe("daily-roundup");
    expect(source.topicId).toBe(topic.id);
    expect(schedule.syncStatus).toBe("SYNCED");

    expect(() => mockDeleteAutomationSource(101)).toThrow(/Disable it instead of deleting it/);
    expect(() => mockDeleteAutomationSchedule(201)).toThrow(/Disable it instead of deleting it/);
  });
});
