import { hostname } from "node:os";
import { readFileSync } from "node:fs";

export interface WorkerConfig {
  readonly backendBaseUrl: string;
  readonly workerToken: string;
  readonly workerId: string;
  readonly provider: "codex-sdk" | "fake-provider";
  readonly codexApiKey: string;
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
  const provider = env.GENERATION_PROVIDER === "fake" && env.NODE_ENV === "test" ? "fake-provider" : "codex-sdk";
  const isolationApproved = env.NODE_ENV === "test"
    ? env.GENERATION_CODEX_ENV_ISOLATION_APPROVED === "true"
    : validCanaryAttestation(env.GENERATION_CODEX_CANARY_ATTESTATION_PATH, "/app/ARTIFACT_DIGEST");
  const config: WorkerConfig = {
    backendBaseUrl: required("GENERATION_BACKEND_BASE_URL").replace(/\/$/u, ""),
    workerToken: required("GENERATION_WORKER_TOKEN"),
    workerId: env.GENERATION_WORKER_ID?.trim() || `${hostname()}-${process.pid.toString()}`,
    provider,
    codexApiKey: provider === "codex-sdk" ? required("CODEX_API_KEY") : "",
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
    throw new Error("Codex production adapter is frozen until a matching container canary attestation is mounted.");
  }
  return config;
}

function validCanaryAttestation(path: string | undefined, artifactDigestPath: string): boolean {
  if (!path?.trim()) return false;
  try {
    const value = JSON.parse(readFileSync(path, "utf8")) as Record<string, unknown>;
    const artifactDigest = readFileSync(artifactDigestPath, "utf8").trim();
    return /^[0-9a-f]{64}$/u.test(artifactDigest)
      && value.version === 1 && value.result === "passed" && value.artifactDigest === artifactDigest;
  } catch { return false; }
}
