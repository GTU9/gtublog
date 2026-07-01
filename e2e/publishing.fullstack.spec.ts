import { expect, test } from "@playwright/test";

test("real Spring, MySQL, and Next publish an administrator-authored post", async ({ page, request }) => {
  const suffix = Date.now().toString(36);
  const categoryName = `Full-stack ${suffix}`;
  const categorySlug = `full-stack-${suffix}`;
  const postTitle = `Full-stack published post ${suffix}`;
  const postSlug = `full-stack-post-${suffix}`;
  const backendPort = process.env.E2E_BACKEND_PORT ?? "18080";

  await page.goto("/admin/login");
  const loginResponse = page.waitForResponse((response) =>
    response.url() === `http://127.0.0.1:${backendPort}/api/v1/auth/login` && response.request().method() === "POST");
  await page.getByRole("button", { name: "Sign in" }).click();
  expect((await loginResponse).ok()).toBeTruthy();
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.getByRole("link", { name: "Taxonomy" }).click();
  await page.getByRole("textbox", { name: "Name" }).nth(0).fill(categoryName);
  await page.getByRole("textbox", { name: "Slug" }).nth(0).fill(categorySlug);
  const categoryResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/admin/taxonomy/categories") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Create category" }).click();
  expect((await categoryResponse).status()).toBe(201);

  await page.getByRole("link", { name: "Posts" }).click();
  await page.getByRole("link", { name: "New draft" }).click();
  await page.getByLabel("Slug").fill(postSlug);
  await page.getByLabel("Title").fill(postTitle);
  await page.getByLabel("Excerpt").fill("Verified through the real full-stack boundary.");
  await page.getByLabel("Markdown").fill("Real Spring and MySQL content.\n\nSecond paragraph.");
  await page.getByLabel("Revision note").fill("Created by full-stack Playwright");
  await page.getByRole("checkbox", { name: categoryName }).check();
  const createResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/admin/posts") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Create draft" }).click();
  expect((await createResponse).status()).toBe(201);

  await page.waitForURL(/\/admin\/posts\/\d+$/);
  const publishResponse = page.waitForResponse((response) =>
    /\/api\/v1\/admin\/posts\/\d+\/publish$/.test(response.url()) && response.request().method() === "POST");
  await page.getByRole("button", { name: "Publish" }).click();
  expect((await publishResponse).ok()).toBeTruthy();

  const apiResponse = await request.get(`http://127.0.0.1:${backendPort}/api/v1/public/posts/${postSlug}`);
  expect(apiResponse.ok()).toBeTruthy();
  const publicPost = await apiResponse.json();
  expect(publicPost.title).toBe(postTitle);
  expect(publicPost.categories).toContainEqual(expect.objectContaining({ slug: categorySlug }));

  await page.goto(`/posts/${postSlug}`);
  await expect(page.getByRole("heading", { name: postTitle })).toBeVisible();
  await expect(page.getByText("Real Spring and MySQL content.")).toBeVisible();
});
