import type { GenerationProvider, GenerationRequest, GenerationSnapshot } from "./provider.js";
import { terminalPayloadDigest, terminalSubmissionId } from "./terminal.js";

export const CONTRACT_SCHEMA_VERSION = "automation-job-v2";

export interface GenerationClaimRequest { readonly workerId: string; readonly supportedProviders: ReadonlyArray<string>; readonly supportedSchemaVersions: ReadonlyArray<string>; }
export interface GenerationClaimResponse {
  readonly jobId: number; readonly jobKey: string; readonly runId: number; readonly topicId: number;
  readonly leaseOwner: string; readonly leaseExpiresAt: string; readonly providerName: string;
  readonly promptVersion: string; readonly schemaVersion: string; readonly prompt: string;
  readonly snapshots: ReadonlyArray<GenerationSnapshot>;
}
export interface GenerationHeartbeatRequest { readonly workerId: string; }
export interface GenerationHeartbeatResponse { readonly jobId: number; readonly status: string; readonly serverTime: string; readonly leaseExpiresAt: string; }
export interface GenerationSubmitRequest {
  readonly terminalSubmissionId: string; readonly payloadDigest: string; readonly workerId: string;
  readonly providerName: string; readonly promptVersion: string; readonly schemaVersion: string;
  readonly draft?: { readonly title: string; readonly excerpt: string; readonly contentMarkdown: string; readonly citationSnapshotIds: ReadonlyArray<number>; };
  readonly failureReason?: string;
}
export interface GenerationSubmitResponse { readonly jobId: number; readonly status: string; readonly submittedAt: string; readonly terminalSubmissionId: string; readonly payloadDigest: string; }

export class BackendOperationError extends Error {
  constructor(readonly operation: "claim" | "heartbeat" | "submit", readonly status: number | null, readonly kind: "fatal" | "transient" | "lease-lost" | "conflict", message: string) { super(message); this.name = "BackendOperationError"; }
}

export class GenerationTimeoutError extends Error {
  constructor() { super("Generation timeout."); this.name = "GenerationTimeoutError"; }
}

export interface BackendGenerationClient {
  claim(request: GenerationClaimRequest, signal?: AbortSignal): Promise<GenerationClaimResponse | null>;
  heartbeat(jobId: number, request: GenerationHeartbeatRequest, signal?: AbortSignal): Promise<GenerationHeartbeatResponse>;
  submit(jobId: number, request: GenerationSubmitRequest, signal?: AbortSignal): Promise<GenerationSubmitResponse>;
}

interface WorkerRuntimeDependencies { readonly client: BackendGenerationClient; readonly provider: GenerationProvider; readonly heartbeatIntervalMs?: number; readonly generationTimeoutMs?: number; readonly leaseSafetyMarginMs?: number; readonly shutdownGraceMs?: number; }
export type BackendContactOperation = "claim" | "heartbeat" | "submit";
export interface BackendContact { readonly operation: BackendContactOperation; readonly status: number; readonly at: string; }
interface BackendGenerationClientOptions {
  readonly baseUrl: string;
  readonly token: string;
  readonly requestTimeoutMs?: number;
  readonly fetchImpl?: typeof fetch;
  readonly onBackendContact?: (contact: BackendContact) => void;
}

