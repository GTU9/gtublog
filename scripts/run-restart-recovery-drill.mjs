import { createHash, randomUUID } from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import http from "node:http";
import net from "node:net";
import process from "node:process";

const windows = process.platform === "win32";
const suffix = randomUUID().slice(0, 8);
const mysqlImage = "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
const containerName = `gtublog-restart-drill-${suffix}`;
const mysqlPort = process.env.RESTART_E2E_MYSQL_PORT ?? "15306";
const backendPort = process.env.RESTART_E2E_BACKEND_PORT ?? "18280";
const frontendPort = process.env.RESTART_E2E_FRONTEND_PORT ?? "13201";
const helperPort = process.env.RESTART_E2E_HELPER_PORT ?? "19191";
const workerToken = "restart-drill-worker-token";
const revalidateSecret = "restart-drill-revalidate-secret";
const baseUrl = `http://127.0.0.1:${backendPort}`;
const helperBaseUrl = `http://127.0.0.1:${helperPort}`;
const helperLocalhostUrl = `http://localhost:${helperPort}`;
const backendEnvironment = {
  ...process.env,
  E2E_MYSQL_PORT: mysqlPort,
  E2E_BACKEND_PORT: backendPort,
  E2E_FRONTEND_PORT: frontendPort,
  AUTOMATION_COLLECTION_ALLOWED_PRIVATE_HOSTS: "localhost,127.0.0.1",
  AUTOMATION_WORKER_SHARED_TOKEN: workerToken,
  AUTOMATION_WORKER_PROVIDER: "fake-provider",
  AUTOMATION_REVALIDATION_BASE_URL: helperBaseUrl,
  AUTOMATION_REVALIDATION_SHARED_SECRET: revalidateSecret,
  AUTOMATION_REVALIDATION_RETRY_DELAY: "PT2S",
  AUTOMATION_RUN_RECOVERY_INTERVAL: "PT5S",
};

let helperMode = "fail";
let backend;

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: process.cwd(),
    env: options.env ?? process.env,
    encoding: options.encoding ?? "utf8",
    stdio: options.stdio ?? "pipe",
    input: options.input,
    shell: options.shell ?? (windows && /\.(?:cmd|bat)$/i.test(command)),
  });
  if (result.error) {
    throw result.error;
  }
  return result;
}

function runDocker(args, options = {}) {
  return run("docker", args, options);
}

function parseHostPort(name, value) {
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`${name} must be an integer between 1 and 65535, but was ${JSON.stringify(value)}.`);
  }
  return port;
}

async function assertHostPortsAvailable(ports) {
  const configuredPorts = new Map();
  for (const { name, value } of ports) {
    const port = parseHostPort(name, value);
    const existingName = configuredPorts.get(port);
    if (existingName) {
      throw new Error(`${name} and ${existingName} both use host port ${port}. Configure distinct ports before starting the restart recovery drill.`);
    }
    configuredPorts.set(port, name);
  }

  await Promise.all([...configuredPorts].map(([port, name]) => new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", (error) => {
      reject(new Error(
        `${name} cannot bind 127.0.0.1:${port} for the restart recovery drill (${error.code ?? error.message}). Stop the conflicting process or configure a different port.`,
      ));
    });
    server.listen({ host: "127.0.0.1", port }, () => {
      server.close((error) => {
        if (error) {
          reject(new Error(`Unable to release restart recovery preflight port 127.0.0.1:${port}: ${error.message}`));
          return;
        }
        resolve();
      });
    });
  })));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function startMysqlContainer() {
  const started = runDocker([
    "run",
    "--detach",
    "--rm",
    "--name",
    containerName,
    "--publish",
    `127.0.0.1:${mysqlPort}:3306`,
    "--env",
    "MYSQL_DATABASE=gtublog_e2e",
    "--env",
    "MYSQL_USER=gtublog_e2e",
    "--env",
    "MYSQL_PASSWORD=gtublog-e2e-password",
    "--env",
    "MYSQL_ROOT_PASSWORD=gtublog-e2e-root-password",
    mysqlImage,
  ], { stdio: "inherit" });
  if (started.status !== 0) {
    throw new Error("Unable to start the restart-drill MySQL container.");
  }
}

async function waitForMysql() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const probe = runDocker(
      ["exec", containerName, "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-pgtublog-e2e-root-password", "--silent"],
      { stdio: "ignore" },
    );
    if (probe.status === 0) {
      return;
    }
    await sleep(1000);
  }
  throw new Error("Restart-drill MySQL did not become healthy within 60 seconds.");
}

function removeMysqlContainer() {
  runDocker(["rm", "-f", containerName], { stdio: "ignore" });
}

