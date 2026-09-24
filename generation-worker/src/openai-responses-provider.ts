import { generationDraftJsonSchema, generationDraftJsonSchemaV3, generationObservationJsonSchemaV4, validateGenerationDraft } from "./draft-schema.js";
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
      const input = request.schemaVersion === "automation-job-v4"
        ? [
            request.prompt,
            "",
            `Choose taxonomy IDs only from this approved catalog: ${JSON.stringify(request.taxonomyCatalog)}`,
            "Return JSON with only top-level observations and taxonomy.",
            "Each observation must have kind SOURCE_MENTION, a 20-160 character literal copied from source text, and exactly two different citationSnapshotIds.",
            "Do not return title, excerpt, contentMarkdown, markdown, HTML, or prose.",
          ].join("\n")
        : request.schemaVersion === "automation-job-v3"
          ? `${request.prompt}\n\nChoose taxonomy IDs only from this approved catalog: ${JSON.stringify(request.taxonomyCatalog)}`
          : request.prompt;
      const schema = request.schemaVersion === "automation-job-v4"
        ? generationObservationJsonSchemaV4
        : request.schemaVersion === "automation-job-v3" ? generationDraftJsonSchemaV3 : generationDraftJsonSchema;
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
            input,
            tools: [],
            tool_choice: "none",
            store: false,
            text: {
              format: {
                type: "json_schema",
                name: request.schemaVersion === "automation-job-v4" ? "source_observations" : "generation_draft",
                strict: true,
                schema,
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
        return validateGenerationDraft(JSON.parse(outputText) as unknown, "openai-responses", request.schemaVersion);
      } catch (error) {
        if (error instanceof SyntaxError) throw new Error("OpenAI Responses returned malformed structured output.");
        throw error;
      }
    },
  };
}
