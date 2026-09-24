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
        ...(request.schemaVersion === "automation-job-v3" ? { taxonomy: request.draft.taxonomy ? {
          categoryId: request.draft.taxonomy.categoryId,
          tagIds: [...request.draft.taxonomy.tagIds].sort((a, b) => a - b),
        } : null } : {}),
      }
    : null;
  const observations = request.schemaVersion === "automation-job-v4" && request.observations
    ? request.observations.map((observation) => ({
        kind: observation.kind,
        literal: normalizeObservationLiteral(observation.literal),
        citationSnapshotIds: [...observation.citationSnapshotIds].sort((a, b) => a - b),
      }))
    : null;
  const taxonomy = request.schemaVersion === "automation-job-v4" && request.taxonomy
    ? {
        categoryId: request.taxonomy.categoryId,
        tagIds: [...request.taxonomy.tagIds].sort((a, b) => a - b),
      }
    : null;
  const failureReason = request.failureReason ? normalize(request.failureReason) : null;
  if (request.schemaVersion === "automation-job-v4") {
    return JSON.stringify({
      terminalSubmissionId: request.terminalSubmissionId,
      workerId: request.workerId,
      providerName: request.providerName,
      promptVersion: request.promptVersion,
      schemaVersion: request.schemaVersion,
      draft,
      observations,
      taxonomy,
      failureReason,
    });
  }
  return JSON.stringify({
    terminalSubmissionId: request.terminalSubmissionId,
    workerId: request.workerId,
    providerName: request.providerName,
    promptVersion: request.promptVersion,
    schemaVersion: request.schemaVersion,
    draft,
    failureReason,
  });
}

export function terminalPayloadDigest(request: Omit<GenerationSubmitRequest, "payloadDigest">): string {
  return createHash("sha256").update(canonicalTerminalPayload(request), "utf8").digest("hex");
}

function normalize(value: string): string {
  return value.normalize("NFC").replace(/\r\n?/gu, "\n");
}

function normalizeObservationLiteral(value: string): string {
  return value.normalize("NFC").replace(/[\p{White_Space}]+/gu, " ").trim();
}
