import { describe, expect, it } from "vitest";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { loadWorkerConfig } from "./config.js";

const requiredEnvironment = {
  NODE_ENV: "test",
  GENERATION_BACKEND_BASE_URL: "https://backend.example.test/",
  GENERATION_WORKER_TOKEN: "worker-secret-value",
  GENERATION_WORKER_ID: "worker-test",
  CODEX_API_KEY: "codex-secret-value",
  GENERATION_CODEX_ENV_ISOLATION_APPROVED: "true",
} satisfies NodeJS.ProcessEnv;

const openAiEnvironment = {
  ...requiredEnvironment,
  GENERATION_PROVIDER: "openai-responses",
  OPENAI_API_KEY: "openai-secret-value",
  GENERATION_OPENAI_RESPONSES_MODEL: "gpt-test-model",
} satisfies NodeJS.ProcessEnv;

describe("worker configuration", () => {
  it("loads required values, strips a trailing base URL slash, and applies bounded defaults", () => {
    const config = loadWorkerConfig(requiredEnvironment);

    expect(config).toMatchObject({
      backendBaseUrl: "https://backend.example.test",
      workerId: "worker-test",
      provider: "codex-sdk",
      pollIntervalMs: 5_000,
      errorBackoffMinMs: 1_000,
      errorBackoffMaxMs: 30_000,
      requestTimeoutMs: 10_000,
      heartbeatIntervalMs: 30_000,
      generationTimeoutMs: 240_000,
      leaseSafetyMarginMs: 45_000,
      shutdownGraceMs: 20_000,
    });
  });

  it.each([
    "GENERATION_BACKEND_BASE_URL",
    "GENERATION_WORKER_TOKEN",
    "CODEX_API_KEY",
  ])("rejects a missing required value without exposing another secret: %s", (missingName) => {
    const environment: NodeJS.ProcessEnv = { ...requiredEnvironment };
    delete environment[missingName];

    expect(() => loadWorkerConfig(environment)).toThrow(missingName);
    try {
      loadWorkerConfig(environment);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      expect(message).not.toContain("worker-secret-value");
      expect(message).not.toContain("codex-secret-value");
    }
  });

  it.each(["0", "-1", "1.5", "NaN", "Infinity"])(
    "rejects an invalid positive integer duration: %s",
    (duration) => {
      expect(() =>
        loadWorkerConfig({
          ...requiredEnvironment,
          GENERATION_REQUEST_TIMEOUT_MS: duration,
        }),
      ).toThrow("GENERATION_REQUEST_TIMEOUT_MS");
    },
  );

  it("rejects an inverted retry range", () => {
    expect(() =>
      loadWorkerConfig({
        ...requiredEnvironment,
        GENERATION_ERROR_BACKOFF_MIN_MS: "2000",
        GENERATION_ERROR_BACKOFF_MAX_MS: "1000",
      }),
    ).toThrow("maximum must be at least its minimum");
  });

  it("allows heartbeat cadence to be shorter than the lease safety margin", () => {
    expect(() => loadWorkerConfig({
      ...requiredEnvironment,
      GENERATION_HEARTBEAT_INTERVAL_MS: "5000",
      GENERATION_LEASE_SAFETY_MARGIN_MS: "10000",
    })).not.toThrow();
  });

  it("rejects a lease safety margin that does not exceed the heartbeat interval", () => {
    expect(() => loadWorkerConfig({
      ...requiredEnvironment,
      GENERATION_HEARTBEAT_INTERVAL_MS: "10000",
      GENERATION_LEASE_SAFETY_MARGIN_MS: "10000",
    })).toThrow("must be greater");
  });

  it("keeps the Codex production adapter frozen until isolation is explicitly approved", () => {
    expect(() =>
      loadWorkerConfig({
        ...requiredEnvironment,
        GENERATION_CODEX_ENV_ISOLATION_APPROVED: "false",
      }),
    ).toThrow("frozen by CR-002");
  });

  it("selects the tool-free OpenAI Responses provider only when explicitly configured", () => {
    const config = loadWorkerConfig(openAiEnvironment);

    expect(config).toMatchObject({
      provider: "openai-responses",
      openaiApiKey: "openai-secret-value",
      openaiResponsesModel: "gpt-test-model",
      codexApiKey: "",
    });
  });

  it("allows the explicitly selected fake provider for local and disposable recovery runs", () => {
    const config = loadWorkerConfig({
      ...requiredEnvironment,
      NODE_ENV: "development",
      GENERATION_PROVIDER: "fake",
      CODEX_API_KEY: "",
    });

    expect(config.provider).toBe("fake-provider");
  });

  it.each(["OPENAI_API_KEY", "GENERATION_OPENAI_RESPONSES_MODEL"])(
    "rejects missing OpenAI Responses configuration without exposing secrets: %s",
    (missingName) => {
      const environment: NodeJS.ProcessEnv = { ...openAiEnvironment };
      delete environment[missingName];

      expect(() => loadWorkerConfig(environment)).toThrow(missingName);
      try {
        loadWorkerConfig(environment);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        expect(message).not.toContain("openai-secret-value");
        expect(message).not.toContain("worker-secret-value");
      }
    },
  );

  it("keeps production frozen even when a legacy v1 attestation repeats the baked digest", () => {
    const directory = mkdtempSync(join(tmpdir(), "gtublog-attestation-"));
    const artifactPath = join(directory, "ARTIFACT_DIGEST");
    const attestationPath = join(directory, "attestation.json");
    writeFileSync(artifactPath, `${"a".repeat(64)}\n`, "utf8");
    writeFileSync(attestationPath, JSON.stringify({ version: 1, result: "passed", artifactDigest: "b".repeat(64) }), "utf8");
    const productionEnvironment = {
      ...requiredEnvironment,
      NODE_ENV: "production",
      GENERATION_CODEX_CANARY_ATTESTATION_PATH: attestationPath,
      GENERATION_ARTIFACT_DIGEST_PATH: artifactPath,
    };

    expect(() => loadWorkerConfig(productionEnvironment)).toThrow("frozen by CR-002");
    writeFileSync(attestationPath, JSON.stringify({ version: 1, result: "passed", artifactDigest: "a".repeat(64) }), "utf8");
    expect(() => loadWorkerConfig(productionEnvironment)).toThrow("frozen by CR-002");
  });
});
