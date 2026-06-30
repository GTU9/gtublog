import { beforeEach, describe, expect, it, vi } from "vitest";

const constructorOptions: unknown[] = [];
const startThread = vi.fn(() => ({
  run: vi.fn().mockResolvedValue({
    finalResponse: JSON.stringify({
      title: "Safe draft",
      excerpt: "Safe summary",
      contentMarkdown: "# Safe draft",
      citationSnapshotIds: [1],
    }),
  }),
}));

vi.mock("@openai/codex-sdk", () => ({
  Codex: class {
    constructor(options: unknown) {
      constructorOptions.push(options);
    }

    startThread = startThread;
  },
}));

import { createCodexGenerationProvider } from "./codex-provider.js";

describe("Codex provider process isolation", () => {
  beforeEach(() => {
    constructorOptions.length = 0;
    startThread.mockClear();
  });

  it("uses an explicit API key and a deny-by-default shell environment policy", async () => {
    const provider = createCodexGenerationProvider({
      apiKey: "codex-api-key-canary",
      workingDirectory: "D:/isolated-empty-workspace",
      env: {
        PATH: "D:/safe-bin",
        HOME: "D:/isolated-home",
        TMPDIR: "D:/isolated-temp",
      },
    });

    await provider.generate({
      jobId: "job-13",
      runId: 13,
      topicId: 1,
      promptVersion: "prompt-v1",
      schemaVersion: "automation-job-v2",
      snapshots: [],
      prompt: "Write a draft.",
    });

    expect(constructorOptions).toHaveLength(1);
    expect(constructorOptions[0]).toMatchObject({
      apiKey: "codex-api-key-canary",
      env: {
        PATH: "D:/safe-bin",
        HOME: "D:/isolated-home",
        TMPDIR: "D:/isolated-temp",
      },
      config: {
        shell_environment_policy: {
          inherit: "none",
          experimental_use_profile: false,
        },
      },
    });

    const options = constructorOptions[0] as {
      config: { shell_environment_policy: { include_only: string[]; set: Record<string, string> } };
    };
    const policy = options.config.shell_environment_policy;
    expect(policy.include_only).toEqual(expect.arrayContaining(["PATH", "HOME", "TMPDIR"]));
    expect(policy.include_only).not.toEqual(expect.arrayContaining([
      "CODEX_API_KEY",
      "OPENAI_API_KEY",
      "GENERATION_WORKER_TOKEN",
    ]));
    expect(JSON.stringify(policy.set)).not.toContain("codex-api-key-canary");
    expect(JSON.stringify(policy.set)).not.toContain("worker-token-canary");
    expect(startThread).toHaveBeenCalledWith(expect.objectContaining({
      workingDirectory: "D:/isolated-empty-workspace",
      sandboxMode: "read-only",
      approvalPolicy: "never",
      networkAccessEnabled: false,
      webSearchMode: "disabled",
    }));
  });
});
