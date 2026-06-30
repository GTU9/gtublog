import { createHash, randomUUID } from "node:crypto";

import type { GenerationSubmitRequest } from "./runtime.js";

export function terminalSubmissionId(): string {
  return randomUUID();
}

export function canonicalTerminalPayload(request: Omit<GenerationSubmitRequest, "payloadDigest">): string {
  const draft = request.draft
    ? {
        title: normalize(request.draft.title),
        excerpt: normalize(request.draft.excerpt),
        contentMarkdown: normalize(request.draft.contentMarkdown),
        citationSnapshotIds: [...request.draft.citationSnapshotIds].sort((a, b) => a - b),
      }
    : null;
  return JSON.stringify({
    terminalSubmissionId: request.terminalSubmissionId,
    workerId: request.workerId,
    providerName: request.providerName,
    promptVersion: request.promptVersion,
    schemaVersion: request.schemaVersion,
    draft,
    failureReason: request.failureReason ? normalize(request.failureReason) : null,
  });
}

export function terminalPayloadDigest(request: Omit<GenerationSubmitRequest, "payloadDigest">): string {
  return createHash("sha256").update(canonicalTerminalPayload(request), "utf8").digest("hex");
}

function normalize(value: string): string {
  return value.normalize("NFC").replace(/\r\n?/gu, "\n");
}
