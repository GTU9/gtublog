import { expect, test } from "@playwright/test";
import { access, readFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");

test("multi-runtime workspace exposes the required project boundaries", async () => {
  const requiredPaths = [
    "backend/build.gradle.kts",
    "frontend/package.json",
    "generation-worker/package.json",
    "contracts/README.md",
    "compose.yaml",
  ];

  await Promise.all(requiredPaths.map((file) => access(path.join(root, file))));

  const workspace = await readFile(path.join(root, "pnpm-workspace.yaml"), "utf8");
  expect(workspace).toContain("frontend");
  expect(workspace).toContain("generation-worker");
});
