import { randomUUID } from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import process from "node:process";

const containerName = `gtublog-e2e-mysql-${randomUUID().slice(0, 8)}`;
const mysqlPort = process.env.E2E_MYSQL_PORT ?? "13306";
const env = {
  ...process.env,
  E2E_MYSQL_PORT: mysqlPort,
  E2E_BACKEND_PORT: process.env.E2E_BACKEND_PORT ?? "18080",
  E2E_FRONTEND_PORT: process.env.E2E_FRONTEND_PORT ?? "13001",
};
const docker = (args, options = {}) => spawnSync("docker", args, { stdio: "inherit", ...options });

function cleanup() {
  docker(["rm", "-f", containerName]);
}

function terminateProcessTree(child, signal = "SIGTERM") {
  if (!child || child.exitCode !== null) return;
  if (process.platform === "win32") {
    spawnSync("taskkill", ["/pid", String(child.pid), "/t", "/f"], { stdio: "ignore" });
  } else {
    try { process.kill(-child.pid, signal); } catch {}
  }
}

function waitForExit(child) {
  return new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code, signal) => resolve({ code, signal }));
  });
}

let playwright;
let stopping = false;
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    if (stopping) return;
    stopping = true;
    terminateProcessTree(playwright);
    setTimeout(() => terminateProcessTree(playwright, "SIGKILL"), 5_000).unref();
    cleanup();
    process.exitCode = 1;
  });
}

try {
  const started = docker([
    "run", "--detach", "--rm", "--name", containerName,
    "--publish", `127.0.0.1:${mysqlPort}:3306`,
    "--env", "MYSQL_DATABASE=gtublog_e2e",
    "--env", "MYSQL_USER=gtublog_e2e",
    "--env", "MYSQL_PASSWORD=gtublog-e2e-password",
    "--env", "MYSQL_ROOT_PASSWORD=gtublog-e2e-root-password",
    "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209",
  ]);
  if (started.status !== 0) throw new Error("Unable to start the disposable MySQL 8.4 container.");

  let ready = false;
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const probe = docker(["exec", containerName, "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-pgtublog-e2e-root-password", "--silent"], { stdio: "ignore" });
    if (probe.status === 0) { ready = true; break; }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  if (!ready) throw new Error("Disposable MySQL did not become healthy within 60 seconds.");

  const windows = process.platform === "win32";
  const command = windows ? process.env.ComSpec ?? "cmd.exe" : "pnpm";
  const args = windows
    ? ["/d", "/s", "/c", "pnpm exec playwright test --config playwright.fullstack.config.ts e2e/publishing.fullstack.spec.ts"]
    : ["exec", "playwright", "test", "--config", "playwright.fullstack.config.ts", "e2e/publishing.fullstack.spec.ts"];
  playwright = spawn(command, args, {
    env,
    stdio: "inherit",
    detached: !windows,
  });
  const result = await waitForExit(playwright);
  process.exitCode = result.code ?? 1;
} finally {
  terminateProcessTree(playwright);
  cleanup();
}
