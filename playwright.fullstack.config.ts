import { defineConfig } from "@playwright/test";

const frontendPort = process.env.E2E_FRONTEND_PORT ?? "13001";
const backendPort = process.env.E2E_BACKEND_PORT ?? "18080";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.fullstack.spec.ts",
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  webServer: [
    {
      command: "node scripts/start-fullstack-backend.mjs",
      url: `http://127.0.0.1:${backendPort}/actuator/health/readiness`,
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        E2E_MYSQL_PORT: process.env.E2E_MYSQL_PORT ?? "13306",
        E2E_BACKEND_PORT: backendPort,
        E2E_FRONTEND_PORT: frontendPort,
      },
    },
    {
      command: `pnpm --dir frontend build && pnpm --dir frontend start --port ${frontendPort}`,
      url: `http://127.0.0.1:${frontendPort}`,
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        NEXT_PUBLIC_GTUBLOG_ENABLE_DEV_MOCKS: "false",
        GTUBLOG_SITE_URL: `http://127.0.0.1:${frontendPort}`,
        GTUBLOG_PUBLIC_API_BASE_URL: `http://127.0.0.1:${backendPort}/api/v1/public`,
        NEXT_PUBLIC_GTUBLOG_APPLICATION_API_BASE_URL: `http://127.0.0.1:${backendPort}/api/v1`,
      },
    },
  ],
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
