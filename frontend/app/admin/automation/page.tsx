"use client";

import { useEffect, useMemo, useState } from "react";

import {
  fetchAutomationOutbox,
  fetchAutomationRunDetail,
  fetchAutomationRuns,
  fetchAutomationSchedules,
  fetchAutomationSources,
  fetchAutomationTopics,
  processAutomationOutbox,
  triggerAutomationRun,
} from "@/src/admin-api";
import { useAdminAuth } from "@/src/admin-auth";
import { AdminPageHeader, AutomationOverview, LoadingCard, MessageCard } from "@/src/admin-ui";
import type {
  AutomationOutboxResponse,
  AutomationRunDetailResponse,
  AutomationRunResponse,
  AutomationScheduleResponse,
  AutomationSourceResponse,
  AutomationTopicResponse,
} from "@/src/admin-types";

export default function AdminAutomationPage() {
  const auth = useAdminAuth();
  const [topics, setTopics] = useState<AutomationTopicResponse[]>([]);
  const [selectedTopicId, setSelectedTopicId] = useState<number | null>(null);
  const [sources, setSources] = useState<AutomationSourceResponse[]>([]);
  const [schedules, setSchedules] = useState<AutomationScheduleResponse[]>([]);
  const [runs, setRuns] = useState<AutomationRunResponse[]>([]);
  const [runDetail, setRunDetail] = useState<AutomationRunDetailResponse | null>(null);
  const [outbox, setOutbox] = useState<AutomationOutboxResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [processingOutboxState, setProcessingOutboxState] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const effectiveTopicId = useMemo(() => selectedTopicId ?? topics[0]?.id ?? null, [selectedTopicId, topics]);

  useEffect(() => {
    if (auth.status !== "authenticated") {
      return;
    }

    void (async () => {
      try {
        const [nextTopics, nextRuns, nextOutbox] = await Promise.all([
          fetchAutomationTopics(auth.authenticatedFetch),
          fetchAutomationRuns(auth.authenticatedFetch),
          fetchAutomationOutbox(auth.authenticatedFetch),
        ]);
        setTopics(nextTopics);
        setRuns(nextRuns);
        setOutbox(nextOutbox);
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
    if (auth.status !== "authenticated" || effectiveTopicId == null) {
      return;
    }

    void Promise.all([
      fetchAutomationSources(auth.authenticatedFetch, effectiveTopicId).then(setSources),
      fetchAutomationSchedules(auth.authenticatedFetch, effectiveTopicId).then(setSchedules),
    ]).catch(() => setError("Unable to load topic automation details."));
  }, [auth, effectiveTopicId]);

  async function refreshRunsAndOutbox(preferredRunId?: number) {
    const [nextRuns, nextOutbox] = await Promise.all([
      fetchAutomationRuns(auth.authenticatedFetch),
      fetchAutomationOutbox(auth.authenticatedFetch),
    ]);
    setRuns(nextRuns);
    setOutbox(nextOutbox);
    const runId = preferredRunId ?? nextRuns[0]?.id;
    if (runId) {
      setRunDetail(await fetchAutomationRunDetail(auth.authenticatedFetch, runId));
    }
  }

  async function handleTriggerRun(topicId: number) {
    setRunning(true);
    setMessage(null);
    try {
      const run = await triggerAutomationRun(auth.authenticatedFetch, topicId);
      await refreshRunsAndOutbox(run.id);
      setMessage("Automation run completed and the latest result has been loaded.");
    } catch {
      setError("Unable to trigger the automation run.");
    } finally {
      setRunning(false);
    }
  }

  async function handleSelectRun(runId: number) {
    setMessage(null);
    try {
      setRunDetail(await fetchAutomationRunDetail(auth.authenticatedFetch, runId));
    } catch {
      setError("Unable to load the selected run detail.");
    }
  }

  async function handleProcessOutbox() {
    setProcessingOutboxState(true);
    setMessage(null);
    try {
      await processAutomationOutbox(auth.authenticatedFetch);
      await refreshRunsAndOutbox(runDetail?.run.id);
      setMessage("Pending publication recovery events were replayed.");
    } catch {
      setError("Unable to process publication recovery events.");
    } finally {
      setProcessingOutboxState(false);
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
        <AutomationOverview
          topics={topics}
          selectedTopicId={effectiveTopicId}
          sources={sources}
          schedules={schedules}
          runs={runs}
          runDetail={runDetail}
          outbox={outbox}
          running={running}
          processingOutbox={processingOutboxState}
          onSelectTopic={setSelectedTopicId}
          onTriggerRun={handleTriggerRun}
          onSelectRun={handleSelectRun}
          onProcessOutbox={handleProcessOutbox}
        />
      )}
    </section>
  );
}
