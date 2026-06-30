import { writeFileSync } from "node:fs";

export interface WorkerHealthSnapshot {
  readonly pid: number;
  readonly startedAt: string;
  readonly lastBackendContactAt: string | null;
  readonly shuttingDown: boolean;
}

export interface WorkerHealthState {
  snapshot(): WorkerHealthSnapshot;
  recordBackendContact(at?: Date): WorkerHealthSnapshot;
  beginShutdown(): WorkerHealthSnapshot;
}

export function createWorkerHealthState(startedAt = new Date(), healthFile?: string): WorkerHealthState {
  let lastBackendContactAt: string | null = null;
  let shuttingDown = false;
  const snapshot = (): WorkerHealthSnapshot => ({
    pid: process.pid,
    startedAt: startedAt.toISOString(),
    lastBackendContactAt,
    shuttingDown,
  });
  const persist = (value: WorkerHealthSnapshot): WorkerHealthSnapshot => {
    if (healthFile) writeFileSync(healthFile, `${JSON.stringify(value)}\n`, { encoding: "utf8", mode: 0o600 });
    return value;
  };
  persist(snapshot());
  return {
    snapshot,
    recordBackendContact(at = new Date()) {
      lastBackendContactAt = at.toISOString();
      return persist(snapshot());
    },
    beginShutdown() {
      shuttingDown = true;
      return persist(snapshot());
    },
  };
}
