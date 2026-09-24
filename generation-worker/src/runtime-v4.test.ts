import { readFileSync } from "node:fs";
import path from "node:path";

import { describe, expect, it, vi } from "vitest";

import { validateGenerationDraft } from "./draft-schema.js";
import { createFakeGenerationProvider } from "./fake-provider.js";
import {
  assertClaimResponse,
  assertSubmitRequest,
  CONTRACT_SCHEMA_VERSION_V4,
  createWorkerRuntime,
  type BackendGenerationClient,
  type GenerationClaimResponse,
  type GenerationSubmitRequest,
} from "./runtime.js";
import { canonicalTerminalPayload, terminalPayloadDigest } from "./terminal.js";

const claim: GenerationClaimResponse = {
  jobId: 14, jobKey: "11111111-1111-4111-8111-111111111114", runId: 140, topicId: 141,
  leaseOwner: "worker-a", leaseExpiresAt: "2026-07-01T12:10:00Z", providerName: "fake-provider",
  promptVersion: "prompt-v1", schemaVersion: CONTRACT_SCHEMA_VERSION_V4, prompt: "Create source observations.",
  snapshots: [
    { snapshotId: 1, sourceUrl: "https://alpha.example.test/source", canonicalUrl: "https://alpha.example.test/source",
      title: "Alpha", originHost: "alpha.example.test", bodyExcerpt: "Structured evidence mention shared by two source snapshots.",
      contentHash: "a".repeat(64), retrievedAt: "2026-07-01T12:00:00Z" },
    { snapshotId: 2, sourceUrl: "https://beta.example.test/source", canonicalUrl: "https://beta.example.test/source",
      title: "Beta", originHost: "beta.example.test", bodyExcerpt: "Structured evidence mention shared by two source snapshots.",
      contentHash: "b".repeat(64), retrievedAt: "2026-07-01T12:00:30Z" },
  ],
  taxonomyCatalog: { categories: [{ id: 10, slug: "technology", name: "Technology" }],
    tags: [{ id: 20, slug: "automation", name: "Automation" }] },
};

