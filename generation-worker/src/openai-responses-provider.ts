import { generationDraftJsonSchema, validateGenerationDraft } from "./draft-schema.js";
import type { GenerationProvider, GenerationRequest, GenerationResult } from "./provider.js";

const responsesEndpoint = "https://api.openai.com/v1/responses";

interface OpenAIResponsesProviderOptions {
  readonly apiKey: string;
  readonly model: string;
  readonly fetchImplementation?: typeof fetch;
  readonly endpoint?: string;
}

interface ResponsesPayload {
  readonly status?: string;
  readonly error?: unknown;
  readonly incomplete_details?: unknown;
  readonly output?: ReadonlyArray<{
    readonly type?: string;
    readonly content?: ReadonlyArray<{ readonly type?: string; readonly text?: string }>;
  }>;
}

export function createOpenAIResponsesGenerationProvider(options: OpenAIResponsesProviderOptions): GenerationProvider {
  const fetchImplementation = options.fetchImplementation ?? fetch;
  const endpoint = options.endpoint ?? responsesEndpoint;

  return {
    name: "openai-responses",
    async generate(request: GenerationRequest, signal?: AbortSignal): Promise<GenerationResult> {
      let response: Response;
      try {
        response = await fetchImplementation(endpoint, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${options.apiKey}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model: options.model,
            input: request.prompt,
            tools: [],
            tool_choice: "none",
            store: false,
            text: {
              format: {
                type: "json_schema",
                name: "generation_draft",
                strict: true,
                schema: generationDraftJsonSchema,
              },
            },
          }),
          signal,
        });
      } catch (error) {
        if (signal?.aborted) throw error;
        throw new Error("OpenAI Responses request failed.");
      }

      if (!response.ok) {
        throw new Error(`OpenAI Responses request failed with status ${response.status}.`);
      }

      let payload: ResponsesPayload;
      try {
        payload = await response.json() as ResponsesPayload;
      } catch {
        throw new Error("OpenAI Responses returned an invalid JSON response.");
      }
      if (payload.status !== "completed" || payload.error !== undefined || payload.incomplete_details !== undefined) {
        throw new Error("OpenAI Responses did not complete generation.");
      }
      const outputText = payload.output
        ?.find((item) => item.type === "message")
        ?.content?.find((item) => item.type === "output_text")
        ?.text;
      if (!outputText) throw new Error("OpenAI Responses returned no structured draft.");

      try {
        return validateGenerationDraft(JSON.parse(outputText) as unknown, "openai-responses");
      } catch (error) {
        if (error instanceof SyntaxError) throw new Error("OpenAI Responses returned malformed structured output.");
        throw error;
      }
    },
  };
}
