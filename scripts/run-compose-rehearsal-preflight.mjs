import { existsSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import process from "node:process";
const envFile = process.env.COMPOSE_REHEARSAL_ENV_FILE ?? ".env.rehearsal";
const composeFile = "compose.prod.yaml";
const requiredVariables = [
  "PUBLIC_ORIGIN", "MYSQL_HOST", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD",
  "AUTH_PUBLIC_KEY_PEM", "AUTH_PRIVATE_KEY_PEM", "ADMIN_BOOTSTRAP_PASSWORD",
  "AUTOMATION_WORKER_SHARED_TOKEN", "AUTOMATION_REVALIDATION_SHARED_SECRET",
];

if (!existsSync(envFile)) {
  throw new Error(`Compose rehearsal environment file is missing: ${envFile}. Copy .env.production.example and use rehearsal-only values.`);
}

const values = parseEnv(readFileSync(envFile, "utf8"));
const missing = requiredVariables.filter((name) => !values.get(name)?.trim());
const placeholders = requiredVariables.filter((name) => /replace-with|example\.com/i.test(values.get(name) ?? ""));
if (missing.length > 0) throw new Error(`Compose rehearsal environment is missing required values: ${missing.join(", ")}`);
if (placeholders.length > 0) throw new Error(`Compose rehearsal environment still contains placeholder values: ${placeholders.join(", ")}`);
if (values.get("GENERATION_CODEX_ENV_ISOLATION_APPROVED") === "true") {
  throw new Error("The rehearsal preflight refuses GENERATION_CODEX_ENV_ISOLATION_APPROVED=true. Codex approval belongs to the separate canary-attestation release gate.");
}

const result = spawnSync("docker", ["compose", "--env-file", envFile, "-f", composeFile, "config", "--quiet"], {
  cwd: process.cwd(), encoding: "utf8",
});
if (result.error) throw result.error;
if (result.status !== 0) throw new Error(`Docker Compose configuration validation failed.\n${result.stderr || result.stdout}`.trim());

console.log(`Compose rehearsal preflight passed for ${envFile}.`);
console.log("The base stack excludes generation-worker until Codex canary attestation is approved. Start it explicitly with --profile generation only after that gate passes.");

function parseEnv(content) {
  const values = new Map();
  for (const line of content.split(/\r?\n/u)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const equals = trimmed.indexOf("=");
    if (equals >= 1) values.set(trimmed.slice(0, equals).trim(), trimmed.slice(equals + 1).trim());
  }
  return values;
}
