import { describe, expect, it } from "vitest";

import { mockArchiveEntries, mockCategoryPage, mockPostBySlug, mockSearchPage, mockTagPage } from "./mock-content";

describe("mock content fixtures", () => {
  it("provides a sample public post detail for e2e and development fallback", () => {
    expect(mockPostBySlug("spring-boot-automation-blog")?.title).toContain("Spring Boot");
  });

  it("supports taxonomy and search views from the same fixture source", () => {
    expect(mockCategoryPage("development").items.length).toBeGreaterThan(0);
    expect(mockTagPage("react").items.length).toBeGreaterThan(0);
    expect(mockSearchPage("automation").items.length).toBeGreaterThan(0);
    expect(mockArchiveEntries().length).toBeGreaterThan(0);
  });
});
