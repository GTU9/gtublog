import { expect, test } from "@playwright/test";

test("public blog routes render meaningful SSR content", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "GTU Automated Content Blog" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Home" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Why this blog uses Spring Boot for automated publishing" })).toBeVisible();

  await page.getByRole("link", { name: "Why this blog uses Spring Boot for automated publishing" }).click();
  await expect(page.getByRole("heading", { name: "Why this blog uses Spring Boot for automated publishing" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Source links", exact: true })).toBeVisible();

  await page.goto("/categories/development");
  await expect(page.getByRole("heading", { name: "Development category" })).toBeVisible();

  await page.goto("/tags/react");
  await expect(page.getByRole("heading", { name: "React tag" })).toBeVisible();

  await page.goto("/search?q=automation");
  await expect(page.getByRole("heading", { name: "Search" })).toBeVisible();
  await expect(page.getByText("Why this blog uses Spring Boot for automated publishing")).toBeVisible();

  await page.goto("/archive");
  await expect(page.getByRole("heading", { name: "Archive" })).toBeVisible();
  await expect(page.getByText("2026 / 6")).toBeVisible();
});

test("rss, sitemap, and robots endpoints are exposed", async ({ request }) => {
  const rss = await request.get("/rss.xml");
  expect(rss.ok()).toBeTruthy();
  expect(await rss.text()).toContain("<rss");

  const sitemap = await request.get("/sitemap.xml");
  expect(sitemap.ok()).toBeTruthy();
  expect(await sitemap.text()).toContain("/posts/spring-boot-automation-blog");

  const robots = await request.get("/robots.txt");
  expect(robots.ok()).toBeTruthy();
  expect(await robots.text()).toContain("Sitemap:");
});

test("public HTML responses include browser security headers", async ({ request }) => {
  const response = await request.get("/");
  expect(response.ok()).toBeTruthy();
  const headers = response.headers();
  expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
  expect(headers["content-security-policy"]).toContain("connect-src 'self' http://127.0.0.1:8080");
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["x-frame-options"]).toBe("DENY");
  expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
  expect(headers["permissions-policy"]).toContain("camera=()");
  expect(headers["strict-transport-security"]).toContain("max-age=31536000");
});
