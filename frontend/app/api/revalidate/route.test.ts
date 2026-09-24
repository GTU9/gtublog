import { createHmac } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";

const cache = vi.hoisted(() => ({ revalidatePath: vi.fn(), revalidateTag: vi.fn() }));
vi.mock("next/cache", () => cache);

import { POST } from "./route";

const secret = "test-only-revalidation-secret-0123456789abcdef";
const eventKey = "11111111-2222-4333-8444-555555555555";

function request(paths = ["/", "/posts/old", "/posts/new"], signatureOverride?: string) {
  const timestamp = String(Math.floor(Date.now() / 1000));
  const body = JSON.stringify({ version: 1, eventKey, postId: 42, slug: "new", tags: ["public-posts"], paths });
  const signature = createHmac("sha256", secret).update(`${timestamp}\n${eventKey}\n${body}`).digest("hex");
  return new Request("http://localhost/api/revalidate", {
    method: "POST", body,
    headers: {
      "X-Revalidation-Timestamp": timestamp,
      "X-Revalidation-Event-Key": eventKey,
      "X-Revalidation-Signature": signatureOverride ?? signature,
    },
  });
}

afterEach(() => { cache.revalidatePath.mockClear(); cache.revalidateTag.mockClear(); vi.unstubAllEnvs(); });

describe("POST /api/revalidate", () => {
  it("expires public data and each literal path, including the old slug", async () => {
    vi.stubEnv("GTUBLOG_REVALIDATION_SHARED_SECRET", secret);
    expect((await POST(request())).status).toBe(200);
    expect(cache.revalidateTag).toHaveBeenCalledWith("public-posts", { expire: 0 });
    expect(cache.revalidatePath.mock.calls.map(([path]) => path)).toEqual(["/", "/posts/old", "/posts/new"]);
  });

  it("does not touch cache for bad signatures or disallowed paths", async () => {
    vi.stubEnv("GTUBLOG_REVALIDATION_SHARED_SECRET", secret);
    expect((await POST(request(undefined, "0".repeat(64)))).status).toBe(401);
    expect((await POST(request(["/admin"]))).status).toBe(401);
    expect(cache.revalidateTag).not.toHaveBeenCalled();
    expect(cache.revalidatePath).not.toHaveBeenCalled();
  });

  it("fails closed without a configured secret", async () => {
    vi.stubEnv("GTUBLOG_REVALIDATION_SHARED_SECRET", "replace-with-production-revalidation-secret");
    expect((await POST(request())).status).toBe(503);
    expect(cache.revalidateTag).not.toHaveBeenCalled();
  });
});
