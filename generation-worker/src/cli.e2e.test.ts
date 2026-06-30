import { spawn, execFileSync } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:http";
import path from "node:path";

import { beforeAll, describe, expect, it } from "vitest";

beforeAll(() => {
  execFileSync(process.execPath, [path.resolve(process.cwd(), "node_modules", "typescript", "bin", "tsc"), "-p", "tsconfig.build.json"], { cwd: process.cwd() });
});

describe("worker CLI", () => {
  it("records a secret-free backend contact and exits successfully when no job exists", async () => {
    const requests: string[] = [];
    const server = createServer((request, response) => {
      requests.push(request.url ?? "");
      response.writeHead(204).end();
    });
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    const address = server.address();
    if (!address || typeof address === "string") throw new Error("Test server did not bind a TCP port.");

    const workerToken = "worker-token-canary";
    const codexApiKey = "codex-api-key-canary";
    try {
      const result = await runCli({
        ...process.env,
        GENERATION_BACKEND_BASE_URL: `http://127.0.0.1:${address.port.toString()}`,
        GENERATION_WORKER_TOKEN: workerToken,
        GENERATION_WORKER_ID: "worker-cli-test",
        GENERATION_CODEX_ENV_ISOLATION_APPROVED: "true",
        CODEX_API_KEY: codexApiKey,
      });

      expect(result.exitCode).toBe(0);
      expect(result.stderr).toBe("");
      expect(requests).toEqual(["/api/v2/internal/generation-jobs/claim"]);
      const events = result.stdout.trim().split("\n").map((line) => JSON.parse(line) as Record<string, unknown>);
      expect(events).toEqual(expect.arrayContaining([
        expect.objectContaining({
          event: "backend_contact",
          workerId: "worker-cli-test",
          operation: "claim",
          status: 204,
        }),
      ]));
      const backendContact = events.find((event) => event.event === "backend_contact");
      expect(Object.keys(backendContact ?? {}).sort()).toEqual(["at", "event", "operation", "status", "workerId"]);
      expect(result.stdout).not.toContain(workerToken);
      expect(result.stdout).not.toContain(codexApiKey);
      expect(result.stdout).not.toContain("prompt");
      expect(result.stdout).not.toContain("contentMarkdown");
    } finally {
      server.close();
      await once(server, "close");
    }
  }, 15_000);

  it("runs the compiled claim-generate-submit success path with the test-only fake provider", async () => {
    const requests: string[] = [];
    const server = createServer((request, response) => {
      const url = request.url ?? "";
      requests.push(url);
      let body = "";
      request.setEncoding("utf8");
      request.on("data", (chunk: string) => { body += chunk; });
      request.on("end", () => {
        response.setHeader("Content-Type", "application/json");
        if (url.endsWith("/claim")) response.end(JSON.stringify({
          jobId: 13, jobKey: "job-13", runId: 130, topicId: 131, leaseOwner: "worker-cli-test",
          leaseExpiresAt: "2099-06-30T12:10:00Z", providerName: "fake-provider", promptVersion: "prompt-v1",
          schemaVersion: "automation-job-v2", prompt: "Create a draft.", snapshots: [{ snapshotId: 1,
            sourceUrl: "https://example.test/source", canonicalUrl: "https://example.test/source", title: "Source",
            originHost: "example.test", bodyExcerpt: "Evidence", contentHash: "a".repeat(64), retrievedAt: "2026-06-30T12:00:00Z" }],
        }));
        else if (url.endsWith("/heartbeat")) response.end(JSON.stringify({ jobId: 13, status: "CLAIMED", serverTime: "2026-06-30T12:00:30Z", leaseExpiresAt: "2099-06-30T12:10:00Z" }));
        else {
          const submitted = JSON.parse(body) as { terminalSubmissionId: string; payloadDigest: string };
          response.statusCode = 202;
          response.end(JSON.stringify({ jobId: 13, status: "SUBMITTED", submittedAt: "2026-06-30T12:01:00Z", terminalSubmissionId: submitted.terminalSubmissionId, payloadDigest: submitted.payloadDigest }));
        }
      });
    });
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    const address = server.address();
    if (!address || typeof address === "string") throw new Error("Test server did not bind a TCP port.");
    try {
      const result = await runCli({
        ...process.env, NODE_ENV: "test", GENERATION_PROVIDER: "fake",
        GENERATION_BACKEND_BASE_URL: `http://127.0.0.1:${address.port.toString()}`,
        GENERATION_WORKER_TOKEN: "worker-token-canary", GENERATION_WORKER_ID: "worker-cli-test",
      });
      expect(result.exitCode).toBe(0);
      expect(requests).toEqual(expect.arrayContaining([
        "/api/v2/internal/generation-jobs/claim",
        "/api/v2/internal/generation-jobs/13/heartbeat",
        "/api/v2/internal/generation-jobs/13/submit",
      ]));
      expect(result.stdout).toContain('"event":"job_completed"');
    } finally {
      server.close();
      await once(server, "close");
    }
  }, 15_000);
});

async function runCli(env: NodeJS.ProcessEnv): Promise<{
  exitCode: number | null;
  stdout: string;
  stderr: string;
}> {
  const child = spawn(process.execPath, [path.resolve(process.cwd(), "dist", "cli.js"), "--once"], {
    cwd: process.cwd(),
    env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = "";
  let stderr = "";
  child.stdout.setEncoding("utf8").on("data", (chunk: string) => { stdout += chunk; });
  child.stderr.setEncoding("utf8").on("data", (chunk: string) => { stderr += chunk; });
  const [exitCode] = await once(child, "exit") as [number | null];
  return { exitCode, stdout, stderr };
}
