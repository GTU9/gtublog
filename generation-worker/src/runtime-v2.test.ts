import { describe, expect, it, vi } from "vitest";

import {
  BackendOperationError,
  CONTRACT_SCHEMA_VERSION,
  assertClaimResponse,
  assertHeartbeatResponse,
  createBackendGenerationClient,
  createWorkerRuntime,
  GenerationTimeoutError,
  type BackendGenerationClient,
  type GenerationClaimResponse,
  type GenerationSubmitRequest,
} from "./runtime.js";
import type { GenerationProvider } from "./provider.js";
import { terminalPayloadDigest } from "./terminal.js";
import { createWorkerHealthState } from "./health.js";

function claimResponse(): GenerationClaimResponse {
  return {
    jobId: 13,
    jobKey: "job-13",
    runId: 130,
    topicId: 131,
    leaseOwner: "worker-a",
    leaseExpiresAt: "2026-06-30T12:10:00Z",
    providerName: "codex-sdk",
    promptVersion: "prompt-v1",
    schemaVersion: CONTRACT_SCHEMA_VERSION,
    prompt: "Create a cited draft.",
    snapshots: [
      {
        snapshotId: 1,
        sourceUrl: "https://example.test/source",
        canonicalUrl: "https://example.test/source",
        title: "Source",
        originHost: "example.test",
        bodyExcerpt: "Evidence",
        contentHash: "a".repeat(64),
        retrievedAt: "2026-06-30T12:00:00Z",
      },
    ],
  };
}

function successfulProvider(): GenerationProvider {
  return {
    name: "codex-sdk",
    generate: vi.fn().mockResolvedValue({
      title: "Draft",
      excerpt: "Summary",
      contentMarkdown: "# Draft",
      citationSnapshotIds: [1],
      provider: "codex-sdk",
    }),
  };
}

function clientWithSubmit(submit: BackendGenerationClient["submit"]): BackendGenerationClient {
  return {
    claim: vi.fn().mockResolvedValue(claimResponse()),
    heartbeat: vi.fn().mockResolvedValue({
      jobId: 13,
      status: "CLAIMED",
      serverTime: "2026-06-30T12:00:30Z",
      leaseExpiresAt: "2026-06-30T12:10:00Z",
    }),
    submit,
  };
}

