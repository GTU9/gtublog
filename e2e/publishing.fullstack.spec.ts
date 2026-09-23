import { expect, test } from "@playwright/test";

test("real Spring, MySQL, and Next publish an administrator-authored post", async ({ page, request }) => {
  test.setTimeout(120_000);
  const suffix = process.env.E2E_POST_SUFFIX ?? Date.now().toString(36);
  const categoryName = process.env.E2E_CATEGORY_NAME ?? `Full-stack ${suffix}`;
  const categorySlug = process.env.E2E_CATEGORY_SLUG ?? `full-stack-${suffix}`;
  const postTitle = process.env.E2E_POST_TITLE ?? `Full-stack published post ${suffix}`;
  const postSlug = process.env.E2E_POST_SLUG ?? `full-stack-post-${suffix}`;
  const backendPort = process.env.E2E_BACKEND_PORT ?? "18080";

  await page.goto("/admin/login");
  const loginResponse = page.waitForResponse((response) =>
    response.url() === `http://127.0.0.1:${backendPort}/api/v1/auth/login` && response.request().method() === "POST");
  await page.getByRole("button", { name: "로그인" }).click();
  const login = await loginResponse;
  expect(login.ok()).toBeTruthy();
  const session = await login.json();
  await expect(page.getByRole("heading", { name: "대시보드" })).toBeVisible();

  await page.getByRole("link", { name: "분류 관리" }).click();
  await page.getByRole("textbox", { name: "이름" }).nth(0).fill(categoryName);
  await page.getByRole("textbox", { name: "Slug" }).nth(0).fill(categorySlug);
  const categoryResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/admin/taxonomy/categories") && response.request().method() === "POST");
  await page.getByRole("button", { name: "카테고리 추가" }).click();
  expect((await categoryResponse).status()).toBe(201);

  await page.getByRole("link", { name: "글 관리" }).click();
  await page.getByRole("link", { name: "새 글 작성" }).click();
  await page.getByLabel("Slug").fill(postSlug);
  await page.getByLabel("제목").fill(postTitle);
  await page.getByLabel("요약").fill("Verified through the real full-stack boundary.");
  await page.getByLabel("Markdown").fill("Real Spring and MySQL content.\n\nSecond paragraph.");
  await page.getByLabel("리비전 메모").fill("Created by full-stack Playwright");
  await page.getByRole("checkbox", { name: categoryName }).check();
  const createResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/admin/posts") && response.request().method() === "POST");
  await page.getByRole("button", { name: "초안 저장" }).click();
  expect((await createResponse).status()).toBe(201);

  await page.waitForURL(/\/admin\/posts\/\d+$/);
  const publishResponse = page.waitForResponse((response) =>
    /\/api\/v1\/admin\/posts\/\d+\/publish$/.test(response.url()) && response.request().method() === "POST");
  await page.getByRole("button", { name: "발행" }).click();
  expect((await publishResponse).ok()).toBeTruthy();

  const apiResponse = await request.get(`http://127.0.0.1:${backendPort}/api/v1/public/posts/${postSlug}`);
  expect(apiResponse.ok()).toBeTruthy();
  const publicPost = await apiResponse.json();
  expect(publicPost.title).toBe(postTitle);
  expect(publicPost.categories).toContainEqual(expect.objectContaining({ slug: categorySlug }));

  await page.goto(`/posts/${postSlug}`);
  await expect(page.getByRole("heading", { name: postTitle })).toBeVisible();
  await expect(page.getByText("Real Spring and MySQL content.")).toBeVisible();

  const authorization = { Authorization: `Bearer ${session.accessToken}` };
  const categoryId = publicPost.categories.find((category: { slug: string }) => category.slug === categorySlug).id;
  for (let index = 0; index < 20; index++) {
    const created = await request.post(`http://127.0.0.1:${backendPort}/api/v1/admin/posts`, {
      headers: authorization,
      data: { slug: `navigation-${suffix}-${index}`, title: `Navigation ${suffix} ${index}`, excerpt: "Pagination regression fixture",
        contentMarkdown: "Navigation body", contentHtml: "<p>Navigation body</p>", categoryIds: [categoryId], tagIds: [], sourceFingerprint: null, revisionNote: "Navigation regression" },
    });
    expect(created.status()).toBe(201);
    const post = await created.json();
    expect((await request.post(`http://127.0.0.1:${backendPort}/api/v1/admin/posts/${post.id}/publish`, { headers: authorization })).ok()).toBeTruthy();
  }
  const stats = await request.get(`http://127.0.0.1:${backendPort}/api/v1/admin/posts/stats`, { headers: authorization });
  expect(stats.ok()).toBeTruthy();
  expect((await stats.json()).published).toBeGreaterThanOrEqual(21);

  await page.goto(`/categories/${categorySlug}`);
  await page.getByRole("link", { name: "다음 페이지" }).click();
  await expect(page).toHaveURL(new RegExp(`/categories/${categorySlug}\\?page=2$`));
  await expect(page.getByRole("link", { name: postTitle, exact: true })).toBeVisible();
  await page.goto(`/search?q=${encodeURIComponent(categoryName)}`);
  await expect(page.getByLabel("글 목록").getByRole("article")).toHaveCount(12);
  await page.getByRole("link", { name: "다음 페이지" }).click();
  expect(new URL(page.url()).searchParams.get("q")).toBe(categoryName);
  await expect(page.getByRole("link", { name: postTitle, exact: true })).toBeVisible();

  const [year, month] = publicPost.firstPublishedAt.split("-");
  await page.goto(`/archive?year=${year}&month=${Number(month)}`);
  await expect(page.getByRole("heading", { name: `${year}년 ${Number(month)}월 아카이브` })).toBeVisible();
  await expect(page.getByLabel("글 목록")).toBeVisible();
});
