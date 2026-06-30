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
  readonly workingDirectory: string;
  readonly apiKey?: string;
  readonly env?: Readonly<Record<string, string>>;
}

const outputSchema = {
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

export function createCodexGenerationProvider(
  options: CodexProviderOptions,
): GenerationProvider {
  const safeToolEnvironment = Object.fromEntries(
    Object.entries(options.env ?? {}).filter(([name]) =>
      ["PATH", "PATHEXT", "SystemRoot", "ComSpec", "HOME", "USERPROFILE", "CODEX_HOME", "TEMP", "TMP", "TMPDIR", "LANG"].includes(name),
    ),
  );
  const codexFactory = options.codexFactory ?? (() => new Codex({
    apiKey: options.apiKey,
    env: options.env,
    config: {
      shell_environment_policy: {
        inherit: "none",
        include_only: Object.keys(safeToolEnvironment),
        experimental_use_profile: false,
        set: safeToolEnvironment,
      },
    },
  }));

  return {
    name: "codex-sdk",
    async generate(request: GenerationRequest, signal?: AbortSignal): Promise<GenerationResult> {
      const codex = codexFactory();
      const thread = codex.startThread({
        workingDirectory: options.workingDirectory,
        sandboxMode: "read-only",
        approvalPolicy: "never",
        networkAccessEnabled: false,
        webSearchMode: "disabled",
        modelReasoningEffort: "low",
      });
      const turn = await thread.run(
        `${request.prompt}\n\n반드시 JSON만 반환하세요.`,
        { outputSchema, signal },
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
    !validNonBlankString(candidate.title, 300) ||
    !validNonBlankString(candidate.excerpt, 1_000) ||
    !validNonBlankString(candidate.contentMarkdown, 100_000) ||
    !Array.isArray(candidate.citationSnapshotIds) ||
    candidate.citationSnapshotIds.length < 1 ||
    candidate.citationSnapshotIds.length > 100 ||
    !candidate.citationSnapshotIds.every((item) => Number.isSafeInteger(item) && item > 0) ||
    new Set(candidate.citationSnapshotIds).size !== candidate.citationSnapshotIds.length
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

function validNonBlankString(value: unknown, maximumLength: number): value is string {
  return typeof value === "string"
    && value.trim().length > 0
    && value.length <= maximumLength;
}