describe("v2 backend client error policy", () => {
  it.each([
    ["claim", 400, "fatal"],
    ["claim", 401, "fatal"],
    ["heartbeat", 404, "lease-lost"],
    ["heartbeat", 409, "lease-lost"],
    ["submit", 409, "conflict"],
    ["submit", 429, "transient"],
    ["submit", 503, "transient"],
  ] as const)("classifies %s status %i as %s", async (operation, status, kind) => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status }));
    const client = createBackendGenerationClient({
      baseUrl: "https://backend.example.test",
      token: "secret-worker-token",
      fetchImpl,
    });

    const action = operation === "claim"
      ? client.claim({ workerId: "worker-a", supportedProviders: ["codex-sdk"], supportedSchemaVersions: [CONTRACT_SCHEMA_VERSION] })
      : operation === "heartbeat"
        ? client.heartbeat(13, { workerId: "worker-a" })
        : client.submit(13, terminalRequest());

    await expect(action).rejects.toMatchObject({
      name: "BackendOperationError",
      operation,
      status,
      kind,
    });
    await expect(action).rejects.not.toThrow("secret-worker-token");
  });

  it("classifies a request timeout as a secret-free transient transport failure", async () => {
    const fetchImpl = vi.fn((_input: string | URL | Request, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => reject(new Error("request aborted")), { once: true });
      })) as unknown as typeof fetch;
    const client = createBackendGenerationClient({
      baseUrl: "https://backend.example.test",
      token: "secret-worker-token",
      requestTimeoutMs: 5,
      fetchImpl,
    });

    const result = client.claim({
      workerId: "worker-a",
      supportedProviders: ["codex-sdk"],
      supportedSchemaVersions: [CONTRACT_SCHEMA_VERSION],
    });

    await expect(result).rejects.toEqual(
      expect.objectContaining({ operation: "claim", status: null, kind: "transient" }),
    );
    await expect(result).rejects.not.toThrow("secret-worker-token");
  });

  it("records every successful backend response, including no-job 204", async () => {
    const contacts: Array<{ operation: string; status: number; at: string }> = [];
    const client = createBackendGenerationClient({
      baseUrl: "https://backend.example.test",
      token: "worker-token",
      fetchImpl: vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
      onBackendContact: (contact) => contacts.push(contact),
    });

    await expect(client.claim({
      workerId: "worker-a",
      supportedProviders: ["codex-sdk"],
      supportedSchemaVersions: [CONTRACT_SCHEMA_VERSION],
    })).resolves.toBeNull();

    expect(contacts).toHaveLength(1);
    expect(contacts[0]).toMatchObject({ operation: "claim", status: 204 });
    expect(() => new Date(contacts[0].at).toISOString()).not.toThrow();
  });

  it("updates health on a periodic heartbeat while generation is still running", async () => {
    const health = createWorkerHealthState(new Date("2026-06-30T00:00:00Z"));
    const contacts: Array<{ operation: string; status: number; at: string }> = [];
    let heartbeatCount = 0;
    const fetchImpl = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      if (url.endsWith("/claim")) {
        return Promise.resolve(Response.json(claimResponse()));
      }
      if (url.endsWith("/heartbeat")) {
        heartbeatCount += 1;
        return Promise.resolve(Response.json({
          jobId: 13,
          status: "CLAIMED",
          serverTime: "2026-06-30T12:00:30Z",
          leaseExpiresAt: "2099-06-30T12:10:00Z",
        }));
      }
      if (typeof init?.body !== "string") throw new Error("Expected a JSON request body.");
      const submitted = JSON.parse(init.body) as GenerationSubmitRequest;
      return Promise.resolve(Response.json(submitResponse(submitted), { status: 202 }));
    }) as unknown as typeof fetch;
    let timestampChangedDuringGeneration = false;
    const provider: GenerationProvider = {
      name: "codex-sdk",
      async generate() {
        const before = health.snapshot().lastBackendContactAt;
        await new Promise((resolve) => setTimeout(resolve, 15));
        timestampChangedDuringGeneration = health.snapshot().lastBackendContactAt !== before;
        return {
          title: "Draft",
          excerpt: "Summary",
          contentMarkdown: "Body",
          citationSnapshotIds: [1],
          provider: "codex-sdk",
        };
      },
    };
    const client = createBackendGenerationClient({
      baseUrl: "https://backend.example.test",
      token: "worker-token",
      fetchImpl,
      onBackendContact(contact) {
        contacts.push(contact);
        health.recordBackendContact(new Date(contact.at));
      },
    });

    await createWorkerRuntime({
      client,
      provider,
      heartbeatIntervalMs: 2,
      leaseSafetyMarginMs: 1,
    }).runOnce("worker-a");

    expect(heartbeatCount).toBeGreaterThan(1);
    expect(timestampChangedDuringGeneration).toBe(true);
    expect(contacts.map((contact) => contact.operation)).toEqual(
      expect.arrayContaining(["claim", "heartbeat", "submit"]),
    );
  });
});

