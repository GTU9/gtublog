import { expect, test } from "@playwright/test";

test("public blog routes render meaningful SSR content", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "지튜 블로그" })).toBeVisible();
  await expect(page.getByRole("link", { name: "홈" })).toBeVisible();
  await expect(
    page.getByLabel("글 목록").getByRole("link", { name: "자동 발행 블로그에 Spring Boot를 선택한 이유" }),
  ).toBeVisible();

  await page.getByLabel("글 목록").getByRole("link", { name: "자동 발행 블로그에 Spring Boot를 선택한 이유" }).click();
  await expect(page.getByRole("heading", { name: "자동 발행 블로그에 Spring Boot를 선택한 이유" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "출처 링크", exact: true })).toBeVisible();

  await page.goto("/categories/development");
  await expect(page.getByRole("heading", { name: "개발 카테고리" })).toBeVisible();

  await page.goto("/tags/react");
  await expect(page.getByRole("heading", { name: "React 태그" })).toBeVisible();

  await page.goto("/search?q=자동화");
  await expect(page.getByRole("heading", { name: "검색" })).toBeVisible();
  await expect(
    page.getByLabel("글 목록").getByRole("link", { name: "자동 발행보다 먼저 필요한 것은 출처 검증" }),
  ).toBeVisible();

  await page.goto("/archive");
  await expect(page.getByRole("heading", { name: "아카이브", level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "2026년 6월 · 총 3건" })).toBeVisible();
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
