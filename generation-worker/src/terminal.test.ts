import { describe, expect, it } from "vitest";

import {
  canonicalTerminalPayload,
  terminalPayloadDigest,
} from "./terminal.js";
import type { GenerationSubmitRequest } from "./runtime.js";

function successRequest(
  overrides: Partial<Omit<GenerationSubmitRequest, "payloadDigest">> = {},
): Omit<GenerationSubmitRequest, "payloadDigest"> {
  return {
    terminalSubmissionId: "11111111-1111-4111-8111-111111111111",
    workerId: "worker-a",
    providerName: "codex-sdk",
    promptVersion: "prompt-v1",
    schemaVersion: "automation-job-v2",
    draft: {
      title: "Cafe\u0301\r\nTitle",
      excerpt: "summary\rtext",
      contentMarkdown: "# Body\r\n\r\nContent",
      citationSnapshotIds: [20, 3, 10],
    },
    ...overrides,
  };
}

describe("terminal submission identity", () => {
  it("canonicalizes Unicode, line endings, null alternatives, and citation order", () => {
    const request = successRequest();

    expect(canonicalTerminalPayload(request)).toBe(
      JSON.stringify({
        terminalSubmissionId: "11111111-1111-4111-8111-111111111111",
        workerId: "worker-a",
        providerName: "codex-sdk",
        promptVersion: "prompt-v1",
        schemaVersion: "automation-job-v2",
        draft: {
          title: "Caf\u00e9\nTitle",
          excerpt: "summary\ntext",
          contentMarkdown: "# Body\n\nContent",
          citationSnapshotIds: [3, 10, 20],
        },
        failureReason: null,
      }),
    );
  });

  it("produces the same digest for semantically equivalent normalized payloads", () => {
    const decomposed = successRequest();
    const normalized = successRequest({
      draft: {
        title: "Caf\u00e9\nTitle",
        excerpt: "summary\ntext",
        contentMarkdown: "# Body\n\nContent",
        citationSnapshotIds: [3, 10, 20],
      },
    });

    expect(terminalPayloadDigest(decomposed)).toBe(terminalPayloadDigest(normalized));
    expect(terminalPayloadDigest(decomposed)).toMatch(/^[a-f0-9]{64}$/u);
  });

  it("changes the digest when terminal identity or canonical content changes", () => {
    const baseline = successRequest();

    expect(
      terminalPayloadDigest(
        successRequest({ terminalSubmissionId: "22222222-2222-4222-8222-222222222222" }),
      ),
    ).not.toBe(terminalPayloadDigest(baseline));
    expect(
      terminalPayloadDigest(
        successRequest({
          draft: {
            ...baseline.draft!,
            contentMarkdown: "different body",
          },
        }),
      ),
    ).not.toBe(terminalPayloadDigest(baseline));
  });

  it("canonicalizes a failure terminal without creating a draft", () => {
    const request = successRequest({
      draft: undefined,
      failureReason: "provider\r\ntimeout",
    });

    expect(JSON.parse(canonicalTerminalPayload(request))).toMatchObject({
      draft: null,
      failureReason: "provider\ntimeout",
    });
  });

  it("canonicalizes v4 observations without changing v2/v3 payload shape", () => {
    const request = {
      terminalSubmissionId: "44444444-4444-4444-8444-444444444444",
      workerId: "worker-a",
      providerName: "codex-sdk",
      promptVersion: "prompt-v1",
      schemaVersion: "automation-job-v4",
      observations: [{
        kind: "SOURCE_MENTION" as const,
        literal: "Cafe\u0301 observation\r\nshared by two source snapshots.",
        citationSnapshotIds: [2, 1] as const,
      }],
      taxonomy: { categoryId: 10, tagIds: [30, 20] },
    };

    expect(canonicalTerminalPayload(request)).toBe(JSON.stringify({
      terminalSubmissionId: "44444444-4444-4444-8444-444444444444",
      workerId: "worker-a",
      providerName: "codex-sdk",
      promptVersion: "prompt-v1",
      schemaVersion: "automation-job-v4",
      draft: null,
      observations: [{
        kind: "SOURCE_MENTION",
        literal: "Caf\u00e9 observation shared by two source snapshots.",
        citationSnapshotIds: [1, 2],
      }],
      taxonomy: { categoryId: 10, tagIds: [20, 30] },
      failureReason: null,
    }));
  });
});
