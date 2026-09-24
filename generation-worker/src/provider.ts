export interface GenerationRequest {
  readonly jobId: string;
  readonly runId: number;
  readonly topicId: number;
  readonly promptVersion: string;
  readonly schemaVersion: string;
  readonly snapshots: ReadonlyArray<GenerationSnapshot>;
  readonly prompt: string;
  readonly taxonomyCatalog?: TaxonomyCatalog;
}

export interface TaxonomyTerm { readonly id: number; readonly slug: string; readonly name: string; }
export interface TaxonomyCatalog { readonly categories: ReadonlyArray<TaxonomyTerm>; readonly tags: ReadonlyArray<TaxonomyTerm>; }
export interface TaxonomySelection { readonly categoryId: number; readonly tagIds: ReadonlyArray<number>; }

export interface GenerationSnapshot {
  readonly snapshotId: number;
  readonly sourceUrl: string;
  readonly canonicalUrl: string;
  readonly title: string | null;
  readonly originHost: string;
  readonly bodyExcerpt: string | null;
  readonly contentHash: string;
  readonly retrievedAt: string;
}

export interface GenerationResult {
  readonly title: string;
  readonly excerpt: string;
  readonly contentMarkdown: string;
  readonly citationSnapshotIds: ReadonlyArray<number>;
  readonly provider: string;
  readonly taxonomy?: TaxonomySelection;
}

export interface GenerationProvider {
  readonly name: string;
  generate(request: GenerationRequest, signal?: AbortSignal): Promise<GenerationResult>;
}

export function createGenerationWorker(provider: GenerationProvider) {
  return {
    generate(request: GenerationRequest, signal?: AbortSignal): Promise<GenerationResult> {
      return provider.generate(request, signal);
    },
  };
}
