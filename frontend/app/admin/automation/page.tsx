"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import {
  cancelAutomationRun,
  deleteAutomationSchedule,
  deleteAutomationSource,
  fetchAutomationDiagnostics,
  fetchAutomationOutbox,
  fetchAutomationRunDetail,
  fetchAutomationRuns,
  fetchAutomationSchedules,
  fetchAutomationSources,
  fetchAutomationTopics,
  overridePublishAutomationRun,
  processAutomationOutbox,
  retryAutomationRun,
  saveAutomationSchedule,
  saveAutomationSource,
  saveAutomationTopic,
  triggerAutomationRun,
} from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AutomationControlCenter } from "@/src/automation-admin-ui";
import { AdminPageHeader, LoadingCard, MessageCard } from "@/src/admin-ui";
import type {
  AutomationDiagnosticsResponse,
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationSourceResponse,
  AutomationTopicResponse,
} from "@/src/admin-types";

export default function AdminAutomationPage() {
  const auth = useAdminAuth();
  const [topicForm, setTopicForm] = useState({
    editingId: null as number | null,
    name: "",
    slug: "",
    promptTemplateVersion: "v1",
    publicationEnabled: true,
    saving: false,
  });
  const [sourceForm, setSourceForm] = useState({
    editingId: null as number | null,
    sourceType: "RSS",
    sourceUrl: "",
    enabled: true,
    saving: false,
  });
  const [scheduleForm, setScheduleForm] = useState({
    editingId: null as number | null,
    name: "",
    cronExpression: "0 0 9 * * *",
    timezone: "Asia/Seoul",
    status: "ACTIVE",
    misfirePolicy: "FIRE_ONCE_NOW",
    saving: false,
  });
  const [topics, setTopics] = useState<AutomationTopicResponse[]>([]);
  const [selectedTopicId, setSelectedTopicId] = useState<number | null>(null);
  const [sources, setSources] = useState<AutomationSourceResponse[]>([]);
  const [schedules, setSchedules] = useState<AutomationScheduleResponse[]>([]);
  const [runs, setRuns] = useState<AutomationRunResponse[]>([]);
  const [runDetail, setRunDetail] = useState<AutomationRunDetailResponse | null>(null);
  const [outbox, setOutbox] = useState<AutomationOutboxResponse[]>([]);
  const [diagnostics, setDiagnostics] = useState<AutomationDiagnosticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [actionPending, setActionPending] = useState<null | "retry" | "cancel" | "override">(null);
  const [processingOutboxState, setProcessingOutboxState] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const effectiveTopicId = useMemo(() => selectedTopicId ?? topics[0]?.id ?? null, [selectedTopicId, topics]);

  function resetTopicForm() {
    setTopicForm({
      editingId: null,
      name: "",
      slug: "",
      promptTemplateVersion: "v1",
      publicationEnabled: true,
      saving: false,
    });
  }

  function resetSourceForm() {
    setSourceForm({
      editingId: null,
      sourceType: "RSS",
      sourceUrl: "",
      enabled: true,
      saving: false,
    });
  }

  function resetScheduleForm() {
    setScheduleForm({
      editingId: null,
      name: "",
      cronExpression: "0 0 9 * * *",
      timezone: "Asia/Seoul",
      status: "ACTIVE",
      misfirePolicy: "FIRE_ONCE_NOW",
      saving: false,
    });
  }

  const refreshTopics = useCallback(async (preferredTopicId?: number) => {
    const nextTopics = await fetchAutomationTopics(auth.authenticatedFetch);
    setTopics(nextTopics);
    const nextSelectedTopicId = preferredTopicId ?? selectedTopicId ?? nextTopics[0]?.id ?? null;
    setSelectedTopicId(nextSelectedTopicId);
    return nextSelectedTopicId;
  }, [auth.authenticatedFetch, selectedTopicId]);

  const refreshTopicDetails = useCallback(async (topicId: number | null) => {
    if (topicId == null) {
      setSources([]);
      setSchedules([]);
      return;
    }

    const [nextSources, nextSchedules] = await Promise.all([
      fetchAutomationSources(auth.authenticatedFetch, topicId),
      fetchAutomationSchedules(auth.authenticatedFetch, topicId),
    ]);
    setSources(nextSources);
    setSchedules(nextSchedules);
  }, [auth.authenticatedFetch]);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    void (async () => {
      try {
        const [nextTopics, nextRuns, nextOutbox, nextDiagnostics] = await Promise.all([
          fetchAutomationTopics(auth.authenticatedFetch),
          fetchAutomationRuns(auth.authenticatedFetch),
          fetchAutomationOutbox(auth.authenticatedFetch),
          fetchAutomationDiagnostics(auth.authenticatedFetch),
        ]);
        setTopics(nextTopics);
        setRuns(nextRuns);
        setOutbox(nextOutbox);
        setDiagnostics(nextDiagnostics);
        setSelectedTopicId((current) => current ?? nextTopics[0]?.id ?? null);
        if (nextRuns[0]) {
          setRunDetail(await fetchAutomationRunDetail(auth.authenticatedFetch, nextRuns[0].id));
        }
      } catch {
        setError("자동화 운영 화면을 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    })();
  }, [auth]);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    let active = true;

    void (async () => {
      try {
        await refreshTopicDetails(effectiveTopicId);
      } catch {
        if (active) {
          setError("선택한 주제의 자동화 세부 정보를 불러오지 못했습니다.");
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [auth.authenticatedFetch, auth.status, effectiveTopicId, refreshTopicDetails]);

  async function refreshRunsAndOutbox(preferredRunId?: number) {
    const [nextRuns, nextOutbox, nextDiagnostics] = await Promise.all([
      fetchAutomationRuns(auth.authenticatedFetch),
      fetchAutomationOutbox(auth.authenticatedFetch),
      fetchAutomationDiagnostics(auth.authenticatedFetch),
    ]);
    setRuns(nextRuns);
    setOutbox(nextOutbox);
    setDiagnostics(nextDiagnostics);
    const runId = preferredRunId ?? nextRuns[0]?.id;
    if (runId) {
      setRunDetail(await fetchAutomationRunDetail(auth.authenticatedFetch, runId));
    }
  }

  async function handleTriggerRun(topicId: number) {
    setRunning(true);
    setMessage(null);
    setError(null);
    try {
      const run = await triggerAutomationRun(auth.authenticatedFetch, topicId);
      await refreshRunsAndOutbox(run.id);
      setMessage("자동화 실행을 시작했고 최신 실행 결과를 불러왔습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 실행을 시작하지 못했습니다.");
    } finally {
      setRunning(false);
    }
  }

  async function handleSelectRun(runId: number) {
    setMessage(null);
    setError(null);
    try {
      setRunDetail(await fetchAutomationRunDetail(auth.authenticatedFetch, runId));
    } catch (error) {
      setError(error instanceof Error ? error.message : "선택한 실행 상세 정보를 불러오지 못했습니다.");
    }
  }

  async function handleProcessOutbox() {
    setProcessingOutboxState(true);
    setMessage(null);
    setError(null);
    try {
      await processAutomationOutbox(auth.authenticatedFetch);
      await refreshRunsAndOutbox(runDetail?.run.id);
      setMessage("대기 중이던 발행 복구 이벤트를 다시 처리했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "발행 복구 이벤트를 처리하지 못했습니다.");
    } finally {
      setProcessingOutboxState(false);
    }
  }

  async function handleRetryRun(runId: number) {
    setActionPending("retry");
    setMessage(null);
    setError(null);
    try {
      const run = await retryAutomationRun(auth.authenticatedFetch, runId);
      await refreshRunsAndOutbox(run.id);
      setMessage("보류된 실행을 재시도했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "보류된 실행을 재시도하지 못했습니다.");
    } finally {
      setActionPending(null);
    }
  }

  async function handleCancelRun(runId: number) {
    setActionPending("cancel");
    setMessage(null);
    setError(null);
    try {
      const run = await cancelAutomationRun(auth.authenticatedFetch, runId);
      await refreshRunsAndOutbox(run.id);
      setMessage("실행 중인 자동화를 취소했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 실행을 취소하지 못했습니다.");
    } finally {
      setActionPending(null);
    }
  }

  async function handleOverridePublish(runId: number) {
    setActionPending("override");
    setMessage(null);
    setError(null);
    try {
      const result = await overridePublishAutomationRun(auth.authenticatedFetch, runId);
      await refreshRunsAndOutbox(runId);
      setMessage(`보류된 초안을 수동 발행했습니다. 게시글 슬러그: ${result.slug}`);
    } catch (error) {
      setError(error instanceof Error ? error.message : "보류된 초안을 수동 발행하지 못했습니다.");
    } finally {
      setActionPending(null);
    }
  }

  function editTopic(topic: AutomationTopicResponse) {
    setSelectedTopicId(topic.id);
    setTopicForm({
      editingId: topic.id,
      name: topic.name,
      slug: topic.slug,
      promptTemplateVersion: topic.promptTemplateVersion,
      publicationEnabled: topic.publicationEnabled,
      saving: false,
    });
  }

  function editSource(source: AutomationSourceResponse) {
    setSourceForm({
      editingId: source.id,
      sourceType: source.sourceType,
      sourceUrl: source.sourceUrl,
      enabled: source.enabled,
      saving: false,
    });
  }

  function editSchedule(schedule: AutomationScheduleResponse) {
    setScheduleForm({
      editingId: schedule.id,
      name: schedule.name,
      cronExpression: schedule.cronExpression,
      timezone: schedule.timezone,
      status: schedule.status,
      misfirePolicy: schedule.misfirePolicy,
      saving: false,
    });
  }

  async function handleTopicSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    setError(null);
    setMessage(null);
    setTopicForm((current) => ({ ...current, saving: true }));
    try {
      const saved = await saveAutomationTopic(auth.authenticatedFetch, topicForm.editingId, {
        name: String(formData.get("name") ?? ""),
        slug: String(formData.get("slug") ?? ""),
        promptTemplateVersion: String(formData.get("promptTemplateVersion") ?? ""),
        publicationEnabled: formData.get("publicationEnabled") === "on",
      });
      const nextTopicId = await refreshTopics(saved.id);
      await refreshTopicDetails(nextTopicId);
      resetTopicForm();
      setMessage(topicForm.editingId ? "자동화 주제를 수정했습니다." : "자동화 주제를 생성했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 주제를 저장하지 못했습니다.");
      setTopicForm((current) => ({ ...current, saving: false }));
      return;
    }
    setTopicForm((current) => ({ ...current, saving: false }));
  }

  async function handleSourceSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (effectiveTopicId == null) {
      setError("소스를 저장하기 전에 주제를 먼저 선택하세요.");
      return;
    }
    const formData = new FormData(event.currentTarget);
    setError(null);
    setMessage(null);
    setSourceForm((current) => ({ ...current, saving: true }));
    try {
      await saveAutomationSource(auth.authenticatedFetch, effectiveTopicId, sourceForm.editingId, {
        sourceType: String(formData.get("sourceType") ?? "RSS"),
        sourceUrl: String(formData.get("sourceUrl") ?? ""),
        enabled: formData.get("enabled") === "on",
      });
      await refreshTopicDetails(effectiveTopicId);
      resetSourceForm();
      setMessage(sourceForm.editingId ? "자동화 소스를 수정했습니다." : "자동화 소스를 추가했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 소스를 저장하지 못했습니다.");
      setSourceForm((current) => ({ ...current, saving: false }));
      return;
    }
    setSourceForm((current) => ({ ...current, saving: false }));
  }

  async function handleScheduleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (effectiveTopicId == null) {
      setError("스케줄을 저장하기 전에 주제를 먼저 선택하세요.");
      return;
    }
    const formData = new FormData(event.currentTarget);
    setError(null);
    setMessage(null);
    setScheduleForm((current) => ({ ...current, saving: true }));
    try {
      await saveAutomationSchedule(auth.authenticatedFetch, effectiveTopicId, scheduleForm.editingId, {
        name: String(formData.get("name") ?? ""),
        cronExpression: String(formData.get("cronExpression") ?? ""),
        timezone: String(formData.get("timezone") ?? ""),
        status: String(formData.get("status") ?? "ACTIVE"),
        misfirePolicy: String(formData.get("misfirePolicy") ?? "FIRE_ONCE_NOW"),
      });
      await refreshTopicDetails(effectiveTopicId);
      resetScheduleForm();
      setMessage(scheduleForm.editingId ? "자동화 스케줄을 수정했습니다." : "자동화 스케줄을 추가했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 스케줄을 저장하지 못했습니다.");
      setScheduleForm((current) => ({ ...current, saving: false }));
      return;
    }
    setScheduleForm((current) => ({ ...current, saving: false }));
  }

  async function handleDeleteSource(sourceId: number) {
    setError(null);
    setMessage(null);
    try {
      await deleteAutomationSource(auth.authenticatedFetch, sourceId);
      await refreshTopicDetails(effectiveTopicId);
      if (sourceForm.editingId === sourceId) {
        resetSourceForm();
      }
      setMessage("자동화 소스를 삭제했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 소스를 삭제하지 못했습니다.");
    }
  }

  async function handleDeleteSchedule(scheduleId: number) {
    setError(null);
    setMessage(null);
    try {
      await deleteAutomationSchedule(auth.authenticatedFetch, scheduleId);
      await refreshTopicDetails(effectiveTopicId);
      if (scheduleForm.editingId === scheduleId) {
        resetScheduleForm();
      }
      setMessage("자동화 스케줄을 삭제했습니다.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "자동화 스케줄을 삭제하지 못했습니다.");
    }
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title="자동화 운영"
        description="수집 실행 상태를 추적하고, 보류 사유를 검토하고, 복구 이벤트를 다시 처리하는 관리자 화면입니다."
      />
      {error ? <MessageCard title="자동화 화면 오류" description={error} tone="error" /> : null}
      {message ? <MessageCard title="자동화 작업 완료" description={message} tone="success" /> : null}
      {loading ? (
        <LoadingCard message="자동화 운영 화면을 불러오는 중입니다..." />
      ) : (
        <AutomationControlCenter
          diagnostics={diagnostics}
          topics={topics}
          selectedTopicId={effectiveTopicId}
          sources={sources}
          schedules={schedules}
          runs={runs}
          runDetail={runDetail}
          outbox={outbox}
          running={running}
          actionPending={actionPending}
          processingOutbox={processingOutboxState}
          topicForm={topicForm}
          sourceForm={sourceForm}
          scheduleForm={scheduleForm}
          onSelectTopic={setSelectedTopicId}
          onTopicSubmit={(event) => void handleTopicSubmit(event)}
          onTopicEdit={editTopic}
          onTopicReset={resetTopicForm}
          onSourceSubmit={(event) => void handleSourceSubmit(event)}
          onSourceEdit={editSource}
          onSourceDelete={(sourceId) => void handleDeleteSource(sourceId)}
          onSourceReset={resetSourceForm}
          onScheduleSubmit={(event) => void handleScheduleSubmit(event)}
          onScheduleEdit={editSchedule}
          onScheduleDelete={(scheduleId) => void handleDeleteSchedule(scheduleId)}
          onScheduleReset={resetScheduleForm}
          onTriggerRun={handleTriggerRun}
          onSelectRun={handleSelectRun}
          onRetryRun={handleRetryRun}
          onCancelRun={handleCancelRun}
          onOverridePublish={handleOverridePublish}
          onProcessOutbox={handleProcessOutbox}
        />
      )}
    </section>
  );
}