export function createWorkerRuntime({ client, provider, heartbeatIntervalMs = 30_000, generationTimeoutMs = 240_000, leaseSafetyMarginMs = 45_000, shutdownGraceMs = 20_000 }: WorkerRuntimeDependencies) {
  return {
    async runOnce(workerId: string, shutdownSignal?: AbortSignal): Promise<GenerationClaimResponse | null> {
      const claim = await client.claim({ workerId, supportedProviders: [provider.name], supportedSchemaVersions: [CONTRACT_SCHEMA_VERSION] }, shutdownSignal);
      if (!claim) return null;
      assertClaimResponse(claim);
      if (claim.leaseOwner !== workerId || claim.providerName !== provider.name) throw new BackendOperationError("claim", 200, "fatal", "Generation claim identity did not match this worker.");
      const initialHeartbeatStarted = performance.now();
      const initialHeartbeat = await client.heartbeat(
        claim.jobId,
        { workerId },
        shutdownSignal ?? new AbortController().signal,
      );
      let leaseDeadline = deadlineFromLease(
        initialHeartbeat.leaseExpiresAt,
        initialHeartbeat.serverTime,
        performance.now() - initialHeartbeatStarted,
        leaseSafetyMarginMs,
      );
      if (leaseDeadline <= performance.now()) throw new BackendOperationError("heartbeat", 409, "lease-lost", "Generation lease is no longer safe.");
      const generationController = new AbortController();
      const heartbeatController = new AbortController();
      const generationDeadline = setTimeout(() => generationController.abort(new GenerationTimeoutError()), generationTimeoutMs);
      const signal = shutdownSignal ? AbortSignal.any([shutdownSignal, generationController.signal]) : generationController.signal;
      const heartbeatSignal = shutdownSignal ? AbortSignal.any([shutdownSignal, heartbeatController.signal]) : heartbeatController.signal;
      let heartbeatStopped = false;
      let heartbeatFailure: unknown;
      const heartbeatLoop = (async () => {
        while (!heartbeatStopped && !heartbeatSignal.aborted) {
          await abortableSleep(Math.min(heartbeatIntervalMs, Math.max(1, leaseDeadline - performance.now())), heartbeatSignal);
          if (heartbeatStopped || heartbeatSignal.aborted) return;
          const started = performance.now();
          try {
            const response = await client.heartbeat(claim.jobId, { workerId }, heartbeatSignal);
            leaseDeadline = deadlineFromLease(response.leaseExpiresAt, response.serverTime, performance.now() - started, leaseSafetyMarginMs);
            if (leaseDeadline <= performance.now()) {
              heartbeatFailure = new BackendOperationError("heartbeat", 409, "lease-lost", "Generation lease is no longer safe.");
              generationController.abort(heartbeatFailure);
            }
          } catch (error) {
            heartbeatFailure = error;
            generationController.abort(error);
          }
        }
      })();
      let cleanedUp = false;
      const cleanup = async (): Promise<void> => {
        if (cleanedUp) return;
        cleanedUp = true;
        clearTimeout(generationDeadline);
        heartbeatStopped = true;
        heartbeatController.abort();
        await heartbeatLoop.catch(() => undefined);
      };
      let result;
      try {
        result = await provider.generate(toGenerationRequest(claim), signal);
      } catch (error) {
        const controlFailure: unknown = heartbeatFailure ?? (signal.aborted ? signal.reason as unknown : undefined);
        clearTimeout(generationDeadline);
        if (heartbeatFailure) { await cleanup(); throw asError(heartbeatFailure); }
        if (controlFailure !== undefined && !(controlFailure instanceof GenerationTimeoutError)) { await cleanup(); throw asError(controlFailure); }
        const effectiveError = controlFailure instanceof GenerationTimeoutError ? controlFailure : error;
        if (effectiveError instanceof BackendOperationError && (effectiveError.kind === "lease-lost" || effectiveError.kind === "conflict" || effectiveError.kind === "fatal")) { await cleanup(); throw effectiveError; }
        const terminal = withDigest({
          terminalSubmissionId: terminalSubmissionId(), workerId, providerName: claim.providerName,
          promptVersion: claim.promptVersion, schemaVersion: claim.schemaVersion,
          failureReason: safeFailureReason(effectiveError),
        });
        try {
          await retrySameSubmission(client, claim.jobId, terminal, shutdownGraceMs, () => leaseDeadline, () => heartbeatFailure);
        } finally {
          await cleanup();
        }
        throw effectiveError;
      }
      clearTimeout(generationDeadline);
      if (heartbeatFailure) { await cleanup(); throw asError(heartbeatFailure); }
      const allowedCitationIds = new Set(claim.snapshots.map((snapshot) => snapshot.snapshotId));
      if (!result.citationSnapshotIds.every((snapshotId) => allowedCitationIds.has(snapshotId))) { await cleanup(); throw new BackendOperationError("submit", null, "fatal", "Generated citations were outside the claimed snapshot set."); }
      const successTerminal = withDigest({
        terminalSubmissionId: terminalSubmissionId(), workerId, providerName: claim.providerName,
        promptVersion: claim.promptVersion, schemaVersion: claim.schemaVersion,
        draft: { title: result.title, excerpt: result.excerpt, contentMarkdown: result.contentMarkdown, citationSnapshotIds: result.citationSnapshotIds },
      });
      try {
        // Once a success payload exists it is immutable: transport ambiguity may only retry this exact payload.
        await retrySameSubmission(client, claim.jobId, successTerminal, shutdownGraceMs, () => leaseDeadline, () => heartbeatFailure);
      } finally {
        await cleanup();
      }
      return claim;
    },
  };
}

