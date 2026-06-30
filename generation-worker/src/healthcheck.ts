#!/usr/bin/env node
import { readFileSync } from "node:fs";

import type { WorkerHealthSnapshot } from "./health.js";

const file = process.env.GENERATION_HEALTH_FILE ?? "/tmp/gtublog-worker-health.json";
const maximumAgeMs = Number(process.env.GENERATION_READINESS_MAX_AGE_MS ?? "120000");

try {
  const snapshot = JSON.parse(readFileSync(file, "utf8")) as WorkerHealthSnapshot;
  const contactTime = snapshot.lastBackendContactAt ? Date.parse(snapshot.lastBackendContactAt) : Number.NaN;
  if (!Number.isSafeInteger(snapshot.pid) || snapshot.pid <= 0 || snapshot.shuttingDown || !Number.isFinite(contactTime) || Date.now() - contactTime > maximumAgeMs) process.exitCode = 1;
  else process.kill(snapshot.pid, 0);
} catch {
  process.exitCode = 1;
}
