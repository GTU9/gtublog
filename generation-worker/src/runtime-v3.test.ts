import { describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import path from "node:path";

import { validateGenerationDraft } from "./draft-schema.js";
import { createFakeGenerationProvider } from "./fake-provider.js";
import type { GenerationProvider } from "./provider.js";
import {
  assertClaimResponse,
  assertSubmitRequest,
  CONTRACT_SCHEMA_VERSION_V3,
  createWorkerRuntime,
  type BackendGenerationClient,
  type GenerationClaimResponse,
  type GenerationSubmitRequest,
} from "./runtime.js";
import { terminalPayloadDigest } from "./terminal.js";

const claim: GenerationClaimResponse = {
  jobId: 13, jobKey: "11111111-1111-4111-8111-111111111113", runId: 130, topicId: 131,
  leaseOwner: "worker-a", leaseExpiresAt: "2026-06-30T12:10:00Z", providerName: "fake-provider",
  promptVersion: "prompt-v1", schemaVersion: CONTRACT_SCHEMA_VERSION_V3, prompt: "Create a cited draft.",
  snapshots: [{ snapshotId: 1, sourceUrl: "https://example.test/source", canonicalUrl: "https://example.test/source",
    title: "Source", originHost: "example.test", bodyExcerpt: "Evidence", contentHash: "a".repeat(64),
    retrievedAt: "2026-06-30T12:00:00Z" }],
  taxonomyCatalog: { categories: [{ id: 10, slug: "technology", name: "Technology" }],
    tags: [{ id: 20, slug: "automation", name: "Automation" }] },
};

describe("v3 taxonomy generation contract", () => {
  it("accepts the versioned claim and terminal fixtures with matching digest", () => {
    const fixtures = path.resolve(process.cwd(), "..", "contracts", "automation", "v3", "fixtures");
    const claimFixture = JSON.parse(readFileSync(path.join(fixtures, "claim-response.json"), "utf8")) as unknown;
    const submitFixture = JSON.parse(readFileSync(path.join(fixtures, "submit-request.json"), "utf8")) as unknown;
    const failureFixture = JSON.parse(readFileSync(path.join(fixtures, "failure-submit-request.json"), "utf8")) as unknown;
    expect(() => assertClaimResponse(claimFixture)).not.toThrow();
    expect(() => assertSubmitRequest(submitFixture)).not.toThrow();
    expect(() => assertSubmitRequest(failureFixture)).not.toThrow();
  });
  it("rejects a missing or malformed claim catalog", () => {
    expect(() => assertClaimResponse(claim)).not.toThrow();
    expect(() => assertClaimResponse({ ...claim, taxonomyCatalog: undefined })).toThrow();
    expect(() => assertClaimResponse({ ...claim, taxonomyCatalog: { categories: [], tags: [] } })).toThrow();
  });

  it("preserves policy-invalid but structurally valid taxonomy for Spring to hold", () => {
    const result = validateGenerationDraft({ title: "Title", excerpt: "Excerpt", contentMarkdown: "# Body",
      citationSnapshotIds: [1], taxonomy: { categoryId: 999, tagIds: [20, 20] } }, "provider", CONTRACT_SCHEMA_VERSION_V3);
    expect(result.taxonomy).toEqual({ categoryId: 999, tagIds: [20, 20] });
    expect(() => validateGenerationDraft({ title: "Title", excerpt: "Excerpt", contentMarkdown: "# Body",
      citationSnapshotIds: [1], taxonomy: { categoryId: "bad", tagIds: [20] } }, "provider", CONTRACT_SCHEMA_VERSION_V3)).toThrow();
  });

  it("submits the selected taxonomy and binds it to the terminal digest", async () => {
    const submitted: GenerationSubmitRequest[] = [];
    const client: BackendGenerationClient = {
      claim: vi.fn().mockResolvedValue(claim),
      heartbeat: vi.fn().mockResolvedValue({ jobId: 13, status: "CLAIMED", serverTime: "2026-06-30T12:00:30Z", leaseExpiresAt: "2026-06-30T12:10:00Z" }),
      submit: vi.fn().mockImplementation((_jobId: number, request: GenerationSubmitRequest) => {
        submitted.push(request);
        return Promise.resolve({ jobId: 13, status: "PUBLISHED", submittedAt: "2026-06-30T12:01:00Z",
          terminalSubmissionId: request.terminalSubmissionId, payloadDigest: request.payloadDigest });
      }),
    };
    await createWorkerRuntime({ client, provider: createFakeGenerationProvider(), heartbeatIntervalMs: 60_000 }).runOnce("worker-a");
    const terminal = submitted[0];
    expect(terminal.draft?.taxonomy).toEqual({ categoryId: 10, tagIds: [20] });
    expect(() => assertSubmitRequest(terminal)).not.toThrow();
    expect(terminalPayloadDigest({ ...terminal, draft: { ...terminal.draft!, taxonomy: { categoryId: 11, tagIds: [20] } } }))
      .not.toBe(terminal.payloadDigest);
  });

  it("submits structural provider failure as FAILED terminal", async () => {
    const submitted: GenerationSubmitRequest[] = [];
    const client: BackendGenerationClient = {
      claim: vi.fn().mockResolvedValue(claim),
      heartbeat: vi.fn().mockResolvedValue({ jobId: 13, status: "CLAIMED", serverTime: "2026-06-30T12:00:30Z", leaseExpiresAt: "2026-06-30T12:10:00Z" }),
      submit: vi.fn().mockImplementation((_jobId: number, request: GenerationSubmitRequest) => {
        submitted.push(request);
        return Promise.resolve({ jobId: 13, status: "FAILED", submittedAt: "2026-06-30T12:01:00Z",
          terminalSubmissionId: request.terminalSubmissionId, payloadDigest: request.payloadDigest });
      }),
    };
    const provider: GenerationProvider = { name: "fake-provider", generate: vi.fn().mockResolvedValue({
      title: "Title", excerpt: "Excerpt", contentMarkdown: "# Body", citationSnapshotIds: [1], provider: "fake-provider",
    }) };
    await expect(createWorkerRuntime({ client, provider, heartbeatIntervalMs: 60_000 }).runOnce("worker-a"))
      .rejects.toThrow("invalid taxonomy fields");
    expect(submitted[0]?.failureReason).toBe("Generated draft has invalid taxonomy fields.");
    expect(submitted[0]?.draft).toBeUndefined();
  });
});
