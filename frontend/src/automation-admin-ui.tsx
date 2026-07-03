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
    return "Not scheduled";
  }

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
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
  onProcessOutbox: () => void;
}) {
  const selectedTopic = topics.find((topic) => topic.id === selectedTopicId) ?? null;

  return (
    <div className="stack">
      {diagnostics ? (
        <div className="admin-stats">
          <article className="admin-card">
            <h3>Runs succeeded</h3>
            <p className="stat-value">{diagnostics.runCounts.succeeded}</p>
          </article>
          <article className="admin-card">
            <h3>Runs held</h3>
            <p className="stat-value">{diagnostics.runCounts.held}</p>
          </article>
          <article className="admin-card">
            <h3>Jobs pending</h3>
            <p className="stat-value">{diagnostics.jobCounts.pending}</p>
          </article>
          <article className="admin-card">
            <h3>Outbox pending</h3>
            <p className="stat-value">{diagnostics.outboxCounts.pending}</p>
          </article>
        </div>
      ) : null}

      <div className="admin-grid">
        <article className="admin-card stack" aria-label="Automation topics panel">
          <div className="inline-actions">
            <h3>Automation topics</h3>
            {selectedTopicId ? (
              <button type="button" onClick={() => onTriggerRun(selectedTopicId)} disabled={running}>
                {running ? "Running..." : "Run now"}
              </button>
            ) : null}
          </div>
          <p className="muted">Create topics for automated post families, then attach source coverage and schedules to the selected topic.</p>
          <form className="stack" onSubmit={onTopicSubmit}>
            <label className="field">
              <span>Name</span>
              <input name="name" defaultValue={topicForm.name} key={`topic-name-${topicForm.editingId ?? "new"}-${topicForm.name}`} required />
            </label>
            <label className="field">
              <span>Slug</span>
              <input
                name="slug"
                defaultValue={topicForm.slug}
                key={`topic-slug-${topicForm.editingId ?? "new"}-${topicForm.slug}`}
                placeholder="leave blank to auto-generate"
              />
            </label>
            <label className="field">
              <span>Prompt template version</span>
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
              <span>Allow automatic publication when all gates pass</span>
            </label>
            <div className="inline-actions">
              <button type="submit" disabled={topicForm.saving}>
                {topicForm.saving ? "Saving..." : topicForm.editingId ? "Update topic" : "Create topic"}
              </button>
              {topicForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onTopicReset}>
                  Clear editing state
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
                    {topic.slug} - {topic.publicationEnabled ? "publication on" : "publication off"} - prompt {topic.promptTemplateVersion}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onTopicEdit(topic)}>
                    Edit
                  </button>
                  {selectedTopicId === topic.id ? (
                    <span className="status-pill">Selected</span>
                  ) : (
                    <button type="button" className="secondary-button" onClick={() => onSelectTopic(topic.id)}>
                      Select
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </article>

        <article className="admin-card stack" aria-label="Automation sources panel">
          <h3>Sources</h3>
          <p className="muted">
            {selectedTopic ? `Selected topic: ${selectedTopic.name}` : "Select a topic before editing source coverage."}
          </p>
          <form className="stack" onSubmit={onSourceSubmit}>
            <label className="field">
              <span>Source type</span>
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
              <span>Source URL</span>
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
              <span>Enabled for collection</span>
            </label>
            <div className="inline-actions">
              <button type="submit" disabled={!selectedTopicId || sourceForm.saving}>
                {sourceForm.saving ? "Saving..." : sourceForm.editingId ? "Update source" : "Add source"}
              </button>
              {sourceForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onSourceReset}>
                  Clear editing state
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
                    {source.sourceUrl} - {source.enabled ? "enabled" : "disabled"}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onSourceEdit(source)}>
                    Edit
                  </button>
                  <button type="button" className="danger-button" onClick={() => onSourceDelete(source.id)}>
                    Delete
                  </button>
                </div>
              </li>
            ))}
            {sources.length === 0 ? <li className="muted">No sources for this topic.</li> : null}
          </ul>
        </article>

        <article className="admin-card stack" aria-label="Automation schedules panel">
          <h3>Schedules</h3>
          <p className="muted">Use cron plus an IANA timezone. Once a schedule has history, disable it instead of deleting it.</p>
          <form className="stack" onSubmit={onScheduleSubmit}>
            <label className="field">
              <span>Name</span>
              <input name="name" defaultValue={scheduleForm.name} key={`schedule-name-${scheduleForm.editingId ?? "new"}-${scheduleForm.name}`} required disabled={!selectedTopicId} />
            </label>
            <label className="field">
              <span>Cron expression</span>
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
              <span>Timezone</span>
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
              <span>Status</span>
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
              <span>Misfire policy</span>
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
                {scheduleForm.saving ? "Saving..." : scheduleForm.editingId ? "Update schedule" : "Add schedule"}
              </button>
              {scheduleForm.editingId ? (
                <button type="button" className="secondary-button" onClick={onScheduleReset}>
                  Clear editing state
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
                    {schedule.cronExpression} - {schedule.timezone} - {schedule.status} - next {formatDateTime(schedule.nextPlannedRunAt)}
                  </p>
                  <p className={`muted ${schedule.syncStatus === "OUT_OF_SYNC" ? "error-text" : ""}`}>
                    {schedule.syncStatus === "OUT_OF_SYNC"
                      ? schedule.syncErrorMessage ?? "Quartz synchronization is currently out of sync."
                      : `Quartz synchronized at ${formatDateTime(schedule.lastSynchronizedAt)}`}
                  </p>
                </div>
                <div className="inline-actions">
                  <button type="button" className="secondary-button" onClick={() => onScheduleEdit(schedule)}>
                    Edit
                  </button>
                  <button type="button" className="danger-button" onClick={() => onScheduleDelete(schedule.id)}>
                    Delete
                  </button>
                </div>
              </li>
            ))}
            {schedules.length === 0 ? <li className="muted">No schedules for this topic.</li> : null}
          </ul>
        </article>
      </div>

      <div className="admin-grid admin-grid-wide">
        <article className="admin-card stack">
          <h3>Recent runs</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Run</th>
                <th>Status</th>
                <th>Trigger</th>
                <th>Snapshots</th>
                <th>Completed</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id}>
                  <td>{run.runKey}</td>
                  <td>
                    <span className="status-pill">{run.status}</span>
                  </td>
                  <td>{run.triggerType}</td>
                  <td>{run.snapshotCount}</td>
                  <td>{formatDateTime(run.completedAt)}</td>
                  <td>
                    <button type="button" className="secondary-button" onClick={() => onSelectRun(run.id)}>
                      Inspect
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>

        <article className="admin-card stack">
          <h3>Selected run detail</h3>
          {runDetail ? (
            <>
              <p className="muted">
                {runDetail.run.status}
                {runDetail.run.holdReason ? ` - ${runDetail.run.holdReason}` : ""}
              </p>
              <ul className="admin-list">
                {runDetail.snapshots.map((snapshot) => (
                  <li key={snapshot.id}>
                    <strong>{snapshot.title}</strong>
                    <span className="muted">
                      {snapshot.originHost} - {snapshot.policyResult} - {snapshot.canonicalUrl}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted">Select a run to inspect collected sources and hold reasons.</p>
          )}
        </article>
      </div>

      {diagnostics ? (
        <article className="admin-card stack">
          <div className="inline-actions">
            <h3>Operational diagnostics</h3>
            <span className="muted">Generated {formatDateTime(diagnostics.generatedAt)}</span>
          </div>
          <ul className="admin-list">
            <li>
              <strong>Held source snapshots</strong>
              <span className="muted">{diagnostics.heldSnapshotCount}</span>
            </li>
            <li>
              <strong>Jobs submitted</strong>
              <span className="muted">{diagnostics.jobCounts.submitted}</span>
            </li>
            <li>
              <strong>Jobs failed</strong>
              <span className="muted">{diagnostics.jobCounts.failed}</span>
            </li>
            <li>
              <strong>Outbox delivered</strong>
              <span className="muted">{diagnostics.outboxCounts.delivered}</span>
            </li>
          </ul>
          <div>
            <h4>Recent hold reasons</h4>
            <ul className="admin-list">
              {diagnostics.recentHoldReasons.length > 0 ? (
                diagnostics.recentHoldReasons.map((reason, index) => <li key={`${reason}-${index}`}>{reason}</li>)
              ) : (
                <li className="muted">No recent hold reasons.</li>
              )}
            </ul>
          </div>
        </article>
      ) : null}

      <article className="admin-card stack">
        <div className="inline-actions">
          <h3>Publication recovery outbox</h3>
          <button type="button" onClick={onProcessOutbox} disabled={processingOutbox}>
            {processingOutbox ? "Processing..." : "Replay pending events"}
          </button>
        </div>
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Post</th>
              <th>Status</th>
              <th>Available</th>
              <th>Last attempt</th>
              <th>Processed</th>
            </tr>
          </thead>
          <tbody>
            {outbox.map((event) => (
              <tr key={event.id}>
                <td>{event.id}</td>
                <td>{event.aggregateId}</td>
                <td>
                  <span className="status-pill">{event.deliveryStatus}</span>
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
