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
});
