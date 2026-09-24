import { describe, expect, it } from "vitest";
import { pageHref, pageIndex } from "./pagination";

describe("public pagination URLs", () => {
  it.each([undefined, "0", "-1", "1.5", "NaN", "Infinity", "100000000000000000", ["2", "3"]])("normalizes invalid page %j", (value) => {
    expect(pageIndex(value)).toBe(0);
  });
  it("converts display pages to zero-based API pages", () => {
    expect(pageIndex("1")).toBe(0);
    expect(pageIndex("2")).toBe(1);
  });
  it("preserves query and archive filters without double encoding", () => {
    expect(pageHref("/search?q=C%2B%2B&page=9", 1)).toBe("/search?q=C%2B%2B&page=2");
    expect(pageHref("/archive?year=2026&month=6", 2)).toBe("/archive?year=2026&month=6&page=3");
    expect(pageHref("/tags/react?page=2", 0)).toBe("/tags/react");
  });
});
