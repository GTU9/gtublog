import { Codex } from "@openai/codex-sdk";

import type { GenerationProvider, GenerationRequest, GenerationResult } from "./provider.js";

type CodexFactory = () => CodexLike;

interface CodexLike {
  startThread(options: Record<string, unknown>): {
    run(prompt: string, options: { outputSchema: object; signal?: AbortSignal }): Promise<{ finalResponse: string }>;
  };
}

interface CodexProviderOptions {
  readonly codexFactory?: CodexFactory;
  readonly workingDirectory?: string;
}

const outputSchema = {
  type: "object",
  properties: {
    title: { type: "string" },
    excerpt: { type: "string" },
    contentMarkdown: { type: "string" },
    citationSnapshotIds: {
      type: "array",
      items: { type: "number" },
      minItems: 1,
    },
  },
  required: ["title", "excerpt", "contentMarkdown", "citationSnapshotIds"],
  additionalProperties: false,
} as const;

export function createCodexGenerationProvider(
  options: CodexProviderOptions = {},
): GenerationProvider {
  const codexFactory = options.codexFactory ?? (() => new Codex());

  return {
    name: "codex-sdk",
    async generate(request: GenerationRequest): Promise<GenerationResult> {
      const codex = codexFactory();
      const thread = codex.startThread({
        workingDirectory: options.workingDirectory ?? process.cwd(),
        sandboxMode: "read-only",
        approvalPolicy: "never",
        networkAccessEnabled: false,
        webSearchMode: "disabled",
        modelReasoningEffort: "low",
      });
      const turn = await thread.run(
        `${request.prompt}\n\n반드시 JSON만 반환하세요.`,
        { outputSchema },
      );
      const parsed = JSON.parse(turn.finalResponse) as unknown;
      return validateCodexDraft(parsed);
    },
  };
}

function validateCodexDraft(value: unknown): GenerationResult {
  if (typeof value !== "object" || value === null) {
    throw new Error("Codex provider returned a non-object response.");
  }

  const candidate = value as Record<string, unknown>;
  if (
    typeof candidate.title !== "string" ||
    typeof candidate.excerpt !== "string" ||
    typeof candidate.contentMarkdown !== "string" ||
    !Array.isArray(candidate.citationSnapshotIds) ||
    !candidate.citationSnapshotIds.every((item) => typeof item === "number")
  ) {
    throw new Error("Codex provider returned an invalid structured draft.");
  }

  return {
    title: candidate.title,
    excerpt: candidate.excerpt,
    contentMarkdown: candidate.contentMarkdown,
    citationSnapshotIds: candidate.citationSnapshotIds,
    provider: "codex-sdk",
  };
}
