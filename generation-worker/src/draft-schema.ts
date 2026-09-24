import type { GenerationResult } from "./provider.js";

export class InvalidGenerationDraftError extends Error {
  constructor(reason: "structure" | "taxonomy") {
    super(reason === "taxonomy" ? "Generated draft has invalid taxonomy fields." : "Provider returned an invalid structured draft.");
    this.name = "InvalidGenerationDraftError";
  }
}

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

export const generationDraftJsonSchemaV3 = {
  ...generationDraftJsonSchema,
  properties: {
    ...generationDraftJsonSchema.properties,
    taxonomy: {
      type: "object",
      properties: {
        categoryId: { type: "integer", minimum: 1 },
        tagIds: { type: "array", items: { type: "integer", minimum: 1 }, minItems: 1, maxItems: 5 },
      },
      required: ["categoryId", "tagIds"],
      additionalProperties: false,
    },
  },
  required: [...generationDraftJsonSchema.required, "taxonomy"],
} as const;

export function validateGenerationDraft(value: unknown, provider: string, schemaVersion = "automation-job-v2"): GenerationResult {
  if (typeof value !== "object" || value === null) {
    throw new InvalidGenerationDraftError("structure");
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
    throw new InvalidGenerationDraftError("structure");
  }

  const taxonomy = candidate.taxonomy as Record<string, unknown> | undefined;
  if (schemaVersion === "automation-job-v3" && (
    !taxonomy || typeof taxonomy !== "object" || !Number.isSafeInteger(taxonomy.categoryId) || (taxonomy.categoryId as number) <= 0
    || !Array.isArray(taxonomy.tagIds) || taxonomy.tagIds.length < 1 || taxonomy.tagIds.length > 5
    || !taxonomy.tagIds.every((id) => Number.isSafeInteger(id) && id > 0)
  )) throw new InvalidGenerationDraftError("taxonomy");

  return {
    title: candidate.title,
    excerpt: candidate.excerpt,
    contentMarkdown: candidate.contentMarkdown,
    citationSnapshotIds: candidate.citationSnapshotIds,
    provider,
    ...(schemaVersion === "automation-job-v3" ? { taxonomy: taxonomy as unknown as { categoryId: number; tagIds: number[] } } : {}),
  };
}

function validNonBlankString(value: unknown, maximumLength: number): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= maximumLength;
}
