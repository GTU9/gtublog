import type { GenerationProvider, GenerationRequest, GenerationResult } from "./provider.js";

export function createFakeGenerationProvider(name = "fake-provider"): GenerationProvider {
  return {
    name,
    generate(request: GenerationRequest): Promise<GenerationResult> {
      const firstSnapshot = request.snapshots[0];
      const title =
        firstSnapshot?.title?.trim() || `자동 수집 초안 ${request.topicId.toString()}`;
      const excerpt =
        firstSnapshot?.bodyExcerpt?.replace(/\s+/g, " ").trim().slice(0, 140) ||
        "수집된 출처를 바탕으로 작성된 자동 초안입니다.";
      const lines = request.snapshots.map((snapshot) =>
        `- [${snapshot.snapshotId.toString()}] ${snapshot.title ?? snapshot.sourceUrl}`,
      );

      return Promise.resolve({
        title,
        excerpt,
        contentMarkdown: [
          `# ${title}`,
          "",
          excerpt,
          "",
          "## 핵심 출처",
          ...lines,
          "",
          "## 초안",
          request.prompt,
        ].join("\n"),
        citationSnapshotIds: request.snapshots.map((snapshot) => snapshot.snapshotId),
        provider: name,
      });
    },
  };
}