function terminateProcessTree(child, signal = "SIGTERM") {
  if (!child || child.exitCode !== null) return;
  if (windows) {
    spawnSync("taskkill", ["/pid", String(child.pid), "/t", "/f"], { stdio: "ignore" });
  } else {
    try {
      process.kill(-child.pid, signal);
    } catch {}
  }
}

function waitForExit(child) {
  return new Promise((resolve) => {
    if (!child) {
      resolve({ code: 0, signal: null });
      return;
    }
    child.once("exit", (code, signal) => resolve({ code, signal }));
  });
}

function startBackend() {
  const command = windows ? "node.exe" : "node";
  const args = ["scripts/start-fullstack-backend.mjs"];
  backend = spawn(command, args, {
    cwd: process.cwd(),
    env: backendEnvironment,
    stdio: "inherit",
    detached: !windows,
  });
  return backend;
}

async function restartBackend() {
  terminateProcessTree(backend);
  await waitForExit(backend);
  startBackend();
  await waitForBackend();
}

async function waitForBackend() {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/actuator/health/readiness`);
      if (response.ok) {
        return;
      }
    } catch {}
    await sleep(1000);
  }
  throw new Error("Backend readiness probe did not become healthy in time.");
}

function startHelperServer() {
  const server = http.createServer(async (request, response) => {
    if (request.method === "GET" && request.url === "/source") {
      const host = request.headers.host ?? "";
      const canonicalUrl = host.startsWith("localhost:")
        ? "https://example.com/restart-drill-source"
        : "https://example.org/restart-drill-source";
      response.writeHead(200, {
        "Content-Type": "text/html; charset=utf-8",
        ETag: "\"restart-drill-source\"",
        "Last-Modified": "Fri, 03 Jul 2026 00:00:00 GMT",
      });
      response.end(`
        <html>
          <head>
            <title>Restart drill source</title>
            <link rel="canonical" href="${canonicalUrl}" />
          </head>
          <body>
            <article><p>Restart drill automation source content.</p></article>
          </body>
        </html>
      `);
      return;
    }

    if (request.method === "POST" && request.url === "/api/revalidate") {
      if (request.headers["x-revalidate-secret"] !== revalidateSecret) {
        response.writeHead(403, { "Content-Type": "application/json" });
        response.end(JSON.stringify({ ok: false, reason: "bad-secret" }));
        return;
      }
      for await (const _chunk of request) {
        // drain body
      }
      if (helperMode === "fail") {
        response.writeHead(503, { "Content-Type": "application/json" });
        response.end(JSON.stringify({ ok: false, mode: helperMode }));
        return;
      }
      response.writeHead(200, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ ok: true, mode: helperMode }));
      return;
    }

    response.writeHead(404, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ ok: false, path: request.url }));
  });

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(Number(helperPort), "0.0.0.0", () => resolve(server));
  });
}

async function api(path, { method = "GET", token, body, headers = {} } = {}) {
  const requestHeaders = {
    Accept: "application/json",
    ...headers,
  };
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
  }
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = text;
    }
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with ${response.status}: ${typeof json === "string" ? json : JSON.stringify(json)}`);
  }
  return json;
}

async function login() {
  const session = await api("/api/v1/auth/login", {
    method: "POST",
    body: { username: "admin", password: "admin-test-password" },
  });
  return session.accessToken;
}

function normalize(value) {
  return value.normalize("NFC").replace(/\r\n?/gu, "\n");
}

function terminalPayloadDigest(requestWithoutDigest) {
  const draft = requestWithoutDigest.draft
    ? {
        title: normalize(requestWithoutDigest.draft.title),
        excerpt: normalize(requestWithoutDigest.draft.excerpt),
        contentMarkdown: normalize(requestWithoutDigest.draft.contentMarkdown),
        citationSnapshotIds: [...requestWithoutDigest.draft.citationSnapshotIds].sort((a, b) => a - b),
      }
    : null;
  const canonical = JSON.stringify({
    terminalSubmissionId: requestWithoutDigest.terminalSubmissionId,
    workerId: requestWithoutDigest.workerId,
    providerName: requestWithoutDigest.providerName,
    promptVersion: requestWithoutDigest.promptVersion,
    schemaVersion: requestWithoutDigest.schemaVersion,
    draft,
    failureReason: requestWithoutDigest.failureReason ? normalize(requestWithoutDigest.failureReason) : null,
  });
  return createHash("sha256").update(canonical, "utf8").digest("hex");
}