export function createBackendGenerationClient({ baseUrl, token, requestTimeoutMs = 10_000, fetchImpl = fetch, onBackendContact }: BackendGenerationClientOptions): BackendGenerationClient {
  const request = async <T>(operation: "claim" | "heartbeat" | "submit", url: string, body: object, signal?: AbortSignal): Promise<{ status: number; body: T | null }> => {
    const timeout = AbortSignal.timeout(requestTimeoutMs);
    const combined = signal ? AbortSignal.any([signal, timeout]) : timeout;
    let response: Response;
    try {
      response = await fetchImpl(url, { method: "POST", signal: combined, headers: { "X-Worker-Token": token, "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify(body) });
    } catch { throw new BackendOperationError(operation, null, "transient", `${operation} transport failed.`); }
    if (response.status === 204) {
      onBackendContact?.({ operation, status: response.status, at: new Date().toISOString() });
      return { status: response.status, body: null };
    }
    if (!response.ok) throw classify(operation, response.status);
    onBackendContact?.({ operation, status: response.status, at: new Date().toISOString() });
    return { status: response.status, body: await response.json() as T };
  };
  return {
    async claim(body, signal) { const response = await request<GenerationClaimResponse>("claim", `${baseUrl}/api/v2/internal/generation-jobs/claim`, body, signal); if (!response.body) return null; assertClaimResponse(response.body); return response.body; },
    async heartbeat(jobId, body, signal) { const response = await request<GenerationHeartbeatResponse>("heartbeat", `${baseUrl}/api/v2/internal/generation-jobs/${jobId.toString()}/heartbeat`, body, signal); if (!response.body) throw new BackendOperationError("heartbeat", 204, "fatal", "Heartbeat response was empty."); assertHeartbeatResponse(response.body); return response.body; },
    async submit(jobId, body, signal) { assertSubmitRequest(body); const response = await request<GenerationSubmitResponse>("submit", `${baseUrl}/api/v2/internal/generation-jobs/${jobId.toString()}/submit`, body, signal); if (!response.body) throw new BackendOperationError("submit", 204, "fatal", "Submit response was empty."); assertSubmitResponse(response.body, body); return response.body; },
  };
}

export function assertClaimResponse(value: unknown): asserts value is GenerationClaimResponse {
  const c = value as Partial<GenerationClaimResponse> | null;
  if (!c || !positiveInteger(c.jobId) || !boundedString(c.jobKey, 1, 36) || !positiveInteger(c.runId) || !positiveInteger(c.topicId) || !boundedString(c.leaseOwner, 1, 120) || !validInstant(c.leaseExpiresAt) || !boundedString(c.providerName, 1, 64) || !boundedString(c.promptVersion, 1, 64) || c.schemaVersion !== CONTRACT_SCHEMA_VERSION || !boundedString(c.prompt, 1, 100_000) || !Array.isArray(c.snapshots) || c.snapshots.length < 1 || c.snapshots.length > 100 || !c.snapshots.every(validSnapshot)) throw new Error("Invalid generation claim payload.");
}
export function assertHeartbeatResponse(value: unknown): asserts value is GenerationHeartbeatResponse {
  const c = value as Partial<GenerationHeartbeatResponse> | null;
  if (!c || !Number.isSafeInteger(c.jobId) || typeof c.status !== "string" || !validInstant(c.serverTime) || !validInstant(c.leaseExpiresAt)) throw new Error("Invalid heartbeat response payload.");
}
export function assertSubmitResponse(value: unknown, request?: GenerationSubmitRequest): asserts value is GenerationSubmitResponse {
  const c = value as Partial<GenerationSubmitResponse> | null;
  if (!c || !positiveInteger(c.jobId) || !boundedString(c.status, 1, 32) || !validInstant(c.submittedAt) || !boundedString(c.terminalSubmissionId, 36, 36) || !/^[0-9a-f]{64}$/u.test(c.payloadDigest ?? "")) throw new BackendOperationError("submit", 200, "fatal", "Submit response payload was invalid.");
  if (request && (c.terminalSubmissionId !== request.terminalSubmissionId || c.payloadDigest !== request.payloadDigest)) throw new BackendOperationError("submit", 200, "conflict", "Submit response identity did not match the request.");
}
export function assertSubmitRequest(value: unknown): asserts value is GenerationSubmitRequest {
  const c = value as Partial<GenerationSubmitRequest> | null;
  if (!c || typeof c.terminalSubmissionId !== "string" || !/^[0-9a-f-]{36}$/iu.test(c.terminalSubmissionId) || !/^[0-9a-f]{64}$/u.test(c.payloadDigest ?? "") || !boundedString(c.workerId, 1, 120) || !boundedString(c.providerName, 1, 64) || !boundedString(c.promptVersion, 1, 64) || c.schemaVersion !== CONTRACT_SCHEMA_VERSION || Boolean(c.failureReason) === Boolean(c.draft)) throw new Error("Invalid generation submit payload.");
  if (c.failureReason !== undefined && !boundedString(c.failureReason, 1, 500)) throw new Error("Invalid generation failure reason.");
  if (c.draft && (!boundedString(c.draft.title, 1, 300) || !boundedString(c.draft.excerpt, 1, 1_000) || !boundedString(c.draft.contentMarkdown, 1, 100_000) || !Array.isArray(c.draft.citationSnapshotIds) || c.draft.citationSnapshotIds.length < 1 || c.draft.citationSnapshotIds.length > 100 || !c.draft.citationSnapshotIds.every(positiveInteger) || new Set(c.draft.citationSnapshotIds).size !== c.draft.citationSnapshotIds.length)) throw new Error("Invalid generation draft payload.");
  const { payloadDigest, ...unsigned } = c as GenerationSubmitRequest;
  if (terminalPayloadDigest(unsigned) !== payloadDigest) throw new Error("Generation terminal payload digest is invalid.");
}

function withDigest(request: Omit<GenerationSubmitRequest, "payloadDigest">): GenerationSubmitRequest { return { ...request, payloadDigest: terminalPayloadDigest(request) }; }
async function retrySameSubmission(client: BackendGenerationClient, jobId: number, request: GenerationSubmitRequest, deadlineMs: number, leaseDeadline: () => number, heartbeatFailure: () => unknown): Promise<void> {
  const shutdownDeadline = performance.now() + deadlineMs; let delay = 250;
  while (true) {
    const heartbeatError = heartbeatFailure();
    if (heartbeatError) throw asError(heartbeatError);
    const remaining = Math.min(shutdownDeadline, leaseDeadline()) - performance.now();
    if (remaining <= 0) throw new BackendOperationError("submit", null, "lease-lost", "Terminal submission exceeded the safe lease deadline.");
    try { await client.submit(jobId, request, AbortSignal.timeout(Math.max(1, Math.floor(remaining)))); return; }
    catch (error) { if (!(error instanceof BackendOperationError) || error.kind !== "transient" || delay >= remaining) throw error; await abortableSleep(delay); delay = Math.min(delay * 2, 2_000); }
  }
}
function classify(operation: "claim" | "heartbeat" | "submit", status: number): BackendOperationError {
  if (operation === "heartbeat" && (status === 404 || status === 409)) return new BackendOperationError(operation, status, "lease-lost", "Generation lease was lost.");
  if (operation === "submit" && status === 409) return new BackendOperationError(operation, status, "conflict", "Terminal submission conflicts with the committed result.");
  if (status === 401 || status === 403 || (operation === "claim" && status === 400)) return new BackendOperationError(operation, status, "fatal", `${operation} was rejected.`);
  if (status === 429 || status >= 500) return new BackendOperationError(operation, status, "transient", `${operation} is temporarily unavailable.`);
  return new BackendOperationError(operation, status, "fatal", `${operation} failed.`);
}
function deadlineFromLease(leaseExpiresAt: string, serverTime: string, elapsedMs: number, marginMs: number): number { return performance.now() + Math.max(0, Date.parse(leaseExpiresAt) - Date.parse(serverTime) - elapsedMs - marginMs); }
function validInstant(value: unknown): value is string { return typeof value === "string" && Number.isFinite(Date.parse(value)) && /(?:Z|[+-]\d\d:\d\d)$/u.test(value); }
function positiveInteger(value: unknown): value is number { return Number.isSafeInteger(value) && (value as number) > 0; }
function boundedString(value: unknown, minimum: number, maximum: number): value is string { return typeof value === "string" && value.length >= minimum && value.length <= maximum; }
function validSnapshot(value: unknown): value is GenerationSnapshot {
  if (!value || typeof value !== "object") return false;
  const s = value as Partial<GenerationSnapshot>;
  try {
    if (!boundedString(s.sourceUrl, 1, 2048) || !boundedString(s.canonicalUrl, 1, 2048)) return false;
    new URL(s.sourceUrl); new URL(s.canonicalUrl);
  } catch { return false; }
  return positiveInteger(s.snapshotId)
    && (s.title === null || boundedString(s.title, 0, 300))
    && boundedString(s.originHost, 1, 253)
    && (s.bodyExcerpt === null || boundedString(s.bodyExcerpt, 0, 50_000))
    && typeof s.contentHash === "string" && /^[0-9a-f]{64}$/u.test(s.contentHash)
    && validInstant(s.retrievedAt);
}
function safeFailureReason(error: unknown): string { return (error instanceof Error ? error.name : "WorkerFailure").slice(0, 120); }
export function abortableSleep(ms: number, signal?: AbortSignal): Promise<void> { return new Promise((resolve, reject) => { if (signal?.aborted) { reject(asError(signal.reason)); return; } const timer = setTimeout(resolve, ms); signal?.addEventListener("abort", () => { clearTimeout(timer); reject(asError(signal.reason)); }, { once: true }); }); }
function asError(value: unknown): Error { return value instanceof Error ? value : new Error("Operation aborted."); }
function toGenerationRequest(claim: GenerationClaimResponse): GenerationRequest { return { jobId: claim.jobKey, runId: claim.runId, topicId: claim.topicId, promptVersion: claim.promptVersion, schemaVersion: claim.schemaVersion, snapshots: claim.snapshots, prompt: claim.prompt }; }
