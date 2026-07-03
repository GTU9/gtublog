import { spawn, spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import process from "node:process";
import { fileURLToPath } from "node:url";

const frontendDirectory = fileURLToPath(new URL("../frontend/", import.meta.url));
const rootDirectory = fileURLToPath(new URL("../", import.meta.url));
const windows = process.platform === "win32";

const buildCommand = windows ? process.env.ComSpec ?? "cmd.exe" : "pnpm";
const buildArgs = windows ? ["/d", "/s", "/c", "pnpm --dir frontend build"] : ["--dir", "frontend", "build"];
const built = spawnSync(buildCommand, buildArgs, {
  cwd: rootDirectory,
  stdio: "inherit",
  env: process.env,
  shell: false,
});
if (built.error) throw built.error;
if (built.status !== 0) process.exit(built.status ?? 1);

const standaloneRoot = fileURLToPath(new URL("../frontend/.next/standalone/frontend/", import.meta.url));
const standaloneStaticDirectory = fileURLToPath(new URL("../frontend/.next/standalone/frontend/.next/static/", import.meta.url));
const builtStaticDirectory = fileURLToPath(new URL("../frontend/.next/static/", import.meta.url));
const publicDirectory = fileURLToPath(new URL("../frontend/public/", import.meta.url));
const standalonePublicDirectory = fileURLToPath(new URL("../frontend/.next/standalone/frontend/public/", import.meta.url));

rmSync(standaloneStaticDirectory, { force: true, recursive: true });
mkdirSync(fileURLToPath(new URL("../frontend/.next/standalone/frontend/.next/", import.meta.url)), { recursive: true });
cpSync(builtStaticDirectory, standaloneStaticDirectory, { recursive: true });

if (existsSync(publicDirectory)) {
  rmSync(standalonePublicDirectory, { force: true, recursive: true });
  cpSync(publicDirectory, standalonePublicDirectory, { recursive: true });
}

const child = spawn("node", ["server.js"], {
  cwd: standaloneRoot,
  env: {
    ...process.env,
    PORT: process.env.PORT ?? process.env.E2E_FRONTEND_PORT ?? "13001",
    HOSTNAME: process.env.HOSTNAME ?? "127.0.0.1",
  },
  stdio: "inherit",
  shell: false,
});

function terminateProcessTree(signal = "SIGTERM") {
  if (child.exitCode !== null) return;
  if (windows) {
    spawnSync("taskkill", ["/pid", String(child.pid), "/t", "/f"], { stdio: "ignore" });
  } else {
    try { child.kill(signal); } catch {}
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    terminateProcessTree("SIGTERM");
    setTimeout(() => terminateProcessTree("SIGKILL"), 5_000).unref();
  });
}

child.once("exit", (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  process.exit(code ?? 1);
});
