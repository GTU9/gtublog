import type { GenerationResult } from "./provider.js";

export const generationDraftJsonSchema = {
  type: "object",
  properties: {
    title: { type: "string", minLength: 1, maxLength: 300 },
    excerpt: { type: "string", minLength: 1, maxLength: 1000 },
    contentMarkdown: { type: "string", minLength: 1, maxLength: 100000 },
    citationSnapshotIds: {
      type: "array",
      items: { type: "integer", minimum: 1 },
      minItems: 1,
      maxItems: 100,
      uniqueItems: true,
    },
  },
  required: ["title", "excerpt", "contentMarkdown", "citationSnapshotIds"],
  additionalProperties: false,
} as const;

export function validateGenerationDraft(value: unknown, provider: string): GenerationResult {
  if (typeof value !== "object" || value === null) {
    throw new Error(`${provider} provider returned a non-object response.`);
  }

  const candidate = value as Record<string, unknown>;
  if (
    !validNonBlankString(candidate.title, 300)
    || !validNonBlankString(candidate.excerpt, 1_000)
    || !validNonBlankString(candidate.contentMarkdown, 100_000)
    || !Array.isArray(candidate.citationSnapshotIds)
    || candidate.citationSnapshotIds.length < 1
    || candidate.citationSnapshotIds.length > 100
    || !candidate.citationSnapshotIds.every((item) => Number.isSafeInteger(item) && item > 0)
    || new Set(candidate.citationSnapshotIds).size !== candidate.citationSnapshotIds.length
  ) {
    throw new Error(`${provider} provider returned an invalid structured draft.`);
  }

  return {
    title: candidate.title,
    excerpt: candidate.excerpt,
    contentMarkdown: candidate.contentMarkdown,
    citationSnapshotIds: candidate.citationSnapshotIds,
    provider,
  };
}

function validNonBlankString(value: unknown, maximumLength: number): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= maximumLength;
}
