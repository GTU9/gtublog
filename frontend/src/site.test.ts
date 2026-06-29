import { describe, expect, it } from "vitest";

import { absoluteUrl, siteDescription, siteName, siteUrl } from "./site";

describe("site metadata", () => {
  it("provides non-empty public blog metadata", () => {
    expect(siteName).toContain("Blog");
    expect(siteDescription.length).toBeGreaterThan(20);
  });

  it("builds canonical absolute urls from the configured site url", () => {
    expect(absoluteUrl("/posts/example")).toBe(`${siteUrl}/posts/example`);
  });
});
