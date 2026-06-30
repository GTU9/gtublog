import { describe, expect, it } from "vitest";

import { exitCodeForError } from "./exit-code.js";
import { BackendOperationError, GenerationTimeoutError } from "./runtime.js";

describe("one-shot worker exit classification", () => {
  it("uses the shutdown/timeout exit code for generation timeout and lease loss", () => {
    expect(exitCodeForError(new GenerationTimeoutError())).toBe(4);
    expect(exitCodeForError(
      new BackendOperationError("heartbeat", 409, "lease-lost", "lease lost"),
    )).toBe(4);
  });

  it("uses configuration exit code for fatal contract failures and transient exit code otherwise", () => {
    expect(exitCodeForError(
      new BackendOperationError("claim", 403, "fatal", "forbidden"),
    )).toBe(2);
    expect(exitCodeForError(
      new BackendOperationError("submit", 409, "conflict", "conflict"),
    )).toBe(2);
    expect(exitCodeForError(
      new BackendOperationError("claim", 503, "transient", "unavailable"),
    )).toBe(3);
    expect(exitCodeForError(new Error("provider failed"))).toBe(3);
  });
});