describe("v4 source observation generation contract", () => {
  it("accepts the versioned fixtures with matching terminal digests", () => {
    const fixtures = path.resolve(process.cwd(), "..", "contracts", "automation", "v4", "fixtures");
    const claimFixture = JSON.parse(readFileSync(path.join(fixtures, "claim-response.json"), "utf8")) as unknown;
    const submitFixture = JSON.parse(readFileSync(path.join(fixtures, "submit-request.json"), "utf8")) as GenerationSubmitRequest;
    const failureFixture = JSON.parse(readFileSync(path.join(fixtures, "failure-submit-request.json"), "utf8")) as GenerationSubmitRequest;
    expect(() => assertClaimResponse(claimFixture)).not.toThrow();
    expect(() => assertSubmitRequest(submitFixture)).not.toThrow();
    expect(() => assertSubmitRequest(failureFixture)).not.toThrow();
    expect(terminalPayloadDigest(submitFixture)).toBe(submitFixture.payloadDigest);
    expect(terminalPayloadDigest(failureFixture)).toBe(failureFixture.payloadDigest);
  });

  it("normalizes literal text and sorts citation ids inside each observation digest", () => {
    const decomposed = {
      terminalSubmissionId: "44444444-4444-4444-8444-444444444444",
      workerId: "worker-fixture",
      providerName: "codex-sdk",
      promptVersion: "prompt-v1",
      schemaVersion: CONTRACT_SCHEMA_VERSION_V4,
      observations: [{
        kind: "SOURCE_MENTION" as const,
        literal: "Cafe\u0301 observation\r\nshared by two source snapshots.",
        citationSnapshotIds: [2, 1] as const,
      }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    };
    const normalized = {
      ...decomposed,
      observations: [{
        kind: "SOURCE_MENTION" as const,
        literal: "Caf\u00e9 observation\nshared by two source snapshots.",
        citationSnapshotIds: [1, 2] as const,
      }],
    };
    expect(canonicalTerminalPayload(decomposed)).toContain("\"citationSnapshotIds\":[1,2]");
    expect(terminalPayloadDigest(decomposed)).toBe(terminalPayloadDigest(normalized));
  });

  it("rejects free-form draft fields for v4 success submissions", () => {
    const request = {
      terminalSubmissionId: "44444444-4444-4444-8444-444444444444",
      workerId: "worker-fixture",
      providerName: "codex-sdk",
      promptVersion: "prompt-v1",
      schemaVersion: CONTRACT_SCHEMA_VERSION_V4,
      draft: { title: "Title", excerpt: "Excerpt", contentMarkdown: "# Body", citationSnapshotIds: [1] },
    };
    expect(() => assertSubmitRequest({ ...request, payloadDigest: terminalPayloadDigest(request) })).toThrow();
  });

  it("validates source mention observations and rejects prose-generation fields", () => {
    const result = validateGenerationDraft({
      observations: [{
        kind: "SOURCE_MENTION",
        literal: "Cafe\u0301 observation shared by two source snapshots.",
        citationSnapshotIds: [2, 1],
      }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    }, "provider", CONTRACT_SCHEMA_VERSION_V4);
    expect(result).toEqual({
      observations: [{
        kind: "SOURCE_MENTION",
        literal: "Caf\u00e9 observation shared by two source snapshots.",
        citationSnapshotIds: [1, 2],
      }],
      taxonomy: { categoryId: 10, tagIds: [20] },
      provider: "provider",
    });
    expect(() => validateGenerationDraft({
      title: "Title", excerpt: "Excerpt", contentMarkdown: "# Body", citationSnapshotIds: [1, 2],
      observations: [{ kind: "SOURCE_MENTION", literal: "Cafe observation shared by two source snapshots.", citationSnapshotIds: [1, 2] }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    }, "provider", CONTRACT_SCHEMA_VERSION_V4)).toThrow("invalid structured draft");
    expect(() => validateGenerationDraft({
      observations: [{ kind: "CLAIM", literal: "Cafe observation shared by two source snapshots.", citationSnapshotIds: [1, 2] }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    }, "provider", CONTRACT_SCHEMA_VERSION_V4)).toThrow("invalid structured draft");
    expect(() => validateGenerationDraft({
      observations: [{ kind: "SOURCE_MENTION", literal: "Cafe observation shared by two source snapshots.", citationSnapshotIds: [1, 2], title: "extra" }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    }, "provider", CONTRACT_SCHEMA_VERSION_V4)).toThrow("invalid structured draft");
    expect(() => validateGenerationDraft({
      observations: [{ kind: "SOURCE_MENTION", literal: "Cafe\u200bobservation shared by two source snapshots.", citationSnapshotIds: [1, 2] }],
      taxonomy: { categoryId: 10, tagIds: [20] },
    }, "provider", CONTRACT_SCHEMA_VERSION_V4)).toThrow("invalid structured draft");
  });

  it("submits observations and top-level taxonomy without a draft", async () => {
    const submitted: GenerationSubmitRequest[] = [];
    const client: BackendGenerationClient = {
      claim: vi.fn().mockResolvedValue(claim),
      heartbeat: vi.fn().mockResolvedValue({ jobId: 14, status: "CLAIMED", serverTime: "2026-07-01T12:00:30Z", leaseExpiresAt: "2026-07-01T12:10:00Z" }),
      submit: vi.fn().mockImplementation((_jobId: number, request: GenerationSubmitRequest) => {
        submitted.push(request);
        return Promise.resolve({ jobId: 14, status: "HELD", submittedAt: "2026-07-01T12:01:00Z",
          terminalSubmissionId: request.terminalSubmissionId, payloadDigest: request.payloadDigest });
      }),
    };
    await createWorkerRuntime({ client, provider: createFakeGenerationProvider(), heartbeatIntervalMs: 60_000 }).runOnce("worker-a");
    const terminal = submitted[0];
    expect(terminal?.draft).toBeUndefined();
    expect(terminal?.observations).toEqual([{ kind: "SOURCE_MENTION", literal: "Structured evidence mention shared by two source snapshots.", citationSnapshotIds: [1, 2] }]);
    expect(terminal?.taxonomy).toEqual({ categoryId: 10, tagIds: [20] });
    expect(() => assertSubmitRequest(terminal)).not.toThrow();
  });

  it("rejects observation citations outside the claimed snapshot set before submitting", async () => {
    const submit = vi.fn();
    const client: BackendGenerationClient = {
      claim: vi.fn().mockResolvedValue(claim),
      heartbeat: vi.fn().mockResolvedValue({ jobId: 14, status: "CLAIMED", serverTime: "2026-07-01T12:00:30Z", leaseExpiresAt: "2026-07-01T12:10:00Z" }),
      submit,
    };
    await expect(createWorkerRuntime({
      client,
      provider: {
        name: "fake-provider",
        generate: vi.fn().mockResolvedValue({
          observations: [{ kind: "SOURCE_MENTION", literal: "Structured evidence mention shared by two source snapshots.", citationSnapshotIds: [1, 999] }],
          taxonomy: { categoryId: 10, tagIds: [20] },
          provider: "fake-provider",
        }),
      },
      heartbeatIntervalMs: 60_000,
    }).runOnce("worker-a")).rejects.toMatchObject({ kind: "fatal" });
    expect(submit).not.toHaveBeenCalled();
  });
});
