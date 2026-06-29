import { Codex } from "@openai/codex-sdk";

const outputSchema = {
  type: "object",
  properties: {
    status: { type: "string", enum: ["ok"] },
    adapter: { type: "string", enum: ["codex-sdk"] },
  },
  required: ["status", "adapter"],
  additionalProperties: false,
} as const;

const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 90_000);

try {
  const codex = new Codex();
  const thread = codex.startThread({
    workingDirectory: process.cwd(),
    sandboxMode: "read-only",
    approvalPolicy: "never",
    networkAccessEnabled: false,
    webSearchMode: "disabled",
    modelReasoningEffort: "low",
  });

  const turn = await thread.run(
    "Return the requested JSON only. Do not inspect files, execute commands, or call tools.",
    { outputSchema, signal: controller.signal },
  );
  const response = JSON.parse(turn.finalResponse) as unknown;

  if (
    typeof response !== "object" ||
    response === null ||
    !("status" in response) ||
    !("adapter" in response) ||
    response.status !== "ok" ||
    response.adapter !== "codex-sdk"
  ) {
    throw new Error("Codex SDK returned an invalid structured response");
  }

  process.stdout.write(
    `${JSON.stringify({ status: "pass", adapter: "codex-sdk", structuredOutput: true })}\n`,
  );
} finally {
  clearTimeout(timeout);
}
