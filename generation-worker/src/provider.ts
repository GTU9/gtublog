export interface GenerationRequest {
  readonly jobId: string;
  readonly prompt: string;
}

export interface GenerationResult {
  readonly content: string;
  readonly provider: string;
}

export interface GenerationProvider {
  readonly name: string;
  generate(request: GenerationRequest): Promise<GenerationResult>;
}

export function createGenerationWorker(provider: GenerationProvider) {
  return {
    generate(request: GenerationRequest): Promise<GenerationResult> {
      return provider.generate(request);
    },
  };
}