async function createPublishedAutomationRun(token) {
  const slugSuffix = Date.now().toString(36);
  const expectedTitle = `Restart Drill Published Post ${slugSuffix}`;
  const topic = await api("/api/v1/admin/automation/topics", {
    method: "POST",
    token,
    body: {
      name: `Restart Drill Publish Topic ${slugSuffix}`,
      slug: `restart-drill-publish-topic-${slugSuffix}`,
      promptTemplateVersion: "prompt-v1",
      publicationEnabled: true,
    },
  });

  await api(`/api/v1/admin/automation/topics/${topic.id}/sources`, {
    method: "POST",
    token,
    body: {
      sourceType: "HTML",
      sourceUrl: `${helperLocalhostUrl}/source`,
      enabled: true,
    },
  });

  await api(`/api/v1/admin/automation/topics/${topic.id}/sources`, {
    method: "POST",
    token,
    body: {
      sourceType: "HTML",
      sourceUrl: `${helperBaseUrl}/source`,
      enabled: true,
    },
  });

  const run = await api(`/api/v1/admin/automation/topics/${topic.id}/runs/manual`, {
    method: "POST",
    token,
    body: { idempotencyKey: `restart-publish-run-${slugSuffix}` },
  });

  const claim = await claimGenerationJob(run.id);
  if (claim.providerName !== "fake-provider") {
    throw new Error(`Expected claimed provider fake-provider but got ${claim.providerName}.`);
  }
  if (!Array.isArray(claim.snapshots) || claim.snapshots.length < 2) {
    throw new Error("Expected two collected snapshots for the publish drill run.");
  }

  const requestWithoutDigest = {
    terminalSubmissionId: randomUUID(),
    workerId: "restart-drill-worker",
    providerName: "fake-provider",
    promptVersion: claim.promptVersion,
    schemaVersion: claim.schemaVersion,
    draft: {
      title: expectedTitle,
      excerpt: "Published during the restart recovery drill.",
      contentMarkdown: `# ${expectedTitle}\n\nRestart drill content.`,
      citationSnapshotIds: claim.snapshots.map((snapshot) => snapshot.snapshotId),
    },
    failureReason: null,
  };
  const submitBody = {
    ...requestWithoutDigest,
    payloadDigest: terminalPayloadDigest(requestWithoutDigest),
  };

  const submitResponse = await fetch(`${baseUrl}/api/v2/internal/generation-jobs/${claim.jobId}/submit`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Worker-Token": workerToken,
    },
    body: JSON.stringify(submitBody),
  });
  if (submitResponse.status !== 202) {
    throw new Error(`Worker submit failed with ${submitResponse.status}: ${await submitResponse.text()}`);
  }

  const detail = await api(`/api/v1/admin/automation/runs/${run.id}`, { token });
  if (detail.run.status !== "SUCCEEDED") {
    throw new Error(`Expected publish drill run to succeed but got ${detail.run.status}.`);
  }

  const publishedSlug = executeSql(
    `SELECT slug FROM post WHERE title = '${expectedTitle.replace(/'/g, "''")}' ORDER BY id DESC LIMIT 1`,
  );
  if (!publishedSlug) {
    throw new Error("Expected an automatically published post slug but none was stored.");
  }

  return await api(`/api/v1/public/posts/${publishedSlug}`);
}

async function waitForPendingOutbox(token, minimum = 1) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const outbox = await api("/api/v1/admin/automation/outbox", { token });
    if (outbox.length >= minimum) {
      return outbox;
    }
    await sleep(500);
  }
  throw new Error("Expected pending outbox events were not observed.");
}

async function waitForOutboxToDrain(token) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const outbox = await api("/api/v1/admin/automation/outbox", { token });
    if (outbox.length === 0) {
      return;
    }
    await sleep(1000);
  }
  throw new Error("Pending outbox events were not drained after replay.");
}

async function createRecoverableAutomationRun(token) {
  const slugSuffix = Date.now().toString(36);
  const topic = await api("/api/v1/admin/automation/topics", {
    method: "POST",
    token,
    body: {
      name: `Restart Drill Topic ${slugSuffix}`,
      slug: `restart-drill-topic-${slugSuffix}`,
      promptTemplateVersion: "v1",
      publicationEnabled: true,
    },
  });
  await api(`/api/v1/admin/automation/topics/${topic.id}/sources`, {
    method: "POST",
    token,
    body: {
      sourceType: "HTML",
      sourceUrl: `${helperBaseUrl}/source`,
      enabled: true,
    },
  });
  return await api(`/api/v1/admin/automation/topics/${topic.id}/runs/manual`, {
    method: "POST",
    token,
    body: { idempotencyKey: `restart-run-${slugSuffix}` },
  });
}

async function claimGenerationJob(runId) {
  const response = await fetch(`${baseUrl}/api/v2/internal/generation-jobs/claim`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Worker-Token": workerToken,
    },
    body: JSON.stringify({
      workerId: "restart-drill-worker",
      supportedProviders: ["codex-sdk", "fake-provider"],
      supportedSchemaVersions: ["automation-job-v2"],
    }),
  });
  if (response.status !== 200) {
    throw new Error(`Worker claim failed with ${response.status}.`);
  }
  const claim = await response.json();
  if (claim.runId !== runId) {
    throw new Error(`Claimed run ${claim.runId} did not match expected run ${runId}.`);
  }
  return claim;
}

