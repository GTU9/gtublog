import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { revalidationBodyLimit, verifyRevalidationEvent } from "./revalidation";

const secret = "test-only-revalidation-secret-0123456789abcdef";
const eventKey = "11111111-2222-4333-8444-555555555555";
const now = 1_800_000_000;
const validEvent = {
  version: 1, eventKey, postId: 42, slug: "new-post",
  tags: ["public-posts"],
  paths: ["/", "/archive", "/search", "/rss.xml", "/sitemap.xml", "/posts/old-post", "/posts/new-post"],
};

function signed(event: unknown = validEvent, timestamp = now, key = eventKey, signingSecret = secret) {
  const body = Buffer.from(JSON.stringify(event));
  const signature = createHmac("sha256", signingSecret)
    .update(`${timestamp}\n${key}\n`, "ascii").update(body).digest("hex");
  const headers = new Headers({
    "X-Revalidation-Timestamp": String(timestamp),
    "X-Revalidation-Event-Key": key,
    "X-Revalidation-Signature": signature,
  });
  return { body, headers };
}

describe("signed revalidation event", () => {
  it("keeps the schema path allowlist aligned with the receiver", () => {
    const schema = JSON.parse(readFileSync(new URL("../../contracts/revalidation/v1/event.schema.json", import.meta.url), "utf8")) as {
      properties: { paths: { items: { oneOf: ({ enum?: string[]; pattern?: string })[] } } };
    };
    const options = schema.properties.paths.items.oneOf;
    const allowedBySchema = (path: string) => options.some((option) =>
      option.enum?.includes(path) || (option.pattern && new RegExp(option.pattern, "u").test(path)));
    for (const path of ["/", "/archive", "/search", "/rss.xml", "/sitemap.xml", "/posts/글-1", "/categories/news", "/tags/topic"]) {
      expect(allowedBySchema(path)).toBe(true);
    }
    for (const path of ["/admin", "/posts/post?draft=1", "/posts/a_b", "/api/revalidate"]) {
      expect(allowedBySchema(path)).toBe(false);
    }
  });
  it("verifies the cross-process raw-body fixture", () => {
    const body = readFileSync(new URL("../../contracts/revalidation/v1/fixtures/event.json", import.meta.url));
    const fixture = JSON.parse(readFileSync(new URL("../../contracts/revalidation/v1/fixtures/signature.json", import.meta.url), "utf8")) as {
      secret: string; timestamp: string; eventKey: string; signature: string;
    };
    const signature = createHmac("sha256", fixture.secret)
      .update(`${fixture.timestamp}\n${fixture.eventKey}\n`, "ascii").update(body).digest("hex");
    expect(signature).toBe(fixture.signature);
    const headers = new Headers({
      "X-Revalidation-Timestamp": fixture.timestamp,
      "X-Revalidation-Event-Key": fixture.eventKey,
      "X-Revalidation-Signature": fixture.signature,
    });
    expect(verifyRevalidationEvent(body, headers, fixture.secret, Number(fixture.timestamp))?.postId).toBe(42);
  });
  it("accepts the canonical raw-byte signature and old/new public paths", () => {
    const { body, headers } = signed();
    expect(verifyRevalidationEvent(body, headers, secret, now)).toEqual(validEvent);
  });

  it("rejects a changed body, wrong key, and header/body event mismatch", () => {
    const { body, headers } = signed();
    expect(verifyRevalidationEvent(Buffer.from(body.toString().replace("new-post", "bad-post")), headers, secret, now)).toBeNull();
    expect(verifyRevalidationEvent(body, headers, "different-test-secret-0123456789abcdef", now)).toBeNull();
    const mismatch = signed(validEvent, now, "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    expect(verifyRevalidationEvent(mismatch.body, mismatch.headers, secret, now)).toBeNull();
  });

  it("enforces the past and future timestamp window", () => {
    for (const timestamp of [now - 301, now + 31]) {
      const { body, headers } = signed(validEvent, timestamp);
      expect(verifyRevalidationEvent(body, headers, secret, now)).toBeNull();
    }
    for (const timestamp of [now - 300, now + 30]) {
      const { body, headers } = signed(validEvent, timestamp);
      expect(verifyRevalidationEvent(body, headers, secret, now)).not.toBeNull();
    }
  });

  it("rejects admin paths, unknown tags, and oversized or malformed payloads", () => {
    for (const change of [{ paths: ["/admin"] }, { paths: ["/posts/a?draft=1"] }, { tags: ["admin"] }, { paths: [] }]) {
      const { body, headers } = signed({ ...validEvent, ...change });
      expect(verifyRevalidationEvent(body, headers, secret, now)).toBeNull();
    }
    const { body, headers } = signed();
    expect(verifyRevalidationEvent(Buffer.alloc(revalidationBodyLimit + 1), headers, secret, now)).toBeNull();
    expect(verifyRevalidationEvent(body, headers, "replace-with-production-revalidation-secret", now)).toBeNull();
    expect(verifyRevalidationEvent(body, headers, "dev-revalidation-shared-secret-placeholder", now)).toBeNull();
  });
});
