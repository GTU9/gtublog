# Story 26 automatic taxonomy context

Task: Continue the approved functionality-recovery roadmap after Story 25. Generated posts currently publish without categories or tags; implement automatic taxonomy without weakening Spring's publication authority.

Outcome: An automatic post receives one administrator-controlled category and at least one administrator-controlled tag selected by generation, with category/tag joins committed atomically with publication. Missing or invalid taxonomy produces a reasoned held run, never uncategorized auto-publication.

Evidence: `.omx/specs/deep-interview-automated-content-blog.md:61` requires category/tags in generated content; `.omx/plans/prd-automated-content-blog.md:228` requires taxonomy in atomic publication; `.omx/plans/test-spec-automated-content-blog.md:29` requires transactional evidence. Current v2 claim/submit and worker draft contain no taxonomy; `AutomationPublicationService.publishDraft` creates no join rows. Manual `PostService` links existing category/tag IDs.

Constraints: Spring/MySQL are authority; worker untrusted; versioned cross-process contracts; no new dependency; preserve v2 files and legacy submission idempotency; one story branch from `prototype` merge 7076404; issue before commit; Korean UTF-8 GitHub text; full required gates and independent verification.

Open decisions: bounded catalog size, legacy v2 job transition, empty/invalid taxonomy hold reason, exact admin review presentation. Architect recommends immutable v3 job contract and claim-scoped existing-taxonomy ID catalog, with no worker-driven creation.

Likely touchpoints: `contracts/automation/v3`, `generation-worker/src/{provider,draft-schema,runtime,terminal}.ts`, backend `GenerationJob*`, `AutomationPublicationService`, `AutomationAdminService`, `AutomationRunDetailResponse`, taxonomy repositories, backend/worker/contract/Playwright tests, release docs.
