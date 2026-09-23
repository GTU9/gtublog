import { expect, test } from "@playwright/test";

test("signed outbox delivery converges cached public routes after manual mutations", async ({ page, request }) => {
  test.setTimeout(240_000);
  const suffix = Date.now().toString(36);
  const backend = `http://127.0.0.1:${process.env.E2E_BACKEND_PORT ?? "18080"}`;
  const frontend = `http://127.0.0.1:${process.env.E2E_FRONTEND_PORT ?? "13001"}`;
  const api = `${backend}/api/v1/admin`;
  const login = await request.post(`${backend}/api/v1/auth/login`, {
    data: { username: "admin", password: "admin-test-password" },
  });
  expect(login.ok()).toBeTruthy();
  const { accessToken } = await login.json() as { accessToken: string };
  const headers = { Authorization: `Bearer ${accessToken}` };
  const categorySlug = `revalidation-${suffix}`;
  const tagSlug = `signed-tag-${suffix}`;
  const category = await request.post(`${api}/taxonomy/categories`, {
    headers, data: { slug: categorySlug, name: `Revalidation ${suffix}`, description: "Cache convergence test" },
  });
  const tag = await request.post(`${api}/taxonomy/tags`, {
    headers, data: { slug: tagSlug, name: `Signed tag ${suffix}`, description: "Cache convergence test" },
  });
  expect(category.status()).toBe(201);
  expect(tag.status()).toBe(201);
  const categoryId = (await category.json() as { id: number }).id;
  const tagId = (await tag.json() as { id: number }).id;

  async function createDraft(slug: string, title: string) {
    const response = await request.post(`${api}/posts`, {
      headers,
      data: { slug, title, excerpt: `Summary ${title}`, contentMarkdown: `Body ${title}`,
        contentHtml: `<p>Body ${title}</p>`, categoryIds: [categoryId], tagIds: [tagId], revisionNote: "Initial" },
    });
    expect(response.status()).toBe(201);
    return (await response.json() as { id: number }).id;
  }

  async function mutate(id: number, path: string, method: "POST" | "PUT" = "POST", data?: object) {
    const response = await request.fetch(`${api}/posts/${id}${path}`, { method, headers, data });
    expect(response.ok()).toBeTruthy();
    await expect.poll(async () => {
      await request.post(`${api}/automation/outbox/process`, { headers });
      const outbox = await request.get(`${api}/automation/outbox`, { headers });
      const events = await outbox.json() as { aggregateId: number; deliveryStatus: string }[];
      return events.find((event) => event.aggregateId === id)?.deliveryStatus;
    }, { timeout: 25_000, intervals: [300, 500, 1000] }).toBe("DELIVERED");
    return response;
  }

  async function html(path: string) { return (await request.get(`${frontend}${path}`)).text(); }
  async function contains(path: string, value: string, present: boolean) {
    if (path.startsWith("/search?")) {
      await expect.poll(async () => {
        await page.goto(path);
        const list = page.getByLabel("글 목록");
        return (await list.count()) > 0 && (await list.textContent())?.includes(value) === true;
      }, { timeout: 10_000 }).toBe(present);
      return;
    }
    await expect.poll(async () => (await html(path)).includes(value), { timeout: 10_000 }).toBe(present);
  }

  const companionSlug = `signed-companion-${suffix}`;
  const companionTitle = `Signed companion ${suffix}`;
  const companionId = await createDraft(companionSlug, companionTitle);
  await mutate(companionId, "/publish");
  const oldSlug = `signed-old-${suffix}`;
  const newSlug = `signed-new-${suffix}`;
  const oldTitle = `Signed old title ${suffix}`;
  const newTitle = `Signed new title ${suffix}`;
  const postId = await createDraft(oldSlug, oldTitle);
  const year = new Date().getUTCFullYear();
  const month = new Date().getUTCMonth() + 1;
  const archivePath = `/archive?year=${year}&month=${month}`;

  // Fill every public cache surface while the primary post is still a draft.
  for (const path of ["/", `/search?q=${encodeURIComponent(oldTitle)}`, `/categories/${categorySlug}`,
    `/tags/${tagSlug}`, archivePath, `/posts/${companionSlug}`, "/rss.xml", "/sitemap.xml"]) {
    await request.get(`${frontend}${path}`);
  }

  await mutate(postId, "/publish");
  for (const path of ["/", `/search?q=${encodeURIComponent(oldTitle)}`, `/categories/${categorySlug}`,
    `/tags/${tagSlug}`, archivePath, `/posts/${companionSlug}`, "/rss.xml"]) {
    await contains(path, oldTitle, true);
  }
  await contains("/sitemap.xml", `/posts/${oldSlug}`, true);
  expect((await request.get(`${frontend}/posts/${oldSlug}`)).status()).toBe(200);

  // The old detail route is cached before the canonical slug changes.
  await request.get(`${frontend}/posts/${oldSlug}`);
  await mutate(postId, "", "PUT", { slug: newSlug, title: newTitle, excerpt: `Summary ${newTitle}`,
    contentMarkdown: `Body ${newTitle}`, contentHtml: `<p>Body ${newTitle}</p>`,
    categoryIds: [categoryId], tagIds: [tagId], revisionNote: "Changed slug" });
  await expect.poll(async () => (await request.get(`${frontend}/posts/${oldSlug}`)).status()).toBe(404);
  for (const path of [`/posts/${newSlug}`, `/search?q=${encodeURIComponent(newTitle)}`,
    `/categories/${categorySlug}`, `/tags/${tagSlug}`, archivePath, `/posts/${companionSlug}`, "/rss.xml"]) {
    await contains(path, newTitle, true);
  }
  await contains("/sitemap.xml", `/posts/${oldSlug}`, false);
  await contains("/sitemap.xml", `/posts/${newSlug}`, true);

  await mutate(postId, "/revisions/1/restore");
  await contains(`/posts/${newSlug}`, oldTitle, true);
  await contains("/rss.xml", newTitle, false);

  await mutate(postId, "/archive");
  await expect.poll(async () => (await request.get(`${frontend}/posts/${newSlug}`)).status()).toBe(404);
  for (const path of ["/", `/search?q=${encodeURIComponent(oldTitle)}`, `/categories/${categorySlug}`,
    `/tags/${tagSlug}`, archivePath, `/posts/${companionSlug}`, "/rss.xml"]) {
    await contains(path, oldTitle, false);
  }
  await contains("/sitemap.xml", `/posts/${newSlug}`, false);

  await mutate(companionId, "/delete");
  await expect.poll(async () => (await request.get(`${frontend}/posts/${companionSlug}`)).status()).toBe(404);
  await contains("/rss.xml", companionTitle, false);
});
