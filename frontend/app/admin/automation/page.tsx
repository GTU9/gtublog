"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import {
  deleteAutomationSchedule,
  deleteAutomationSource,
  fetchAutomationDiagnostics,
  fetchAutomationOutbox,
  fetchAutomationRunDetail,
  fetchAutomationRuns,
  fetchAutomationSchedules,
  fetchAutomationSources,
  fetchAutomationTopics,
  processAutomationOutbox,
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
        setError("Unable to load automation controls right now.");
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
          setError("Unable to load topic automation details.");
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
      setMessage("Automation run completed and the latest result has been loaded.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to trigger the automation run.");
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
      setError(error instanceof Error ? error.message : "Unable to load the selected run detail.");
    }
  }

  async function handleProcessOutbox() {
    setProcessingOutboxState(true);
    setMessage(null);
    setError(null);
    try {
      await processAutomationOutbox(auth.authenticatedFetch);
      await refreshRunsAndOutbox(runDetail?.run.id);
      setMessage("Pending publication recovery events were replayed.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to process publication recovery events.");
    } finally {
      setProcessingOutboxState(false);
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
      setMessage(topicForm.editingId ? "Automation topic updated." : "Automation topic created.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to save the automation topic.");
      setTopicForm((current) => ({ ...current, saving: false }));
      return;
    }
    setTopicForm((current) => ({ ...current, saving: false }));
  }

  async function handleSourceSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (effectiveTopicId == null) {
      setError("Select a topic before saving sources.");
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
      setMessage(sourceForm.editingId ? "Automation source updated." : "Automation source added.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to save the automation source.");
      setSourceForm((current) => ({ ...current, saving: false }));
      return;
    }
    setSourceForm((current) => ({ ...current, saving: false }));
  }

  async function handleScheduleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (effectiveTopicId == null) {
      setError("Select a topic before saving schedules.");
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
      setMessage(scheduleForm.editingId ? "Automation schedule updated." : "Automation schedule added.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to save the automation schedule.");
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
      setMessage("Automation source deleted.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to delete the automation source.");
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
      setMessage("Automation schedule deleted.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Unable to delete the automation schedule.");
    }
  }

  return (
    <section className="stack">
      <AdminPageHeader
        title="Automation"
        description="Monitor collection runs, inspect publication holds, and replay recovery events from one administrator surface."
      />
      {error ? <MessageCard title="Automation unavailable" description={error} tone="error" /> : null}
      {message ? <MessageCard title="Automation updated" description={message} tone="success" /> : null}
      {loading ? (
        <LoadingCard message="Loading automation controls..." />
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
          onProcessOutbox={handleProcessOutbox}
        />
      )}
    </section>
  );
}
