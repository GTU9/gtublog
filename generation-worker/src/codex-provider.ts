import { Codex } from "@openai/codex-sdk";

import { generationDraftJsonSchema, validateGenerationDraft } from "./draft-schema.js";
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
        { outputSchema: generationDraftJsonSchema, signal },
      );
      const parsed = JSON.parse(turn.finalResponse) as unknown;
      return validateGenerationDraft(parsed, "codex-sdk");
    },
  };
}
