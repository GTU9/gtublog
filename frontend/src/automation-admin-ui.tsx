"use client";

import type {
  AutomationDiagnosticsResponse,
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationSourceResponse,
  AutomationTopicResponse,
} from "@/src/admin-types";

function formatDateTime(value: string | null) {
  if (!value) {
    return "없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatRunStatus(status: string) {
  switch (status) {
    case "PENDING":
      return "대기";
    case "RUNNING":
      return "실행 중";
    case "SUCCEEDED":
      return "성공";
    case "HELD":
      return "보류";
    case "FAILED":
      return "실패";
    default:
      return status;
  }
}

function formatTriggerType(triggerType: string) {
  switch (triggerType) {
    case "MANUAL":
      return "수동 실행";
    case "SCHEDULED":
      return "예약 실행";
    default:
      return triggerType;
  }
}

function formatResolutionStatus(status: string | null) {
  switch (status) {
    case null:
      return null;
    case "RETRIED":
      return "재시도 처리됨";
    case "CANCELLED":
      return "관리자 취소";
    case "OVERRIDE_PUBLISHED":
      return "수동 발행 완료";
    default:
      return status;
  }
}

function formatPolicyResult(policyResult: string) {
  switch (policyResult) {
    case "ALLOWED":
      return "허용";
    case "HELD":
      return "보류";
    case "REJECTED":
      return "차단";
    default:
      return policyResult;
  }
}

function formatScheduleStatus(status: string) {
  switch (status) {
    case "ACTIVE":
      return "활성";
    case "PAUSED":
      return "일시 중지";
    case "DISABLED":
      return "비활성";
    default:
      return status;
  }
}

function formatMisfirePolicy(policy: string) {
  switch (policy) {
    case "FIRE_ONCE_NOW":
      return "놓친 실행 1회 즉시 보정";
    case "DO_NOTHING":
      return "놓친 실행 건너뜀";
    default:
      return policy;
  }
}

function formatOutboxStatus(status: string) {
  switch (status) {
    case "PENDING":
      return "대기";
    case "DELIVERED":
      return "전달 완료";
    default:
      return status;
  }
}

function buildRunGuidance(runDetail: AutomationRunDetailResponse) {
  if (runDetail.run.resolutionStatus === "OVERRIDE_PUBLISHED") {
    return "보류 초안이 관리자 판단으로 수동 발행된 상태입니다. 이후 공개 글과 감사 로그를 함께 확인하세요.";
  }
  if (runDetail.run.resolutionStatus === "RETRIED") {
    return "이 실행은 이미 재시도 요청을 보냈습니다. 새 실행 결과에서 보류 사유가 해소됐는지 확인하세요.";
  }
  if (runDetail.run.resolutionStatus === "CANCELLED") {
    return "관리자가 실행을 중단한 상태입니다. 작업 중단 사유와 후속 재실행 필요 여부를 확인하세요.";
  }
  if (runDetail.run.status === "HELD") {
    return "보류 상태입니다. 보류 사유를 먼저 읽고, 재시도와 수동 발행 중 어떤 조치가 맞는지 판단하세요.";
  }
  if (runDetail.run.status === "RUNNING") {
    return "실행이 진행 중입니다. 아직 최종 발행 판단이 끝나지 않았으므로 필요 시에만 취소하세요.";
  }
  if (runDetail.run.status === "SUCCEEDED") {
    return "정상 완료된 실행입니다. 발행 반영과 아웃박스 전달 상태까지 함께 확인하면 운영 추적이 쉬워집니다.";
  }
  return "실행 결과와 후속 조치를 함께 검토하세요.";
}

export function AutomationControlCenter({
  diagnostics,
  topics,
  selectedTopicId,
  sources,
  schedules,
  runs,
  runDetail,
  outbox,
  running,
  actionPending,
  processingOutbox,
  topicForm,
  sourceForm,
  scheduleForm,
  onSelectTopic,
  onTopicSubmit,
  onTopicEdit,
  onTopicReset,
  onSourceSubmit,
  onSourceEdit,
  onSourceDelete,
  onSourceReset,
  onScheduleSubmit,
  onScheduleEdit,
  onScheduleDelete,
  onScheduleReset,
  onTriggerRun,
  onSelectRun,
  onRetryRun,
  onCancelRun,
  onOverridePublish,
  onProcessOutbox,
}: {
  diagnostics: AutomationDiagnosticsResponse | null;
  topics: AutomationTopicResponse[];
  selectedTopicId: number | null;
  sources: AutomationSourceResponse[];
  schedules: AutomationScheduleResponse[];
  runs: AutomationRunResponse[];
  runDetail: AutomationRunDetailResponse | null;
  outbox: AutomationOutboxResponse[];
  running: boolean;
  actionPending: null | "retry" | "cancel" | "override";
  processingOutbox: boolean;
  topicForm: {
    editingId: number | null;
    name: string;
    slug: string;
    promptTemplateVersion: string;
    publicationEnabled: boolean;
    saving: boolean;
  };
  sourceForm: {
    editingId: number | null;
    sourceType: string;
    sourceUrl: string;
    enabled: boolean;
    saving: boolean;
  };
  scheduleForm: {
    editingId: number | null;
    name: string;
    cronExpression: string;
    timezone: string;
    status: string;
    misfirePolicy: string;
    saving: boolean;
  };
  onSelectTopic: (topicId: number) => void;
  onTopicSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onTopicEdit: (topic: AutomationTopicResponse) => void;
  onTopicReset: () => void;
  onSourceSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onSourceEdit: (source: AutomationSourceResponse) => void;
  onSourceDelete: (sourceId: number) => void;
  onSourceReset: () => void;
  onScheduleSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onScheduleEdit: (schedule: AutomationScheduleResponse) => void;
  onScheduleDelete: (scheduleId: number) => void;
  onScheduleReset: () => void;
  onTriggerRun: (topicId: number) => void;
  onSelectRun: (runId: number) => void;
  onRetryRun: (runId: number) => void;
  onCancelRun: (runId: number) => void;
  onOverridePublish: (runId: number) => void;
  onProcessOutbox: () => void;
}) {
  const selectedTopic = topics.find((topic) => topic.id === selectedTopicId) ?? null;
  const uniqueOriginHosts = runDetail ? new Set(runDetail.snapshots.map((snapshot) => snapshot.originHost)).size : 0;
  const citationCount = runDetail?.generatedDraft?.citationSnapshotIds.length ?? 0;

  return (
    <div className="stack">
      {diagnostics ? (
        <div className="admin-stats">
          <article className="admin-card">
            <h3>성공한 실행</h3>
            <p className="stat-value">{diagnostics.runCounts.succeeded}</p>
          </article>
          <article className="admin-card">
            <h3>보류된 실행</h3>
            <p className="stat-value">{diagnostics.runCounts.held}</p>
          </article>
          <article className="admin-card">
            <h3>대기 중 작업</h3>
            <p className="stat-value">{diagnostics.jobCounts.pending}</p>
          </article>
          <article className="admin-card">
            <h3>대기 중 아웃박스</h3>
            <p className="stat-value">{diagnostics.outboxCounts.pending}</p>
          </article>
        </div>
      ) : null}

      <div className="admin-grid">
        <article className="admin-card stack" aria-label="자동화 주제 패널">
          <div className="inline-actions">
            <h3>자동화 주제</h3>
            {selectedTopicId ? (
              <button type="button" onClick={() => onTriggerRun(selectedTopicId)} disabled={running}>
                {running ? "실행 중..." : "지금 실행"}
              </button>
            ) : null}
          </div>
          <p className="muted">자동 수집/작성 주제를 만들고, 해당 주제에 소스와 스케줄을 연결하세요.</p>
          <form className="stack" onSubmit={onTopicSubmit}>
            <label className="field">
              <span>주제명</span>
              <input name="name" defaultValue={topicForm.name} key={`topic-name-${topicForm.editingId ?? "new"}-${topicForm.name}`} required />
            </label>
            <label className="field">
              <span>슬러그</span>
              <input
                name="slug"
                defaultValue={topicForm.slug}
                key={`topic-slug-${topicForm.editingId ?? "new"}-${topicForm.slug}`}
                placeholder="비워두면 자동 생성"
              />
            </label>
            <label className="field">
              <span>프롬프트 템플릿 버전</span>
              <input
                name="promptTemplateVersion"
                defaultValue={topicForm.promptTemplateVersion}
                key={`topic-prompt-${topicForm.editingId ?? "new"}-${topicForm.promptTemplateVersion}`}
                required
              />
            </label>
            <label className="checkbox-item">
              <input
                type="checkbox"
                name="publicationEnabled"
                defaultChecked={topicForm.publicationEnabled}
                key={`topic-publication-${topicForm.editingId ?? "new"}-${topicForm.publicationEnabled}`}
              />
              <span>모든 검증 통과 시 자동 발행 허용</span>
            </label>
            <div className="inline-actions">
              <button type="submit" disabled={topicForm.saving}>
                {topicForm.saving ? "저장 중..." : topicForm.editingId ? "주제 수정" : "주제 생성"}
              </button>
              {topicForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onTopicReset}>
                  편집 취소
                </button>
              ) : null}
            </div>
          </form>
          <ul className="admin-list">
            {topics.map((topic) => (
              <li key={topic.id}>
                <div>
                  <button type="button" className="link-button" onClick={() => onSelectTopic(topic.id)}>
                    {topic.name}
                  </button>
                  <p className="muted">
                    {topic.slug} · {topic.publicationEnabled ? "자동 발행 켜짐" : "자동 발행 꺼짐"} · 프롬프트 {topic.promptTemplateVersion}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onTopicEdit(topic)}>
                    수정
                  </button>
                  {selectedTopicId === topic.id ? (
                    <span className="status-pill">선택됨</span>
                  ) : (
                    <button type="button" className="secondary-button" onClick={() => onSelectTopic(topic.id)}>
                      선택
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </article>

        <article className="admin-card stack" aria-label="자동화 소스 패널">
          <h3>소스</h3>
          <p className="muted">
            {selectedTopic ? `선택한 주제: ${selectedTopic.name}` : "소스를 편집하려면 먼저 주제를 선택하세요."}
          </p>
          <form className="stack" onSubmit={onSourceSubmit}>
            <label className="field">
              <span>소스 유형</span>
              <select
                name="sourceType"
                defaultValue={sourceForm.sourceType}
                key={`source-type-${sourceForm.editingId ?? "new"}-${sourceForm.sourceType}`}
                disabled={!selectedTopicId}
              >
                <option value="RSS">RSS</option>
                <option value="HTML">HTML</option>
              </select>
            </label>
            <label className="field">
              <span>소스 URL</span>
              <input
                name="sourceUrl"
                type="url"
                defaultValue={sourceForm.sourceUrl}
                key={`source-url-${sourceForm.editingId ?? "new"}-${sourceForm.sourceUrl}`}
                placeholder="https://example.com/feed.xml"
                required
                disabled={!selectedTopicId}
              />
            </label>
            <label className="checkbox-item">
              <input
                type="checkbox"
                name="enabled"
                defaultChecked={sourceForm.enabled}
                key={`source-enabled-${sourceForm.editingId ?? "new"}-${sourceForm.enabled}`}
                disabled={!selectedTopicId}
              />
              <span>수집 대상으로 사용</span>
            </label>
            <div className="inline-actions">
              <button type="submit" disabled={!selectedTopicId || sourceForm.saving}>
                {sourceForm.saving ? "저장 중..." : sourceForm.editingId ? "소스 수정" : "소스 추가"}
              </button>
              {sourceForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onSourceReset}>
                  편집 취소
                </button>
              ) : null}
            </div>
          </form>
          <ul className="admin-list">
            {sources.map((source) => (
              <li key={source.id}>
                <div>
                  <strong>{source.sourceType}</strong>
                  <p className="muted">
                    {source.sourceUrl} · {source.enabled ? "활성" : "비활성"}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onSourceEdit(source)}>
                    수정
                  </button>
                  <button type="button" className="danger-button" onClick={() => onSourceDelete(source.id)}>
                    삭제
                  </button>
                </div>
              </li>
            ))}
            {sources.length === 0 ? <li className="muted">이 주제에 연결된 소스가 없습니다.</li> : null}
          </ul>
        </article>

        <article className="admin-card stack" aria-label="자동화 스케줄 패널">
          <h3>스케줄</h3>
          <p className="muted">Cron 표현식과 IANA 시간대를 사용합니다. 실행 이력이 생기면 삭제 대신 비활성화를 권장합니다.</p>
          <form className="stack" onSubmit={onScheduleSubmit}>
            <label className="field">
              <span>스케줄명</span>
              <input name="name" defaultValue={scheduleForm.name} key={`schedule-name-${scheduleForm.editingId ?? "new"}-${scheduleForm.name}`} required disabled={!selectedTopicId} />
            </label>
            <label className="field">
              <span>Cron 표현식</span>
              <input
                name="cronExpression"
                defaultValue={scheduleForm.cronExpression}
                key={`schedule-cron-${scheduleForm.editingId ?? "new"}-${scheduleForm.cronExpression}`}
                placeholder="0 0 9 * * *"
                required
                disabled={!selectedTopicId}
              />
            </label>
            <label className="field">
              <span>시간대</span>
              <input
                name="timezone"
                defaultValue={scheduleForm.timezone}
                key={`schedule-timezone-${scheduleForm.editingId ?? "new"}-${scheduleForm.timezone}`}
                placeholder="Asia/Seoul"
                required
                disabled={!selectedTopicId}
              />
            </label>
            <label className="field">
              <span>상태</span>
              <select
                name="status"
                defaultValue={scheduleForm.status}
                key={`schedule-status-${scheduleForm.editingId ?? "new"}-${scheduleForm.status}`}
                disabled={!selectedTopicId}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="PAUSED">PAUSED</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </label>
            <label className="field">
              <span>미스파이어 정책</span>
              <select
                name="misfirePolicy"
                defaultValue={scheduleForm.misfirePolicy}
                key={`schedule-misfire-${scheduleForm.editingId ?? "new"}-${scheduleForm.misfirePolicy}`}
                disabled={!selectedTopicId}
              >
                <option value="FIRE_ONCE_NOW">FIRE_ONCE_NOW</option>
                <option value="DO_NOTHING">DO_NOTHING</option>
              </select>
            </label>
            <div className="inline-actions">
              <button type="submit" disabled={!selectedTopicId || scheduleForm.saving}>
                {scheduleForm.saving ? "저장 중..." : scheduleForm.editingId ? "스케줄 수정" : "스케줄 추가"}
              </button>
              {scheduleForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onScheduleReset}>
                  편집 취소
                </button>
              ) : null}
            </div>
          </form>
          <ul className="admin-list">
            {schedules.map((schedule) => (
              <li key={schedule.id}>
                <div>
                  <strong>{schedule.name}</strong>
                  <p className="muted">
                    {schedule.cronExpression} · {schedule.timezone} · {formatScheduleStatus(schedule.status)} · 다음 실행 {formatDateTime(schedule.nextPlannedRunAt)}
                  </p>
                  <p className="muted">미스파이어 처리: {formatMisfirePolicy(schedule.misfirePolicy)}</p>
                  <p className={`muted ${schedule.syncStatus === "OUT_OF_SYNC" ? "error-text" : ""}`}>
                    {schedule.syncStatus === "OUT_OF_SYNC"
                      ? schedule.syncErrorMessage ?? "Quartz 동기화가 어긋난 상태입니다."
                      : `Quartz 동기화 시각 ${formatDateTime(schedule.lastSynchronizedAt)}`}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onScheduleEdit(schedule)}>
                    수정
                  </button>
                  <button type="button" className="danger-button" onClick={() => onScheduleDelete(schedule.id)}>
                    삭제
                  </button>
                </div>
              </li>
            ))}
            {schedules.length === 0 ? <li className="muted">이 주제에 연결된 스케줄이 없습니다.</li> : null}
          </ul>
        </article>
      </div>

      <div className="admin-grid admin-grid-wide">
        <article className="admin-card stack">
          <h3>최근 실행 이력</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>실행 키</th>
                <th>상태</th>
                <th>트리거</th>
                <th>스냅샷</th>
                <th>완료 시각</th>
                <th>동작</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id}>
                  <td>{run.runKey}</td>
                  <td>
                    <span className="status-pill">{formatRunStatus(run.status)}</span>
                  </td>
                  <td>{formatTriggerType(run.triggerType)}</td>
                  <td>{run.snapshotCount}</td>
                  <td>{formatDateTime(run.completedAt)}</td>
                  <td>
                    <button type="button" className="secondary-button" onClick={() => onSelectRun(run.id)}>
                      상세보기
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>

        <article className="admin-card stack">
          <h3>선택한 실행 상세</h3>
          {runDetail ? (
            <>
              <p className="muted">
                {formatRunStatus(runDetail.run.status)}
                {runDetail.run.holdReason ? ` · ${runDetail.run.holdReason}` : ""}
                {formatResolutionStatus(runDetail.run.resolutionStatus) ? ` · 처리 상태 ${formatResolutionStatus(runDetail.run.resolutionStatus)}` : ""}
              </p>
              <p className="muted">{buildRunGuidance(runDetail)}</p>
              <div className="inline-actions">
                <button type="button" className="secondary-button" disabled={!runDetail.availableActions.canRetry || actionPending !== null} onClick={() => onRetryRun(runDetail.run.id)}>
                  {actionPending === "retry" ? "재시도 중..." : "재시도"}
                </button>
                <button type="button" className="secondary-button" disabled={!runDetail.availableActions.canCancel || actionPending !== null} onClick={() => onCancelRun(runDetail.run.id)}>
                  {actionPending === "cancel" ? "취소 중..." : "실행 취소"}
                </button>
                <button type="button" disabled={!runDetail.availableActions.canOverridePublish || actionPending !== null} onClick={() => onOverridePublish(runDetail.run.id)}>
                  {actionPending === "override" ? "발행 중..." : "수동 발행"}
                </button>
              </div>
              {runDetail.generatedDraft ? (
                <article className="admin-card stack">
                  <h4>저장된 생성 초안</h4>
                  <p><strong>{runDetail.generatedDraft.title}</strong></p>
                  <p className="muted">{runDetail.generatedDraft.excerpt}</p>
                  <p className="muted">
                    인용 스냅샷 {citationCount}건 · 독립 출처 호스트 {uniqueOriginHosts}개
                  </p>
                  <pre className="code-block">{runDetail.generatedDraft.contentMarkdown}</pre>
                </article>
              ) : null}
              <ul className="admin-list">
                {runDetail.snapshots.map((snapshot) => (
                  <li key={snapshot.id}>
                    <strong>{snapshot.title}</strong>
                    <span className="muted">
                      {snapshot.originHost} · {formatPolicyResult(snapshot.policyResult)} · HTTP {snapshot.httpStatus} · 수집 {formatDateTime(snapshot.retrievedAt)}
                    </span>
                    <span className="muted">{snapshot.canonicalUrl}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted">실행을 선택하면 수집한 소스, 보류 사유, 사용 가능한 후속 조치를 볼 수 있습니다.</p>
          )}
        </article>
      </div>

      {diagnostics ? (
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>운영 진단 요약</h3>
            <span className="muted">생성 시각 {formatDateTime(diagnostics.generatedAt)}</span>
          </div>
          <ul className="admin-list">
            <li>
              <strong>보류된 소스 스냅샷</strong>
              <span className="muted">{diagnostics.heldSnapshotCount}</span>
            </li>
            <li>
              <strong>제출된 작업</strong>
              <span className="muted">{diagnostics.jobCounts.submitted}</span>
            </li>
            <li>
              <strong>실패한 작업</strong>
              <span className="muted">{diagnostics.jobCounts.failed}</span>
            </li>
            <li>
              <strong>전달 완료 아웃박스</strong>
              <span className="muted">{diagnostics.outboxCounts.delivered}</span>
            </li>
          </ul>
          <div>
            <h4>최근 보류 사유</h4>
            <ul className="admin-list">
              {diagnostics.recentHoldReasons.length > 0 ? (
                diagnostics.recentHoldReasons.map((reason, index) => <li key={`${reason}-${index}`}>{reason}</li>)
              ) : (
                <li className="muted">최근 보류 사유가 없습니다.</li>
              )}
            </ul>
          </div>
        </article>
      ) : null}

      <article className="admin-card stack">
        <div className="inline-actions">
          <h3>발행 복구 아웃박스</h3>
          <button type="button" onClick={onProcessOutbox} disabled={processingOutbox}>
            {processingOutbox ? "처리 중..." : "대기 이벤트 재처리"}
          </button>
        </div>
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>게시글</th>
              <th>상태</th>
              <th>처리 가능 시각</th>
              <th>마지막 시도</th>
              <th>처리 완료</th>
            </tr>
          </thead>
          <tbody>
            {outbox.map((event) => (
              <tr key={event.id}>
                <td>{event.id}</td>
                <td>{event.aggregateId}</td>
                <td>
                  <span className="status-pill">{formatOutboxStatus(event.deliveryStatus)}</span>
                </td>
                <td>{formatDateTime(event.availableAt)}</td>
                <td>{formatDateTime(event.lastAttemptAt)}</td>
                <td>{formatDateTime(event.processedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
    </div>
  );
}