function executeSql(sql) {
  const result = runDocker(
    ["exec", containerName, "mysql", "-N", "-uroot", "-pgtublog-e2e-root-password", "-e", sql, "gtublog_e2e"],
    {},
  );
  if (result.status !== 0) {
    throw new Error(`SQL failed: ${sql}`);
  }
  return result.stdout.trim();
}

async function waitForRecoveredRun(token, runId) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const detail = await api(`/api/v1/admin/automation/runs/${runId}`, { token });
    if (detail.run.status === "FAILED") {
      return detail;
    }
    await sleep(1000);
  }
  throw new Error("Expired automation run was not recovered to FAILED.");
}

await assertHostPortsAvailable([
  { name: "RESTART_E2E_MYSQL_PORT", value: mysqlPort },
  { name: "RESTART_E2E_BACKEND_PORT", value: backendPort },
  { name: "RESTART_E2E_FRONTEND_PORT", value: frontendPort },
  { name: "RESTART_E2E_HELPER_PORT", value: helperPort },
]);

try {
  startMysqlContainer();
  await waitForMysql();
  const helperServer = await startHelperServer();

  for (const signal of ["SIGINT", "SIGTERM"]) {
    process.once(signal, () => {
      terminateProcessTree(backend);
      helperServer.close();
      removeMysqlContainer();
      process.exitCode = 1;
    });
  }

  startBackend();
  await waitForBackend();

  let token = await login();
  const publicPost = await createPublishedAutomationRun(token);
  if (!publicPost.title.startsWith("Restart Drill Published Post ")) {
    throw new Error("Published post was not readable through the public API before restart.");
  }

  const pendingBeforeRestart = await waitForPendingOutbox(token);
  if (!pendingBeforeRestart.every((event) => event.deliveryStatus === "PENDING")) {
    throw new Error("Expected all restart-drill outbox events to remain pending before replay.");
  }

  await restartBackend();
  token = await login();
  const pendingAfterRestart = await waitForPendingOutbox(token);
  if (pendingAfterRestart.length < pendingBeforeRestart.length) {
    throw new Error("Pending outbox events disappeared across backend restart.");
  }

  helperMode = "success";
  await api("/api/v1/admin/automation/outbox/process", { method: "POST", token });
  await waitForOutboxToDrain(token);

  const run = await createRecoverableAutomationRun(token);
  if (run.status !== "RUNNING") {
    throw new Error(`Expected automation run to start as RUNNING but got ${run.status}.`);
  }

  const claim = await claimGenerationJob(run.id);
  if (claim.providerName !== "fake-provider") {
    throw new Error(`Expected claimed provider fake-provider but got ${claim.providerName}.`);
  }

  executeSql(`UPDATE automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE WHERE id = ${run.id}`);
  const expiredRunState = executeSql(
    `SELECT CONCAT(status, '|', COALESCE(lease_owner, 'null'), '|', COALESCE(DATE_FORMAT(lease_expires_at, '%Y-%m-%dT%H:%i:%s.%f'), 'null')) FROM automation_run WHERE id = ${run.id}`,
  );
  const expiredCandidates = executeSql(
    `SELECT COUNT(*) FROM automation_run WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at <= UTC_TIMESTAMP(6)`,
  );
  const recovery = await api(`/api/v1/admin/automation/runs/${run.id}/recovery`, { method: "POST", token });
  if (!recovery || recovery.recovered !== true) {
    throw new Error(
      `Expected at least one recovered run but got ${JSON.stringify(recovery)}. run=${expiredRunState} expiredCandidates=${expiredCandidates}`,
    );
  }
  await waitForRecoveredRun(token, run.id);

  const jobStatus = executeSql(`SELECT job_status FROM generation_job WHERE run_id = ${run.id}`);
  if (jobStatus !== "CANCELLED") {
    throw new Error(`Expected generation job to be CANCELLED after recovery but found ${jobStatus}.`);
  }

  const auditCount = executeSql(
    `SELECT COUNT(*) FROM audit_entry WHERE target_id = '${run.id}' AND action_type = 'AUTOMATION_RUN_RECOVERED_AS_FAILED'`,
  );
  if (auditCount !== "1") {
    throw new Error(`Expected one recovery audit event but found ${auditCount}.`);
  }

  const diagnostics = await api("/api/v1/admin/automation/diagnostics", { token });
  if (!diagnostics || !diagnostics.runCounts) {
    throw new Error("Automation diagnostics were not available after restart and recovery drill.");
  }

  helperServer.close();
} finally {
  terminateProcessTree(backend);
  removeMysqlContainer();
}
