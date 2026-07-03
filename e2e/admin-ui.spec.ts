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
  await expect(page.getByRole("heading", { name: "Taxonomy" })).toBeVisible();

  await page.getByRole("link", { name: "Audit" }).click();
  await expect(page.getByRole("heading", { name: "Audit log" })).toBeVisible();
});

test("administrator can manage automation configuration and see safe conflict guidance", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "Sign in" }).click();

  await page.getByRole("link", { name: "Automation" }).click();
  await expect(page.getByRole("heading", { name: "Automation", exact: true })).toBeVisible();

  const defaultSourcesPanel = page.getByLabel("Automation sources panel");
  await defaultSourcesPanel.getByRole("button", { name: "Delete" }).first().click({ force: true });
  await expect(page.getByRole("heading", { name: "Automation unavailable", exact: true })).toBeVisible();
  await expect(page.getByText("This source is already referenced by collected evidence. Disable it instead of deleting it.", { exact: true })).toBeVisible();

  const defaultSchedulesPanel = page.getByLabel("Automation schedules panel");
  await defaultSchedulesPanel.getByRole("button", { name: "Delete" }).first().click({ force: true });
  await expect(page.getByText("This schedule already has run history. Disable it instead of deleting it.", { exact: true })).toBeVisible();

  await page.getByRole("textbox", { name: "Name" }).nth(0).fill("Playwright Automation Topic");
  await page.getByRole("textbox", { name: "Slug" }).nth(0).fill("playwright-automation-topic");
  await page.getByRole("textbox", { name: "Prompt template version" }).fill("v2");
  await page.getByRole("button", { name: "Create topic" }).click();
  await expect(page.getByRole("button", { name: "Playwright Automation Topic" })).toBeVisible();

  await page.getByRole("button", { name: "Playwright Automation Topic" }).click();

  const sourcesPanel = page.getByLabel("Automation sources panel");
  await sourcesPanel.getByRole("combobox", { name: "Source type" }).selectOption("HTML");
  await sourcesPanel.getByRole("textbox", { name: "Source URL" }).fill("https://playwright.example.com/feed");
  await sourcesPanel.getByRole("button", { name: "Add source" }).click();
  await expect(sourcesPanel.getByText("https://playwright.example.com/feed")).toBeVisible();

  const schedulesPanel = page.getByLabel("Automation schedules panel");
  await schedulesPanel.getByRole("textbox", { name: "Name" }).fill("Playwright schedule");
  await schedulesPanel.getByRole("textbox", { name: "Cron expression" }).fill("0 15 10 * * *");
  await schedulesPanel.getByRole("textbox", { name: "Timezone" }).fill("Asia/Seoul");
  await schedulesPanel.getByRole("button", { name: "Add schedule" }).click();
  await expect(schedulesPanel.getByText("Playwright schedule")).toBeVisible();
});
