import { spawnSync } from "node:child_process";
import process from "node:process";

const composeFile = process.env.PROD_SMOKE_COMPOSE_FILE ?? "compose.prod.yaml";
const composeProject = process.env.PROD_SMOKE_PROJECT ?? "gtublog-prod";
const composeEnvFile = process.env.PROD_SMOKE_ENV_FILE;
const baseUrl = new URL(process.env.PROD_SMOKE_BASE_URL ?? "http://127.0.0.1");
const requestTimeoutMs = Number.parseInt(process.env.PROD_SMOKE_REQUEST_TIMEOUT_MS ?? "10000", 10);
const adminUsername = process.env.PROD_SMOKE_ADMIN_USERNAME;
const adminPassword = process.env.PROD_SMOKE_ADMIN_PASSWORD;
const postSlug = process.env.PROD_SMOKE_POST_SLUG;
const expectGenerationWorker = process.env.PROD_SMOKE_EXPECT_GENERATION === "true";

if (!["http:", "https:"].includes(baseUrl.protocol)) {
  throw new Error("PROD_SMOKE_BASE_URL must use http or https.");
}
if (!Number.isSafeInteger(requestTimeoutMs) || requestTimeoutMs <= 0) {
  throw new Error("PROD_SMOKE_REQUEST_TIMEOUT_MS must be a positive integer.");
}
if (Boolean(adminUsername) !== Boolean(adminPassword)) {
  throw new Error("Set both PROD_SMOKE_ADMIN_USERNAME and PROD_SMOKE_ADMIN_PASSWORD, or neither.");
}

function compose(args, options = {}) {
  const environmentArgs = composeEnvFile ? ["--env-file", composeEnvFile] : [];
  const result = spawnSync("docker", ["compose", ...environmentArgs, "-p", composeProject, "-f", composeFile, ...args], {
    cwd: process.cwd(),
    encoding: "utf8",
    stdio: options.stdio ?? "pipe",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`docker compose ${args.join(" ")} failed: ${(result.stderr || result.stdout).trim()}`);
  }
  return result.stdout;
}

async function request(path, options = {}) {
  const url = new URL(path, baseUrl);
  try {
    const response = await fetch(url, {
      redirect: "error",
      headers: { Accept: "application/json, text/html;q=0.9", ...options.headers },
      signal: AbortSignal.timeout(requestTimeoutMs),
      ...options,
    });
    const body = await response.text();
    return { response, body };
  } catch (error) {
    throw new Error(`Request to ${url} failed within ${requestTimeoutMs}ms.`, { cause: error });
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function parseComposeServices() {
  const output = compose(["ps", "--format", "json"]);
  const trimmed = output.trim();
  const rows = !trimmed
    ? []
    : trimmed.startsWith("[")
      ? JSON.parse(trimmed)
      : trimmed.split(/\r?\n/u).filter(Boolean).map((line) => JSON.parse(line));
  const byService = new Map(rows.map((row) => [row.Service, row]));
  const expectedServices = ["proxy", "frontend", "backend"];
  if (expectGenerationWorker) expectedServices.push("generation-worker");
  for (const service of expectedServices) {
    const row = byService.get(service);
    assert(row, `Compose service ${service} is not present.`);
    assert(row.State === "running", `Compose service ${service} is not running (state=${row.State}).`);
    if (["backend", "generation-worker"].includes(service)) {
      assert(row.Health === "healthy", `Compose service ${service} is not healthy (health=${row.Health ?? "none"}).`);
    }
  }
}

async function verifyPublicBoundary() {
  const home = await request("/");
  assert(home.response.ok, `Public homepage returned ${home.response.status}.`);
  assert(home.response.headers.get("content-type")?.includes("text/html"), "Public homepage did not return HTML.");
  assert(home.body.includes("<html"), "Public homepage response is not a rendered HTML document.");

  const posts = await request("/api/v1/public/posts?page=0&size=1");
  assert(posts.response.ok, `Public posts API returned ${posts.response.status}.`);
  const payload = JSON.parse(posts.body);
  assert(Array.isArray(payload.items), "Public posts API did not return a page payload.");

  if (postSlug) {
    const postApi = await request(`/api/v1/public/posts/${encodeURIComponent(postSlug)}`);
    assert(postApi.response.ok, `Public post API returned ${postApi.response.status}.`);
    const postPage = await request(`/posts/${encodeURIComponent(postSlug)}`);
    assert(postPage.response.ok, `Public post page returned ${postPage.response.status}.`);
    assert(postPage.response.headers.get("content-type")?.includes("text/html"), "Public post page did not return HTML.");
  }

  const actuator = await request("/actuator/health");
  assert(actuator.response.status === 404, "Actuator must not be exposed through the public reverse proxy.");
}

async function verifyOptionalAdminLogin() {
  if (!adminUsername) return;
  const login = await request("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: adminUsername, password: adminPassword }),
  });
  assert(login.response.ok, `Administrator login returned ${login.response.status}.`);
  const session = JSON.parse(login.body);
  assert(typeof session.accessToken === "string" && session.accessToken.length > 20, "Administrator login did not return an access token.");

  const currentSession = await request("/api/v1/auth/session", {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  assert(currentSession.response.ok, `Authenticated session check returned ${currentSession.response.status}.`);

  const diagnostics = await request("/api/v1/admin/automation/diagnostics", {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  assert(diagnostics.response.ok, `Automation diagnostics returned ${diagnostics.response.status}.`);

  const outbox = await request("/api/v1/admin/automation/outbox", {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  assert(outbox.response.ok, `Automation outbox returned ${outbox.response.status}.`);
}

function verifyInternalHealth() {
  compose(["exec", "-T", "backend", "wget", "-qO-", "http://127.0.0.1:8080/actuator/health/readiness"]);
  if (expectGenerationWorker) {
    compose(["exec", "-T", "generation-worker", "node", "dist/healthcheck.js"]);
  }
}

compose(["config", "--quiet"]);
parseComposeServices();
await verifyPublicBoundary();
await verifyOptionalAdminLogin();
verifyInternalHealth();
console.log(`Production Compose smoke check passed for ${baseUrl.origin} (project: ${composeProject}).`);