describe("v2 runtime terminal safety", () => {
  it("retries a response-lost success with the exact same terminal payload", async () => {
    const submissions: GenerationSubmitRequest[] = [];
    const submit = vi.fn((_jobId: number, request: GenerationSubmitRequest) => {
      submissions.push(request);
      if (submissions.length === 1) {
        return Promise.reject(new BackendOperationError("submit", null, "transient", "response lost"));
      }
      return Promise.resolve(submitResponse(request));
    });
    const runtime = createWorkerRuntime({
      client: clientWithSubmit(submit),
      provider: successfulProvider(),
      heartbeatIntervalMs: 60_000,
      shutdownGraceMs: 1_000,
    });

    await expect(runtime.runOnce("worker-a")).resolves.toMatchObject({ jobId: 13 });

    expect(submissions).toHaveLength(2);
    expect(submissions[1]).toEqual(submissions[0]);
    expect(submissions.every((request) => request.draft !== undefined)).toBe(true);
    expect(submissions.every((request) => request.failureReason === undefined)).toBe(true);
  });

  it("never changes an ambiguous successful terminal into a failure terminal", async () => {
    const submissions: GenerationSubmitRequest[] = [];
    const submit = vi.fn((_jobId: number, request: GenerationSubmitRequest) => {
      submissions.push(request);
      return Promise.reject(new BackendOperationError("submit", null, "transient", "response lost"));
    });
    const runtime = createWorkerRuntime({
      client: clientWithSubmit(submit),
      provider: successfulProvider(),
      heartbeatIntervalMs: 60_000,
      shutdownGraceMs: 1,
    });

    await expect(runtime.runOnce("worker-a")).rejects.toMatchObject({ kind: "transient" });

    expect(submissions.length).toBeGreaterThan(0);
    expect(submissions.every((request) => request.draft !== undefined)).toBe(true);
    expect(submissions.every((request) => request.failureReason === undefined)).toBe(true);
    expect(new Set(submissions.map((request) => JSON.stringify(request))).size).toBe(1);
  });

  it("submits only one bounded failure terminal when the provider fails", async () => {
    const submit = vi.fn((_jobId: number, request: GenerationSubmitRequest) => Promise.resolve(submitResponse(request)));
    const provider: GenerationProvider = {
      name: "codex-sdk",
      generate: vi.fn().mockRejectedValue(new Error("sensitive provider detail")),
    };
    const runtime = createWorkerRuntime({
      client: clientWithSubmit(submit),
      provider,
      heartbeatIntervalMs: 60_000,
    });

    await expect(runtime.runOnce("worker-a")).rejects.toThrow("sensitive provider detail");

    expect(submit).toHaveBeenCalledOnce();
    const request = submit.mock.calls[0]?.[1];
    expect(request?.draft).toBeUndefined();
    expect(request?.failureReason).toBe("Error");
    expect(request?.failureReason).not.toContain("sensitive provider detail");
  });

  it("does not attempt a failure terminal after a success digest conflict", async () => {
    const submit = vi.fn().mockRejectedValue(
      new BackendOperationError("submit", 409, "conflict", "terminal conflict"),
    );
    const runtime = createWorkerRuntime({
      client: clientWithSubmit(submit),
      provider: successfulProvider(),
      heartbeatIntervalMs: 60_000,
    });

    await expect(runtime.runOnce("worker-a")).rejects.toMatchObject({ kind: "conflict" });
    expect(submit).toHaveBeenCalledOnce();
    const firstSubmission = submit.mock.calls[0]?.[1] as GenerationSubmitRequest | undefined;
    expect(firstSubmission?.draft).toBeDefined();
  });

  it("clears generation and heartbeat timers after a failed run", async () => {
    vi.useFakeTimers();
    try {
      const submit = vi.fn((_jobId: number, request: GenerationSubmitRequest) => Promise.resolve(submitResponse(request)));
      const runtime = createWorkerRuntime({
        client: clientWithSubmit(submit),
        provider: {
          name: "codex-sdk",
          generate: vi.fn().mockRejectedValue(new Error("provider failed")),
        },
        heartbeatIntervalMs: 30_000,
        generationTimeoutMs: 240_000,
      });

      await expect(runtime.runOnce("worker-a")).rejects.toThrow("provider failed");
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it("cleans up heartbeat and timeout timers when generated citations escape the claim", async () => {
    vi.useFakeTimers();
    try {
      const submit = vi.fn();
      const runtime = createWorkerRuntime({
        client: clientWithSubmit(submit),
        provider: { ...successfulProvider(), generate: vi.fn().mockResolvedValue({
          title: "Draft", excerpt: "Summary", contentMarkdown: "Body", citationSnapshotIds: [999], provider: "codex-sdk",
        }) },
        heartbeatIntervalMs: 30_000,
      });
      await expect(runtime.runOnce("worker-a")).rejects.toMatchObject({ kind: "fatal" });
      expect(submit).not.toHaveBeenCalled();
      expect(vi.getTimerCount()).toBe(0);
    } finally { vi.useRealTimers(); }
  });

  it("does not submit a terminal after heartbeat transport uncertainty aborts the provider", async () => {
    const submit = vi.fn();
    let heartbeatCount = 0;
    const client = clientWithSubmit(submit);
    client.heartbeat = vi.fn().mockImplementation(() => {
      heartbeatCount += 1;
      if (heartbeatCount === 1) return Promise.resolve({
        jobId: 13,
        status: "CLAIMED",
        serverTime: "2026-06-30T12:00:30Z",
        leaseExpiresAt: "2026-06-30T12:10:00Z",
      });
      return Promise.reject(new BackendOperationError("heartbeat", null, "transient", "heartbeat transport failed"));
    });
    const provider: GenerationProvider = {
      name: "codex-sdk",
      generate: vi.fn((_request, signal?: AbortSignal): Promise<never> => new Promise((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("wrapped", "AbortError")), { once: true });
      })),
    };
    const runtime = createWorkerRuntime({ client, provider, heartbeatIntervalMs: 1 });

    await expect(runtime.runOnce("worker-a")).rejects.toMatchObject({ operation: "heartbeat", kind: "transient" });
    expect(submit).not.toHaveBeenCalled();
  });

  it("restores the generation timeout when the provider wraps abort as AbortError", async () => {
    const submit = vi.fn((_jobId: number, request: GenerationSubmitRequest) => Promise.resolve(submitResponse(request)));
    const provider: GenerationProvider = {
      name: "codex-sdk",
      generate: vi.fn((_request, signal?: AbortSignal): Promise<never> => new Promise((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("wrapped", "AbortError")), { once: true });
      })),
    };
    const runtime = createWorkerRuntime({
      client: clientWithSubmit(submit),
      provider,
      heartbeatIntervalMs: 60_000,
      generationTimeoutMs: 5,
    });

    await expect(runtime.runOnce("worker-a")).rejects.toBeInstanceOf(GenerationTimeoutError);
    expect(submit).toHaveBeenCalledOnce();
    expect(submit.mock.calls[0]?.[1].failureReason).toBe("GenerationTimeoutError");
  });
});

