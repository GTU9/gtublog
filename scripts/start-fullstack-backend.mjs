import { generateKeyPairSync } from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import process from "node:process";
import { fileURLToPath } from "node:url";

const { publicKey, privateKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});

const windows = process.platform === "win32";
const command = windows ? process.env.ComSpec ?? "cmd.exe" : "./gradlew";
const args = windows ? ["/d", "/s", "/c", "gradlew.bat bootRun --no-daemon --no-configuration-cache"] : ["bootRun", "--no-daemon", "--no-configuration-cache"];
const child = spawn(command, args, {
  cwd: fileURLToPath(new URL("../backend/", import.meta.url)),
  env: {
    ...process.env,
    SERVER_PORT: process.env.E2E_BACKEND_PORT ?? "18080",
    MYSQL_HOST: "127.0.0.1",
    MYSQL_PORT: process.env.E2E_MYSQL_PORT ?? "13306",
    MYSQL_DATABASE: "gtublog_e2e",
    MYSQL_USER: "gtublog_e2e",
    MYSQL_PASSWORD: "gtublog-e2e-password",
    SPRING_PROFILES_ACTIVE: "e2e",
    SPRING_DATASOURCE_URL: `jdbc:mysql://127.0.0.1:${process.env.E2E_MYSQL_PORT ?? "13306"}/gtublog_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`,
    SPRING_DATASOURCE_USERNAME: "gtublog_e2e",
    SPRING_DATASOURCE_PASSWORD: "gtublog-e2e-password",
    ADMIN_BOOTSTRAP_USERNAME: "admin",
    ADMIN_BOOTSTRAP_PASSWORD: "admin-test-password",
    ADMIN_BOOTSTRAP_DISPLAY_NAME: "E2E Administrator",
    AUTH_PUBLIC_KEY_PEM: publicKey,
    AUTH_PRIVATE_KEY_PEM: privateKey,
    AUTH_ALLOWED_ORIGIN: `http://127.0.0.1:${process.env.E2E_FRONTEND_PORT ?? "13001"}`,
    AUTH_COOKIE_SECURE: "false",
    AUTOMATION_WORKER_SHARED_TOKEN: "e2e-worker-token-not-for-production",
  },
  stdio: "inherit",
  detached: !windows,
});

function terminateProcessTree(signal = "SIGTERM") {
  if (child.exitCode !== null) return;
  if (windows) {
    spawnSync("taskkill", ["/pid", String(child.pid), "/t", "/f"], { stdio: "ignore" });
  } else {
    try { process.kill(-child.pid, signal); } catch {}
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
