import { expect, test } from "@playwright/test";

const applicationApiBaseUrl = (
  process.env.NEXT_PUBLIC_GTUBLOG_APPLICATION_API_BASE_URL
  ?? process.env.GTUBLOG_APPLICATION_API_BASE_URL
  ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/, "");

test("administrator can sign in, create a draft, publish it, and restore a revision", async ({ page }) => {
  test.setTimeout(60_000);
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
  await page.route(`${applicationApiBaseUrl}/**`, (route) => route.abort("connectionrefused"));
  await page.goto("/admin/automation");
  await expect(page.getByRole("heading", { name: "자동화 운영", exact: true })).toBeVisible();

  const defaultSourcesPanel = page.getByLabel("자동화 소스 패널");
  const defaultSchedulesPanel = page.getByLabel("자동화 스케줄 패널");
  await expect(defaultSourcesPanel).toBeVisible();
  await expect(defaultSchedulesPanel).toBeVisible();
  const [sourceBox, scheduleBox] = await Promise.all([
    defaultSourcesPanel.boundingBox(),
    defaultSchedulesPanel.boundingBox(),
  ]);
  expect(sourceBox).not.toBeNull();
  expect(scheduleBox).not.toBeNull();
  const panelsOverlap = sourceBox!.x < scheduleBox!.x + scheduleBox!.width
    && sourceBox!.x + sourceBox!.width > scheduleBox!.x
    && sourceBox!.y < scheduleBox!.y + scheduleBox!.height
    && sourceBox!.y + sourceBox!.height > scheduleBox!.y;
  expect(panelsOverlap).toBe(false);

  await defaultSourcesPanel.getByRole("button", { name: "삭제" }).first().click();
  await expect(page.getByRole("heading", { name: "자동화 화면 오류", exact: true })).toBeVisible();
  await expect(page.getByText("이 소스는 이미 수집 증거에 연결되어 있습니다. 삭제 대신 비활성화하세요.", { exact: true })).toBeVisible();

  await defaultSchedulesPanel.getByRole("button", { name: "삭제" }).first().click();
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

  await page.getByRole("row").filter({ hasText: "run-302" }).getByRole("button", { name: "상세보기" }).click();
  await expect(page.getByText("핵심 주장별 근거가 검증되지 않아 자동 발행을 보류했습니다.")).toBeVisible();
});