describe("v2 deep contract validation", () => {
  it("rejects malformed nested snapshots before provider execution", () => {
    expect(() => assertClaimResponse({
      ...claimResponse(),
      snapshots: [{ snapshotId: "not-a-number" }],
    })).toThrow("Invalid generation claim payload");
  });

  it("rejects offset-free heartbeat timestamps", () => {
    expect(() => assertHeartbeatResponse({
      jobId: 13,
      status: "CLAIMED",
      serverTime: "2026-06-30T12:00:00",
      leaseExpiresAt: "2026-06-30T12:10:00Z",
    })).toThrow("Invalid heartbeat response payload");
  });
});

function terminalRequest(): GenerationSubmitRequest {
  const request = {
    terminalSubmissionId: "11111111-1111-4111-8111-111111111111",
    workerId: "worker-a",
    providerName: "codex-sdk",
    promptVersion: "prompt-v1",
    schemaVersion: CONTRACT_SCHEMA_VERSION,
    failureReason: "Error",
  } as const;
  return { ...request, payloadDigest: terminalPayloadDigest(request) };
}

function submitResponse(request: GenerationSubmitRequest) {
  return {
    jobId: 13,
    status: request.draft ? "SUBMITTED" : "FAILED",
    submittedAt: "2026-06-30T12:01:00Z",
    terminalSubmissionId: request.terminalSubmissionId,
    payloadDigest: request.payloadDigest,
  };
}
