import { expect, test } from "@playwright/test";

test("restored MySQL backup preserves admin login, public post reads, and automation diagnostics", async ({ page }) => {
  const postTitle = process.env.E2E_POST_TITLE ?? "Restored full-stack post";
  const postSlug = process.env.E2E_POST_SLUG ?? "restored-full-stack-post";

  await page.goto("/admin/login");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.goto(`/posts/${postSlug}`);
  await expect(page.getByRole("heading", { name: postTitle })).toBeVisible();

  await page.goto("/admin/login");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.getByRole("link", { name: "Automation" }).click();
  await expect(page.getByRole("heading", { name: "Automation" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Operational diagnostics" })).toBeVisible();
});
