import { expect, test } from "@playwright/test";

test("restored MySQL backup preserves admin login, public post reads, and automation diagnostics", async ({ page }) => {
  const postTitle = process.env.E2E_POST_TITLE ?? "Restored full-stack post";
  const postSlug = process.env.E2E_POST_SLUG ?? "restored-full-stack-post";

  await page.goto("/admin/login");
  await page.getByRole("button", { name: "로그인" }).click();
  await expect(page.getByRole("heading", { name: "대시보드" })).toBeVisible();

  await page.goto(`/posts/${postSlug}`);
  await expect(page.getByRole("heading", { name: postTitle })).toBeVisible();

  await page.goto("/admin/login");
  await page.getByRole("button", { name: "로그인" }).click();
  await expect(page.getByRole("heading", { name: "대시보드" })).toBeVisible();

  await page.getByRole("link", { name: "자동화 운영" }).click();
  await expect(page.getByRole("heading", { name: "자동화 운영" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "운영 진단 요약" })).toBeVisible();
});
