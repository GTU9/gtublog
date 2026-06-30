import { BackendOperationError, GenerationTimeoutError } from "./runtime.js";

export type WorkerFailureExitCode = 2 | 3 | 4;

export function exitCodeForError(error: unknown): WorkerFailureExitCode {
  if (error instanceof GenerationTimeoutError) return 4;
  if (error instanceof BackendOperationError) {
    if (error.kind === "lease-lost") return 4;
    if (error.kind === "fatal" || error.kind === "conflict") return 2;
  }
  return 3;
}
