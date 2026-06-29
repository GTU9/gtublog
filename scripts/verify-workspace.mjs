import { existsSync } from "node:fs";
import { delimiter, join } from "node:path";
import { spawnSync } from "node:child_process";

const root = process.cwd();
const windows = process.platform === "win32";

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    env: options.env ?? process.env,
    // Commands and arguments are fixed by this tracked script; no user input is interpolated.
    shell: windows && /\.(?:bat|cmd)$/i.test(command),
    stdio: "inherit",
  });

  if (result.error) {
    if (!options.suppressError) {
      process.stderr.write(`${result.error.message}\n`);
    }
    return { status: null, error: result.error };
  }
  return { status: result.status, error: null };
}

const backendEnvironment = { ...process.env };
const localJavaHome = join(root, ".tools", "jdk-25.0.3+9");
if (!backendEnvironment.JAVA_HOME && existsSync(localJavaHome)) {
  backendEnvironment.JAVA_HOME = localJavaHome;
  const pathKey = Object.keys(backendEnvironment).find(
    (key) => key.toLowerCase() === "path",
  ) ?? "PATH";
  backendEnvironment[pathKey] =
    `${join(localJavaHome, "bin")}${delimiter}${backendEnvironment[pathKey] ?? ""}`;
}

const gradleCommand = windows
  ? join(root, "backend", "gradlew.bat")
  : join(root, "backend", "gradlew");
const gradle = run(gradleCommand, ["clean", "check", "bootJar"], {
  env: backendEnvironment,
});
if (gradle.status !== 0) process.exit(gradle.status ?? 1);

const nodeChecks = run(windows ? "pnpm.cmd" : "pnpm", ["quality:node"]);
if (nodeChecks.status !== 0) process.exit(nodeChecks.status ?? 1);

let compose = run("docker", ["compose", "-f", "compose.yaml", "config", "--quiet"], {
  suppressError: true,
});
if (compose.error && windows) {
  const localCompose = join(root, ".tools", "docker-compose.exe");
  if (existsSync(localCompose)) {
    compose = run(localCompose, ["-f", "compose.yaml", "config", "--quiet"]);
  }
}

if (compose.status !== 0) {
  if (compose.error) {
    process.stderr.write(
      "Docker Compose CLI was not found. Install Docker Desktop/Engine with Compose, then rerun pnpm quality.\n",
    );
  }
  process.exit(compose.status ?? 1);
}
