import { randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const mysqlImage = "mysql:8.4.10@sha256:d36d39a64cd12a5c1cc9e6aa2bfb5f8d4c81a2f6586e0a04a9ae13939db02209";
const windows = process.platform === "win32";
const require = createRequire(import.meta.url);
const playwrightCli = require.resolve("@playwright/test/cli");
const suffix = randomUUID().slice(0, 8);
const sourceContainer = `gtublog-backup-src-${suffix}`;
const restoreContainer = `gtublog-backup-restore-${suffix}`;
const dumpPath = join(tmpdir(), `gtublog-backup-${suffix}.sql`);
const sourceMysqlPort = process.env.E2E_MYSQL_PORT ?? "14306";
const restoreMysqlPort = process.env.RESTORE_E2E_MYSQL_PORT ?? "14307";
const sourceBackendPort = process.env.E2E_BACKEND_PORT ?? "18180";
const sourceFrontendPort = process.env.E2E_FRONTEND_PORT ?? "13101";
const restoreBackendPort = process.env.RESTORE_E2E_BACKEND_PORT ?? "18181";
const restoreFrontendPort = process.env.RESTORE_E2E_FRONTEND_PORT ?? "13102";
const postSuffix = process.env.E2E_POST_SUFFIX ?? `restore-${Date.now().toString(36)}`;
const postTitle = process.env.E2E_POST_TITLE ?? `Restored full-stack post ${postSuffix}`;
const postSlug = process.env.E2E_POST_SLUG ?? `restored-full-stack-post-${postSuffix}`;
const categoryName = process.env.E2E_CATEGORY_NAME ?? `Restored category ${postSuffix}`;
const categorySlug = process.env.E2E_CATEGORY_SLUG ?? `restored-category-${postSuffix}`;
const dumpDatabase = "gtublog_e2e";
const dumpUser = "gtublog_e2e";
const dumpPassword = "gtublog-e2e-password";
const dumpRootPassword = "gtublog-e2e-root-password";
const publishTestTitle = "real Spring, MySQL, and Next publish an administrator-authored post";
const restoreTestTitle = "restored MySQL backup preserves admin login, public post reads, and automation diagnostics";

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: process.cwd(),
    env: options.env ?? process.env,
    encoding: options.encoding ?? "utf8",
    stdio: options.stdio ?? "pipe",
    input: options.input,
    shell: options.shell ?? (windows && /\.(?:cmd|bat)$/i.test(command)),
  });
  if (result.error) {
    throw result.error;
  }
  return result;
}

function runDocker(args, options = {}) {
  return run("docker", args, options);
}

function removeContainer(name) {
  runDocker(["rm", "-f", name], { stdio: "ignore" });
}

function waitForMysql(containerName) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const probe = runDocker(
      ["exec", containerName, "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", `-p${dumpRootPassword}`, "--silent"],
      { stdio: "ignore" },
    );
    if (probe.status === 0) {
      return;
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  throw new Error(`MySQL container ${containerName} did not become healthy within 60 seconds.`);
}

function startMysqlContainer(name, hostPort) {
  const started = runDocker([
    "run",
    "--detach",
    "--rm",
    "--name",
    name,
    "--publish",
    `127.0.0.1:${hostPort}:3306`,
    "--env",
    `MYSQL_DATABASE=${dumpDatabase}`,
    "--env",
    `MYSQL_USER=${dumpUser}`,
    "--env",
    `MYSQL_PASSWORD=${dumpPassword}`,
    "--env",
    `MYSQL_ROOT_PASSWORD=${dumpRootPassword}`,
    mysqlImage,
  ], { stdio: "inherit" });
  if (started.status !== 0) {
    throw new Error(`Unable to start MySQL container ${name}.`);
  }
  waitForMysql(name);
}

function runPlaywrightPhase({ mysqlPort, backendPort, frontendPort, grep }) {
  const env = {
    ...process.env,
    E2E_MYSQL_PORT: mysqlPort,
    E2E_BACKEND_PORT: backendPort,
    E2E_FRONTEND_PORT: frontendPort,
    E2E_POST_SUFFIX: postSuffix,
    E2E_POST_TITLE: postTitle,
    E2E_POST_SLUG: postSlug,
    E2E_CATEGORY_NAME: categoryName,
    E2E_CATEGORY_SLUG: categorySlug,
  };
  const command = process.execPath;
  const args = [playwrightCli, "test", "--config", "playwright.fullstack.config.ts", "--grep", grep];
  const result = run(command, args, {
    env,
    stdio: "inherit",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(`Playwright phase failed for grep: ${grep}`);
  }
}

function dumpDatabaseFromContainer(containerName) {
  const dump = runDocker([
    "exec",
    containerName,
    "mysqldump",
    "-uroot",
    `-p${dumpRootPassword}`,
    "--single-transaction",
    "--routines",
    "--triggers",
    dumpDatabase,
  ]);
  if (dump.status !== 0) {
    throw new Error(`Unable to dump database from ${containerName}.`);
  }
  writeFileSync(dumpPath, dump.stdout, { encoding: "utf8" });
}

function restoreDumpIntoContainer(containerName) {
  const dumpSql = readFileSync(dumpPath);
  const restored = runDocker(
    ["exec", "-i", containerName, "mysql", "-uroot", `-p${dumpRootPassword}`, dumpDatabase],
    { input: dumpSql },
  );
  if (restored.status !== 0) {
    throw new Error(`Unable to restore dump into ${containerName}.`);
  }
}

try {
  startMysqlContainer(sourceContainer, sourceMysqlPort);
  runPlaywrightPhase({
    mysqlPort: sourceMysqlPort,
    backendPort: sourceBackendPort,
    frontendPort: sourceFrontendPort,
    grep: publishTestTitle,
  });

  dumpDatabaseFromContainer(sourceContainer);

  startMysqlContainer(restoreContainer, restoreMysqlPort);
  restoreDumpIntoContainer(restoreContainer);
  runPlaywrightPhase({
    mysqlPort: restoreMysqlPort,
    backendPort: restoreBackendPort,
    frontendPort: restoreFrontendPort,
    grep: restoreTestTitle,
  });
} finally {
  removeContainer(sourceContainer);
  removeContainer(restoreContainer);
  rmSync(dumpPath, { force: true });
}
