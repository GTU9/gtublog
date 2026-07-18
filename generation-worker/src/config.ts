import { hostname } from "node:os";

export interface WorkerConfig {
  readonly backendBaseUrl: string;
  readonly workerToken: string;
  readonly workerId: string;
  readonly provider: "codex-sdk" | "openai-responses" | "fake-provider";
  readonly codexApiKey: string;
  readonly openaiApiKey: string;
  readonly openaiResponsesModel: string;
  readonly codexEnvIsolationApproved: boolean;
  readonly pollIntervalMs: number;
  readonly errorBackoffMinMs: number;
  readonly errorBackoffMaxMs: number;
  readonly requestTimeoutMs: number;
  readonly heartbeatIntervalMs: number;
  readonly generationTimeoutMs: number;
  readonly leaseSafetyMarginMs: number;
  readonly shutdownGraceMs: number;
}

export function loadWorkerConfig(env: NodeJS.ProcessEnv = process.env): WorkerConfig {
  const required = (name: string): string => {
    const value = env[name]?.trim();
    if (!value) throw new Error(`Required configuration ${name} is missing.`);
    return value;
  };
  const duration = (name: string, fallback: number): number => {
    const raw = env[name];
    if (raw === undefined) return fallback;
    const value = Number(raw);
    if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer.`);
    return value;
  };
  const provider = env.GENERATION_PROVIDER === "fake"
    ? "fake-provider"
    : env.GENERATION_PROVIDER === "openai-responses"
      ? "openai-responses"
      : "codex-sdk";
  const isolationApproved = env.NODE_ENV === "test"
    ? env.GENERATION_CODEX_ENV_ISOLATION_APPROVED === "true"
    : validCanaryAttestation();
  const config: WorkerConfig = {
    backendBaseUrl: required("GENERATION_BACKEND_BASE_URL").replace(/\/$/u, ""),
    workerToken: required("GENERATION_WORKER_TOKEN"),
    workerId: env.GENERATION_WORKER_ID?.trim() || `${hostname()}-${process.pid.toString()}`,
    provider,
    codexApiKey: provider === "codex-sdk" ? required("CODEX_API_KEY") : "",
    openaiApiKey: provider === "openai-responses" ? required("OPENAI_API_KEY") : "",
    openaiResponsesModel: provider === "openai-responses" ? required("GENERATION_OPENAI_RESPONSES_MODEL") : "",
    codexEnvIsolationApproved: isolationApproved,
    pollIntervalMs: duration("GENERATION_POLL_INTERVAL_MS", 5_000),
    errorBackoffMinMs: duration("GENERATION_ERROR_BACKOFF_MIN_MS", 1_000),
    errorBackoffMaxMs: duration("GENERATION_ERROR_BACKOFF_MAX_MS", 30_000),
    requestTimeoutMs: duration("GENERATION_REQUEST_TIMEOUT_MS", 10_000),
    heartbeatIntervalMs: duration("GENERATION_HEARTBEAT_INTERVAL_MS", 30_000),
    generationTimeoutMs: duration("GENERATION_TIMEOUT_MS", 240_000),
    leaseSafetyMarginMs: duration("GENERATION_LEASE_SAFETY_MARGIN_MS", 45_000),
    shutdownGraceMs: duration("GENERATION_SHUTDOWN_GRACE_MS", 20_000),
  };
  if (config.errorBackoffMaxMs < config.errorBackoffMinMs) throw new Error("Error backoff maximum must be at least its minimum.");
  if (config.leaseSafetyMarginMs <= config.heartbeatIntervalMs) throw new Error("GENERATION_LEASE_SAFETY_MARGIN_MS must be greater than GENERATION_HEARTBEAT_INTERVAL_MS.");
  if (provider === "codex-sdk" && !config.codexEnvIsolationApproved) {
    throw new Error("Codex production adapter is frozen by CR-002 until signed attestation and credential-boundary support are implemented.");
  }
  return config;
}

function validCanaryAttestation(): boolean {
  // v1 only proves that a host-mounted JSON repeats the image digest. It has no
  // independent issuer, signature, freshness, or restart-probe evidence, so it
  // must never release the production Codex adapter (CR-002).
  return false;
}
