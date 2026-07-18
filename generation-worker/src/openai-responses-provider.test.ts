import { describe, expect, it, vi } from "vitest";

import { createOpenAIResponsesGenerationProvider } from "./openai-responses-provider.js";
import type { GenerationRequest } from "./provider.js";

const request: GenerationRequest = {
  jobId: "job-26",
  runId: 26,
  topicId: 1,
  promptVersion: "prompt-v1",
  schemaVersion: "automation-job-v2",
  snapshots: [],
  prompt: "Write a cited draft without using tools.",
};

const validResponse = {
  status: "completed",
  output: [{
    type: "message",
    content: [{
      type: "output_text",
      text: JSON.stringify({
        title: "안전한 초안",
        excerpt: "요약",
        contentMarkdown: "# 본문",
        citationSnapshotIds: [1, 2],
      }),
    }],
  }],
};

describe("OpenAI Responses generation provider", () => {
  it("sends a tool-free request with a strict JSON Schema and maps its draft", async () => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(validResponse), { status: 200 }));
    const provider = createOpenAIResponsesGenerationProvider({
      apiKey: "openai-api-key-canary",
      model: "gpt-test-model",
      fetchImplementation,
    });

    await expect(provider.generate(request)).resolves.toEqual({
      title: "안전한 초안",
      excerpt: "요약",
      contentMarkdown: "# 본문",
      citationSnapshotIds: [1, 2],
      provider: "openai-responses",
    });

    expect(fetchImplementation).toHaveBeenCalledWith("https://api.openai.com/v1/responses", expect.objectContaining({
      method: "POST",
      headers: {
        Authorization: "Bearer openai-api-key-canary",
        "Content-Type": "application/json",
      },
    }));
    const init = fetchImplementation.mock.calls[0]?.[1];
    if (typeof init?.body !== "string") throw new Error("Responses request body was not a JSON string.");
    const body = JSON.parse(init.body) as Record<string, unknown>;
    expect(body).toMatchObject({
      model: "gpt-test-model",
      input: request.prompt,
      tools: [],
      tool_choice: "none",
      store: false,
      text: { format: { type: "json_schema", name: "generation_draft", strict: true } },
    });
    expect(JSON.stringify(body)).not.toContain("openai-api-key-canary");
    expect(JSON.stringify(body)).not.toContain("worker-token-canary");
  });

  it("forwards cancellation to fetch", async () => {
    const controller = new AbortController();
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(validResponse), { status: 200 }));
    const provider = createOpenAIResponsesGenerationProvider({ apiKey: "key", model: "model", fetchImplementation });

    await provider.generate(request, controller.signal);

    expect(fetchImplementation.mock.calls[0]?.[1]?.signal).toBe(controller.signal);
  });

  it.each([401, 429, 500])("hides provider response bodies and credentials for HTTP %i", async (status) => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(new Response("provider-body-secret", { status }));
    const provider = createOpenAIResponsesGenerationProvider({
      apiKey: "openai-api-key-canary",
      model: "model",
      fetchImplementation,
    });

    await expect(provider.generate(request)).rejects.toThrow(`status ${status}`);
    await provider.generate(request).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      expect(message).not.toContain("provider-body-secret");
      expect(message).not.toContain("openai-api-key-canary");
    });
  });

  it.each([
    [{ status: "completed", output: [] }, "no structured draft"],
    [{ status: "incomplete", output: [] }, "did not complete generation"],
    [{ status: "completed", error: { message: "provider-body-secret" }, output: [] }, "did not complete generation"],
    [{ status: "completed", incomplete_details: { reason: "max_output_tokens" }, output: [] }, "did not complete generation"],
    [{ status: "completed", output: [{ type: "message", content: [{ type: "output_text", text: "not-json" }] }] }, "malformed structured output"],
    [{ status: "completed", output: [{ type: "message", content: [{ type: "output_text", text: JSON.stringify({ title: "x" }) }] }] }, "invalid structured draft"],
  ])("rejects malformed output before returning a draft", async (payload, expectedMessage) => {
    const fetchImplementation = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 }));
    const provider = createOpenAIResponsesGenerationProvider({ apiKey: "key", model: "model", fetchImplementation });

    await expect(provider.generate(request)).rejects.toThrow(expectedMessage);
  });
});