test("administrator reviews observed article hosts and records, conflicts, then revokes an approval", async ({ page }) => {
  await page.route(`${applicationApiBaseUrl}/**`, (route) => route.abort("connectionrefused"));
  await page.goto("/admin/automation");

  const panel = page.getByLabel("기사 출처 승인");
  await expect(panel.getByRole("heading", { name: "기사 출처 그룹과 호스트 승인" })).toBeVisible();
  await expect(panel.getByText("이 기록만으로 자동 발행이 허용되지는 않습니다.", { exact: false })).toBeVisible();
  await expect(panel.locator("#observed-article-hosts option[value='articles.example.org']")).toHaveCount(1);
  await expect(panel.getByText("선택한 실행에서 이 소스에 연결된 관찰 호스트: articles.example.org")).toBeVisible();

  await panel.getByRole("textbox", { name: "그룹 이름" }).fill("Independent desk");
  await panel.getByRole("textbox", { name: "그룹 근거" }).fill("Editorial ownership reviewed");
  await panel.getByRole("button", { name: "그룹 기록" }).click();
  await expect(panel.getByText("Editorial ownership reviewed")).toBeVisible();
  await panel.getByRole("textbox", { name: "그룹 이름" }).fill("Syndication bureau");
  await panel.getByRole("textbox", { name: "그룹 근거" }).fill("Separate desk and ownership");
  await panel.getByRole("button", { name: "그룹 기록" }).click();
  await expect(panel.getByText("Separate desk and ownership")).toBeVisible();

  await panel.getByRole("combobox", { name: "설정된 소스" }).selectOption("101");
  await panel.getByLabel("실제 응답에서 관찰한 기사 호스트").fill("articles.example.org");
  await panel.getByRole("combobox", { name: "출처 그룹" }).selectOption({ label: "Independent desk" });
  await panel.getByRole("textbox", { name: "승인 근거", exact: true }).fill("Original article ownership checked");
  await panel.getByRole("button", { name: "호스트 승인 기록" }).click();
  const history = panel.getByLabel("소스 101 승인 이력");
  await expect(history.getByText("Original article ownership checked", { exact: false })).toBeVisible();
  await expect(history.getByText(/articles\.example\.org.*활성.*Independent desk.*revision 1/)).toBeVisible();

  const approvalRoute = `${applicationApiBaseUrl}/admin/automation/sources/101/origin-approvals`;
  await page.route(approvalRoute, (route) => route.fulfill({
    status: 409,
    contentType: "application/problem+json",
    body: JSON.stringify({ detail: "이미 활성 승인된 기사 호스트입니다." }),
  }));
  await panel.getByLabel("실제 응답에서 관찰한 기사 호스트").fill("articles.example.org");
  await panel.getByRole("combobox", { name: "출처 그룹" }).selectOption({ label: "Independent desk" });
  await panel.getByRole("textbox", { name: "승인 근거", exact: true }).fill("Duplicate attempt");
  await panel.getByRole("button", { name: "호스트 승인 기록" }).click();
  await expect(panel.getByRole("alert")).toContainText("충돌:");
  await expect(panel.getByRole("alert")).toContainText("최신 승인 이력을 확인한 뒤 다시 시도하세요.");
  await page.unroute(approvalRoute);

  await history.getByRole("button", { name: "승인 취소", exact: true }).click();
  await history.getByRole("textbox", { name: "취소 근거" }).fill("Ownership changed");
  await history.getByRole("button", { name: "승인 취소 확정" }).click();
  await expect(history.getByText(/articles\.example\.org.*취소됨.*Independent desk.*revision 2/)).toBeVisible();
  await expect(history.getByText("승인 근거: Original article ownership checked")).toBeVisible();
  await expect(history.getByText("취소 근거: Ownership changed")).toBeVisible();

  await panel.getByRole("combobox", { name: "첫 번째 그룹" }).selectOption({ label: "Syndication bureau" });
  await panel.getByRole("combobox", { name: "두 번째 그룹" }).selectOption({ label: "Independent desk" });
  await panel.getByRole("textbox", { name: "독립성 승인 근거" }).fill("No shared editorial control");
  await panel.getByRole("button", { name: "그룹 쌍 승인 기록" }).click();
  const pairHistory = panel.getByText("그룹 쌍 승인 이력").locator("..");
  await expect(pairHistory.getByText(/Independent desk.*Syndication bureau.*활성.*revision 1/)).toBeVisible();
  await expect(pairHistory.getByText("승인 근거: No shared editorial control")).toBeVisible();

  const pairRoute = `${applicationApiBaseUrl}/admin/automation/topics/1/origin-pairs`;
  await page.route(pairRoute, (route) => route.fulfill({
    status: 409,
    contentType: "application/problem+json",
    body: JSON.stringify({ detail: "이미 활성 승인된 그룹 쌍입니다." }),
  }));
  await panel.getByRole("combobox", { name: "첫 번째 그룹" }).selectOption({ label: "Independent desk" });
  await panel.getByRole("combobox", { name: "두 번째 그룹" }).selectOption({ label: "Syndication bureau" });
  await panel.getByRole("textbox", { name: "독립성 승인 근거" }).fill("Duplicate pair");
  await panel.getByRole("button", { name: "그룹 쌍 승인 기록" }).click();
  await expect(panel.getByRole("alert")).toContainText("충돌:");
  await page.unroute(pairRoute);

  await page.getByRole("button", { name: "지금 실행" }).click();
  await expect(panel.getByText(/승인 #.*revision 1.*Independent desk.*Syndication bureau/)).toBeVisible();

  await pairHistory.getByRole("button", { name: "그룹 쌍 승인 취소", exact: true }).click();
  await pairHistory.getByRole("textbox", { name: "그룹 쌍 취소 근거" }).fill("Shared upstream discovered");
  await pairHistory.getByRole("button", { name: "그룹 쌍 승인 취소 확정" }).click();
  await expect(pairHistory.getByText(/Independent desk.*Syndication bureau.*취소됨.*revision 2/)).toBeVisible();
  await expect(pairHistory.getByText("취소 근거: Shared upstream discovered")).toBeVisible();
  await expect(panel.getByText(/승인 #.*revision 1.*Independent desk.*Syndication bureau/)).toBeVisible();
});
