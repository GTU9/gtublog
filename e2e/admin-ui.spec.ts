import { expect, test } from "@playwright/test";

test("administrator can sign in, create a draft, publish it, and restore a revision", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();

  await page.getByRole("link", { name: "Posts" }).click();
  await page.getByRole("link", { name: "New draft" }).click();

  await page.getByLabel("Title").fill("Playwright managed draft");
  await page.getByLabel("Excerpt").fill("A draft authored through the admin flow.");
  await page.getByLabel("Markdown").fill("First revision body\n\nWith preview content.");
  await page.getByLabel("Revision note").fill("Created from Playwright");
  await page.getByRole("checkbox", { name: "Development" }).check();
  await page.getByRole("button", { name: "Create draft" }).click();

  await page.waitForURL(/\/admin\/posts\/\d+$/);
  await expect(page.getByLabel("Title")).toHaveValue("Playwright managed draft");
  await expect(page.getByText("Drafts are created in DRAFT state")).not.toBeVisible();

  await page.getByRole("button", { name: "Publish" }).click();
  await expect(page.getByText("Post publish action completed.")).toBeVisible();

  await page.getByLabel("Title").fill("Playwright managed draft updated");
  await page.getByLabel("Revision note").fill("Edited after publish");
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByText("Post changes saved.")).toBeVisible();

  await page.getByRole("button", { name: "Restore this revision" }).nth(1).click();
  await expect(page.getByText("Revision 1 restored.")).toBeVisible();
  await expect(page.getByLabel("Title")).toHaveValue("Playwright managed draft");
});

test("administrator can manage taxonomy and review audit activity", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "Sign in" }).click();

  await page.getByRole("link", { name: "Taxonomy" }).click();
  await page.getByRole("textbox", { name: "Name" }).nth(0).fill("Playwright Category");
  await page.getByRole("textbox", { name: "Slug" }).nth(0).fill("playwright-category");
  await page.getByRole("button", { name: "Create category" }).click();
  await expect(page.getByText("Category changes saved.")).toBeVisible();

  await page.getByRole("link", { name: "Audit" }).click();
  await expect(page.getByRole("heading", { name: "Audit log" })).toBeVisible();
  await expect(page.getByText("CATEGORY_CREATED")).toBeVisible();
});
