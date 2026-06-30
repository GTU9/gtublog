#!/usr/bin/env node
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createCodexGenerationProvider } from "./codex-provider.js";
import { createFakeGenerationProvider } from "./fake-provider.js";
import { loadWorkerConfig } from "./config.js";
import { createWorkerHealthState } from "./health.js";
import { exitCodeForError } from "./exit-code.js";
import { abortableSleep, BackendOperationError, createBackendGenerationClient, createWorkerRuntime } from "./runtime.js";

async function main(): Promise<number> {
  const once = process.argv.includes("--once");
  let config;
  try { config = loadWorkerConfig(); } catch (error) { lifecycle("configuration_rejected", { reason: error instanceof Error ? error.message : "invalid configuration" }); return 2; }
  const shutdown = new AbortController();
  const health = createWorkerHealthState(new Date(), process.env.GENERATION_HEALTH_FILE);
  process.once("SIGINT", () => { health.beginShutdown(); shutdown.abort(new Error("SIGINT")); });
  process.once("SIGTERM", () => { health.beginShutdown(); shutdown.abort(new Error("SIGTERM")); });
  const isolatedRoot = await mkdtemp(join(tmpdir(), "gtublog-worker-"));
  const providerEnv = isolatedEnvironment(isolatedRoot, config.codexApiKey);
  const provider = config.provider === "fake-provider"
    ? createFakeGenerationProvider()
    : createCodexGenerationProvider({ workingDirectory: isolatedRoot, apiKey: config.codexApiKey, env: providerEnv });
  const client = createBackendGenerationClient({
    baseUrl: config.backendBaseUrl,
    token: config.workerToken,
    requestTimeoutMs: config.requestTimeoutMs,
    onBackendContact: ({ operation, status, at }) => {
      const contact = health.recordBackendContact(new Date(at));
      lifecycle("backend_contact", {
        workerId: config.workerId,
        operation,
        status,
        at: contact.lastBackendContactAt ?? contact.startedAt,
      });
    },
  });
  const runtime = createWorkerRuntime({ client, provider, heartbeatIntervalMs: config.heartbeatIntervalMs, generationTimeoutMs: config.generationTimeoutMs, leaseSafetyMarginMs: config.leaseSafetyMarginMs, shutdownGraceMs: config.shutdownGraceMs });
  let backoff = config.errorBackoffMinMs;
  try {
    lifecycle("worker_started", { workerId: config.workerId, mode: once ? "once" : "poll" });
    while (!shutdown.signal.aborted) {
      try {
        const claim = await runtime.runOnce(config.workerId, shutdown.signal);
        if (claim) lifecycle("job_completed", { workerId: config.workerId, jobId: claim.jobId });
        backoff = config.errorBackoffMinMs;
        if (once) return 0;
        await abortableSleep(claim ? 1 : jitter(config.workerId, config.pollIntervalMs), shutdown.signal);
      } catch (error) {
        if (shutdown.signal.aborted) return 4;
        const fatal = error instanceof BackendOperationError && (error.kind === "fatal" || error.kind === "conflict");
        lifecycle("worker_operation_failed", { workerId: config.workerId, category: error instanceof BackendOperationError ? error.kind : "provider" });
        if (once) return exitCodeForError(error);
        if (fatal) return 2;
        await abortableSleep(jitter(config.workerId, backoff), shutdown.signal);
        backoff = Math.min(backoff * 2, config.errorBackoffMaxMs);
      }
    }
    return 4;
  } finally {
    await rm(isolatedRoot, { recursive: true, force: true });
    lifecycle("worker_stopped", { workerId: config.workerId });
  }
}

function isolatedEnvironment(root: string, apiKey: string): Record<string, string> {
  const env: Record<string, string> = { CODEX_API_KEY: apiKey, HOME: root, USERPROFILE: root, CODEX_HOME: join(root, ".codex"), TEMP: root, TMP: root, TMPDIR: root };
  for (const name of process.platform === "win32" ? ["SystemRoot", "ComSpec", "PATH", "PATHEXT"] : ["PATH", "LANG"]) { const value = process.env[name]; if (value) env[name] = value; }
  return env;
}
function jitter(workerId: string, base: number): number { let hash = 2166136261; for (const char of workerId) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619); return Math.max(1, Math.round(base * (0.8 + ((hash >>> 0) % 401) / 1000))); }
function lifecycle(event: string, fields: Record<string, string | number>): void { process.stdout.write(`${JSON.stringify({ event, ...fields })}\n`); }

main().then((code) => { process.exitCode = code; }).catch(() => { process.exitCode = 3; });
