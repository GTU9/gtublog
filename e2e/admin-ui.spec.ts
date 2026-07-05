import { expect, test } from "@playwright/test";

test("administrator can sign in, create a draft, publish it, and restore a revision", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "로그인" }).click();

  await expect(page.getByRole("heading", { name: "대시보드" })).toBeVisible();

  await page.getByRole("link", { name: "글 관리" }).click();
  await page.getByRole("link", { name: "새 글 작성" }).click();

  await page.getByLabel("제목").fill("Playwright managed draft");
  await page.getByLabel("요약").fill("A draft authored through the admin flow.");
  await page.getByLabel("Markdown").fill("First revision body\n\nWith preview content.");
  await page.getByLabel("리비전 메모").fill("Created from Playwright");
  await page.getByRole("checkbox", { name: "Development" }).check();
  await page.getByRole("button", { name: "초안 저장" }).click();

  await page.waitForURL(/\/admin\/posts\/\d+$/);
  await expect(page.getByLabel("제목")).toHaveValue("Playwright managed draft");
  await expect(page.getByText("새 글은 초안 상태로 저장되며, 상세 편집기에서 발행할 수 있습니다.")).not.toBeVisible();

  await page.getByRole("button", { name: "발행" }).click();
  await expect(page.getByText("발행 작업을 완료했습니다.")).toBeVisible();

  await page.getByLabel("제목").fill("Playwright managed draft updated");
  await page.getByLabel("리비전 메모").fill("Edited after publish");
  await page.getByRole("button", { name: "변경 저장" }).click();
  await expect(page.getByText("글 변경 사항을 저장했습니다.")).toBeVisible();

  await page.getByRole("button", { name: "이 리비전 복원" }).nth(1).click();
  await expect(page.getByText("1번 리비전을 복원했습니다.")).toBeVisible();
  await expect(page.getByLabel("제목")).toHaveValue("Playwright managed draft");
});

test("administrator can manage taxonomy and review audit activity", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "로그인" }).click();

  await page.getByRole("link", { name: "분류 관리" }).click();
  await expect(page.getByRole("heading", { name: "분류 관리" })).toBeVisible();

  await page.getByRole("link", { name: "감사 로그" }).click();
  await expect(page.getByRole("heading", { name: "감사 로그" })).toBeVisible();
});

test("administrator can manage automation configuration and see safe conflict guidance", async ({ page }) => {
  await page.goto("/admin/login");
  await page.getByRole("button", { name: "로그인" }).click();

  await page.getByRole("link", { name: "자동화 운영" }).click();
  await expect(page.getByRole("heading", { name: "자동화 운영", exact: true })).toBeVisible();

  const defaultSourcesPanel = page.getByLabel("자동화 소스 패널");
  await defaultSourcesPanel.getByRole("button", { name: "삭제" }).first().click({ force: true });
  await expect(page.getByRole("heading", { name: "자동화 화면 오류", exact: true })).toBeVisible();
  await expect(page.getByText("이 소스는 이미 수집 증거에 연결되어 있습니다. 삭제 대신 비활성화하세요.", { exact: true })).toBeVisible();

  const defaultSchedulesPanel = page.getByLabel("자동화 스케줄 패널");
  await defaultSchedulesPanel.getByRole("button", { name: "삭제" }).first().click({ force: true });
  await expect(page.getByText("이 스케줄에는 이미 실행 이력이 있습니다. 삭제 대신 비활성화하세요.", { exact: true })).toBeVisible();

  await page.getByRole("textbox", { name: "주제명" }).fill("Playwright Automation Topic");
  await page.getByRole("textbox", { name: "슬러그" }).fill("playwright-automation-topic");
  await page.getByRole("textbox", { name: "프롬프트 템플릿 버전" }).fill("v2");
  await page.getByRole("button", { name: "주제 생성" }).click();
  await expect(page.getByRole("button", { name: "Playwright Automation Topic" })).toBeVisible();

  await page.getByRole("button", { name: "Playwright Automation Topic" }).click();

  const sourcesPanel = page.getByLabel("자동화 소스 패널");
  await sourcesPanel.getByRole("combobox", { name: "소스 유형" }).selectOption("HTML");
  await sourcesPanel.getByRole("textbox", { name: "소스 URL" }).fill("https://playwright.example.com/feed");
  await sourcesPanel.getByRole("button", { name: "소스 추가" }).click();
  await expect(sourcesPanel.getByText("https://playwright.example.com/feed")).toBeVisible();

  const schedulesPanel = page.getByLabel("자동화 스케줄 패널");
  await schedulesPanel.getByRole("textbox", { name: "스케줄명" }).fill("Playwright schedule");
  await schedulesPanel.getByRole("textbox", { name: "Cron 표현식" }).fill("0 15 10 * * *");
  await schedulesPanel.getByRole("textbox", { name: "시간대" }).fill("Asia/Seoul");
  await schedulesPanel.getByRole("button", { name: "스케줄 추가" }).click();
  await expect(schedulesPanel.getByText("Playwright schedule")).toBeVisible();

  await page.getByRole("button", { name: "상세보기" }).first().click();
  await expect(page.getByRole("heading", { name: "선택한 실행 상세" })).toBeVisible();
  await expect(page.getByText("운영 진단 요약")).toBeVisible();
});
