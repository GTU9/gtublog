import { describe, expect, it } from "vitest";

import { siteDescription, siteName } from "./site";

describe("site metadata", () => {
  it("provides non-empty Korean metadata for the initial page", () => {
    expect(siteName).toMatch(/블로그/);
    expect(siteDescription.length).toBeGreaterThan(20);
  });
});
