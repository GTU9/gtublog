import type { GenerationProvider, GenerationRequest, GenerationSnapshot } from "./provider.js";

export const CONTRACT_SCHEMA_VERSION = "automation-job-v1";

export interface GenerationClaimRequest {
  readonly workerId: string;
  readonly supportedProviders: ReadonlyArray<string>;
  readonly supportedSchemaVersions: ReadonlyArray<string>;
}

export interface GenerationClaimResponse {
  readonly jobId: number;
  readonly jobKey: string;
  readonly runId: number;
  readonly topicId: number;
  readonly leaseOwner: string;
  readonly leaseExpiresAt: string;
  readonly providerName: string;
  readonly promptVersion: string;
  readonly schemaVersion: string;
  readonly prompt: string;
  readonly snapshots: ReadonlyArray<GenerationSnapshot>;
}

export interface GenerationSubmitRequest {
  readonly workerId: string;
  readonly providerName: string;
  readonly promptVersion: string;
  readonly schemaVersion: string;
  readonly draft?: {
    readonly title: string;
    readonly excerpt: string;
    readonly contentMarkdown: string;
    readonly citationSnapshotIds: ReadonlyArray<number>;
  };
  readonly failureReason?: string;
}

export interface GenerationHeartbeatRequest {
  readonly workerId: string;
}

export interface BackendGenerationClient {
  claim(request: GenerationClaimRequest): Promise<GenerationClaimResponse | null>;
  heartbeat(jobId: number, request: GenerationHeartbeatRequest): Promise<void>;
  submit(jobId: number, request: GenerationSubmitRequest): Promise<void>;
}

interface WorkerRuntimeDependencies {
  readonly client: BackendGenerationClient;
  readonly provider: GenerationProvider;
}

interface BackendGenerationClientOptions {
  readonly baseUrl: string;
  readonly token: string;
  readonly fetchImpl?: typeof fetch;
}

export function createWorkerRuntime({ client, provider }: WorkerRuntimeDependencies) {
  return {
    async runOnce(workerId: string): Promise<GenerationClaimResponse | null> {
      const claim = await client.claim({
        workerId,
        supportedProviders: [provider.name],
        supportedSchemaVersions: [CONTRACT_SCHEMA_VERSION],
      });
      if (!claim) {
        return null;
      }

      assertClaimResponse(claim);

      try {
        await client.heartbeat(claim.jobId, { workerId });
        const result = await provider.generate(toGenerationRequest(claim));
        await client.submit(claim.jobId, {
          workerId,
          providerName: claim.providerName,
          promptVersion: claim.promptVersion,
          schemaVersion: claim.schemaVersion,
          draft: {
            title: result.title,
            excerpt: result.excerpt,
            contentMarkdown: result.contentMarkdown,
            citationSnapshotIds: result.citationSnapshotIds,
          },
        });
      } catch (error) {
        const failureReason =
          error instanceof Error ? error.message : "Unknown worker failure.";
        await client.submit(claim.jobId, {
          workerId,
          providerName: claim.providerName,
          promptVersion: claim.promptVersion,
          schemaVersion: claim.schemaVersion,
          failureReason,
        });
        throw error;
      }

      return claim;
    },
  };
}

export function createBackendGenerationClient({
  baseUrl,
  token,
  fetchImpl = fetch,
}: BackendGenerationClientOptions): BackendGenerationClient {
  const workerAuth = { "X-Worker-Token": token };

  return {
    async claim(request: GenerationClaimRequest): Promise<GenerationClaimResponse | null> {
      const response = await fetchImpl(`${baseUrl}/api/v1/internal/generation-jobs/claim`, {
        method: "POST",
        headers: {
          ...workerAuth,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
      });
      if (response.status === 204) {
        return null;
      }
      if (!response.ok) {
        throw new Error(`Claim failed with status ${response.status.toString()}.`);
      }
      const body = (await response.json()) as unknown;
      assertClaimResponse(body);
      return body;
    },
    async heartbeat(jobId: number, request: GenerationHeartbeatRequest): Promise<void> {
      const response = await fetchImpl(
        `${baseUrl}/api/v1/internal/generation-jobs/${jobId.toString()}/heartbeat`,
        {
          method: "POST",
          headers: {
            ...workerAuth,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(request),
        },
      );
      if (!response.ok) {
        throw new Error(`Heartbeat failed with status ${response.status.toString()}.`);
      }
    },
    async submit(jobId: number, request: GenerationSubmitRequest): Promise<void> {
      assertSubmitRequest(request);
      const response = await fetchImpl(
        `${baseUrl}/api/v1/internal/generation-jobs/${jobId.toString()}/submit`,
        {
          method: "POST",
          headers: {
            ...workerAuth,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(request),
        },
      );
      if (!response.ok) {
        throw new Error(`Submit failed with status ${response.status.toString()}.`);
      }
    },
  };
}

export function assertClaimResponse(value: unknown): asserts value is GenerationClaimResponse {
  const candidate = value as Partial<GenerationClaimResponse> | null;
  if (
    !candidate ||
    typeof candidate.jobId !== "number" ||
    typeof candidate.jobKey !== "string" ||
    typeof candidate.runId !== "number" ||
    typeof candidate.topicId !== "number" ||
    typeof candidate.leaseOwner !== "string" ||
    typeof candidate.leaseExpiresAt !== "string" ||
    typeof candidate.providerName !== "string" ||
    typeof candidate.promptVersion !== "string" ||
    typeof candidate.schemaVersion !== "string" ||
    typeof candidate.prompt !== "string" ||
    !Array.isArray(candidate.snapshots)
  ) {
    throw new Error("Invalid generation claim payload.");
  }
}

export function assertSubmitRequest(value: unknown): asserts value is GenerationSubmitRequest {
  const candidate = value as Partial<GenerationSubmitRequest> | null;
  if (
    !candidate ||
    typeof candidate.workerId !== "string" ||
    typeof candidate.providerName !== "string" ||
    typeof candidate.promptVersion !== "string" ||
    typeof candidate.schemaVersion !== "string"
  ) {
    throw new Error("Invalid generation submit payload.");
  }

  if (candidate.failureReason && candidate.draft) {
    throw new Error("Submit payload cannot contain both a draft and a failure reason.");
  }
  if (!candidate.failureReason && !candidate.draft) {
    throw new Error("Submit payload requires either a draft or a failure reason.");
  }
}

function toGenerationRequest(claim: GenerationClaimResponse): GenerationRequest {
  return {
    jobId: claim.jobKey,
    runId: claim.runId,
    topicId: claim.topicId,
    promptVersion: claim.promptVersion,
    schemaVersion: claim.schemaVersion,
    snapshots: claim.snapshots,
    prompt: claim.prompt,
  };
}
