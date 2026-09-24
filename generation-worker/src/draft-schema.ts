import type { GenerationResult, SourceMentionObservation, TaxonomySelection } from "./provider.js";

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

export const generationObservationJsonSchemaV4 = {
  type: "object",
  properties: {
    observations: {
      type: "array",
      minItems: 1,
      maxItems: 3,
      items: {
        type: "object",
        properties: {
          kind: { const: "SOURCE_MENTION" },
          literal: { type: "string", minLength: 20, maxLength: 160 },
          citationSnapshotIds: {
            type: "array",
            items: { type: "integer", minimum: 1 },
            minItems: 2,
            maxItems: 2,
            uniqueItems: true,
          },
        },
        required: ["kind", "literal", "citationSnapshotIds"],
        additionalProperties: false,
      },
    },
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
  required: ["observations", "taxonomy"],
  additionalProperties: false,
} as const;

export function validateGenerationDraft(value: unknown, provider: string, schemaVersion = "automation-job-v2"): GenerationResult {
  if (typeof value !== "object" || value === null) {
    throw new InvalidGenerationDraftError("structure");
  }

  const candidate = value as Record<string, unknown>;
  if (schemaVersion === "automation-job-v4") {
    const taxonomy = candidate.taxonomy as Record<string, unknown> | undefined;
    if (!hasOnlyKeys(candidate, ["observations", "taxonomy"])) throw new InvalidGenerationDraftError("structure");
    if (!validTaxonomySelection(taxonomy)) throw new InvalidGenerationDraftError("taxonomy");
    if (
      !Array.isArray(candidate.observations)
      || candidate.observations.length < 1
      || candidate.observations.length > 3
      || !candidate.observations.every(validSourceMentionObservation)
    ) throw new InvalidGenerationDraftError("structure");
    return {
      observations: candidate.observations.map((observation) => ({
        kind: "SOURCE_MENTION",
        literal: normalizeObservationLiteral(observation.literal),
        citationSnapshotIds: [...observation.citationSnapshotIds].sort((a, b) => a - b) as [number, number],
      })),
      taxonomy: { categoryId: taxonomy.categoryId, tagIds: taxonomy.tagIds },
      provider,
    };
  }

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
  if (schemaVersion === "automation-job-v3" && !validTaxonomySelection(taxonomy)) throw new InvalidGenerationDraftError("taxonomy");

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

function validTaxonomySelection(value: unknown): value is TaxonomySelection {
  if (!value || typeof value !== "object") return false;
  const taxonomy = value as Partial<TaxonomySelection>;
  return Number.isSafeInteger(taxonomy.categoryId) && (taxonomy.categoryId as number) > 0
    && Array.isArray(taxonomy.tagIds) && taxonomy.tagIds.length >= 1 && taxonomy.tagIds.length <= 5
    && taxonomy.tagIds.every((id) => Number.isSafeInteger(id) && id > 0);
}

function validSourceMentionObservation(value: unknown): value is SourceMentionObservation {
  if (!value || typeof value !== "object") return false;
  if (!hasOnlyKeys(value as Record<string, unknown>, ["kind", "literal", "citationSnapshotIds"])) return false;
  const observation = value as Partial<SourceMentionObservation>;
  const literal = normalizeObservationLiteral(observation.literal);
  return observation.kind === "SOURCE_MENTION"
    && literal.length >= 20
    && literal.length <= 160
    && !/[<>\p{C}]/u.test(literal)
    && Array.isArray(observation.citationSnapshotIds)
    && observation.citationSnapshotIds.length === 2
    && observation.citationSnapshotIds.every((item) => Number.isSafeInteger(item) && item > 0)
    && new Set(observation.citationSnapshotIds).size === 2;
}

function normalizeObservationLiteral(value: unknown): string {
  return typeof value === "string" ? value.normalize("NFC").replace(/[\p{White_Space}]+/gu, " ").trim() : "";
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: ReadonlyArray<string>): boolean {
  const allowedKeys = new Set(allowed);
  return Object.keys(value).every((key) => allowedKeys.has(key));
}
