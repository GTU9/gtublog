"use client";

import { useEffect, useState } from "react";
import {
  AutomationOriginConflictError,
  createAutomationOriginApproval,
  createAutomationOriginGroup,
  fetchAutomationOriginApprovals,
  fetchAutomationOriginGroups,
  revokeAutomationOriginApproval,
} from "@/src/admin-api";
import type {
  AutomationOriginApprovalResponse,
  AutomationOriginGroupResponse,
  AutomationRunDetailResponse,
  AutomationSourceResponse,
} from "@/src/admin-types";

type AuthenticatedFetch = (input: string, init?: RequestInit) => Promise<Response>;

export function AutomationOriginPanel({ authenticatedFetch, topicId, sources, runDetail }: {
  authenticatedFetch: AuthenticatedFetch;
  topicId: number | null;
  sources: AutomationSourceResponse[];
  runDetail: AutomationRunDetailResponse | null;
}) {
  const [groups, setGroups] = useState<AutomationOriginGroupResponse[]>([]);
  const [approvals, setApprovals] = useState<AutomationOriginApprovalResponse[]>([]);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [sourceId, setSourceId] = useState<number | null>(null);
  const [revokeId, setRevokeId] = useState<number | null>(null);
  const requestKey = topicId === null ? null : `${topicId}:${sources.map((source) => source.id).join(",")}`;
  const loading = requestKey !== null && loadedKey !== requestKey;

  const selectedSource = sources.find((source) => source.id === sourceId) ?? sources[0] ?? null;
  const observedHosts = selectedSource && runDetail
    ? [...new Set(runDetail.snapshots.filter((snapshot) => snapshot.automationSourceId === selectedSource.id)
      .map((snapshot) => snapshot.originHost))]
    : [];

  useEffect(() => {
    let active = true;
    if (topicId === null) return;
    void Promise.all([
      fetchAutomationOriginGroups(authenticatedFetch, topicId),
      Promise.all(sources.map((source) => fetchAutomationOriginApprovals(authenticatedFetch, source.id))),
    ]).then(([nextGroups, approvalLists]) => {
      if (!active) return;
      setGroups(nextGroups);
      setApprovals(approvalLists.flat());
      setError(null);
    }).catch((cause) => {
      if (active) {
        setGroups([]);
        setApprovals([]);
        setError(cause instanceof Error ? cause.message : "승인 이력을 불러오지 못했습니다.");
      }
    }).finally(() => {
      if (active) setLoadedKey(requestKey);
    });
    return () => { active = false; };
  }, [authenticatedFetch, topicId, sources, requestKey]);

  async function refresh() {
    if (topicId === null) return;
    const [nextGroups, approvalLists] = await Promise.all([
      fetchAutomationOriginGroups(authenticatedFetch, topicId),
      Promise.all(sources.map((source) => fetchAutomationOriginApprovals(authenticatedFetch, source.id))),
    ]);
    setGroups(nextGroups);
    setApprovals(approvalLists.flat());
  }

  function reportError(cause: unknown) {
    if (cause instanceof AutomationOriginConflictError) {
      setError(`충돌: ${cause.message} 최신 승인 이력을 확인한 뒤 다시 시도하세요.`);
    } else {
      setError(cause instanceof Error ? cause.message : "요청을 처리하지 못했습니다.");
    }
  }

  async function submitGroup(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (topicId === null) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    setPending(true); setError(null); setMessage(null);
    try {
      await createAutomationOriginGroup(authenticatedFetch, topicId, {
        name: String(data.get("name") ?? "").trim(),
        rationale: String(data.get("rationale") ?? "").trim(),
      });
      await refresh();
      form.reset();
      setMessage("출처 그룹을 기록했습니다.");
    } catch (cause) { reportError(cause); }
    finally { setPending(false); }
  }

  async function submitApproval(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSource) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    setPending(true); setError(null); setMessage(null);
    try {
      await createAutomationOriginApproval(authenticatedFetch, selectedSource.id, {
        originHost: String(data.get("originHost") ?? "").trim(),
        groupId: Number(data.get("groupId")),
        rationale: String(data.get("rationale") ?? "").trim(),
      });
      await refresh();
      form.reset();
      setMessage("선택한 소스의 정확한 기사 호스트 승인을 기록했습니다.");
    } catch (cause) { reportError(cause); }
    finally { setPending(false); }
  }

  async function submitRevocation(event: React.FormEvent<HTMLFormElement>, approval: AutomationOriginApprovalResponse) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setPending(true); setError(null); setMessage(null);
    try {
      await revokeAutomationOriginApproval(authenticatedFetch, approval.id, {
        revision: approval.revision,
        rationale: String(data.get("rationale") ?? "").trim(),
      });
      await refresh();
      setRevokeId(null);
      setMessage("기사 호스트 승인을 취소했습니다.");
    } catch (cause) { reportError(cause); }
    finally { setPending(false); }
  }

  return <section className="admin-card stack" aria-label="기사 출처 승인">
    <div>
      <h2>기사 출처 그룹과 호스트 승인</h2>
      <p>승인은 설정된 소스 ID와 실제 기사 응답의 정확한 호스트에만 적용됩니다. 피드 주소의 호스트만으로 다른 기사 호스트가 승인되지 않습니다.</p>
      <p>이 기록만으로 자동 발행이 허용되지는 않습니다. 현재 자동 발행 보류 판정은 유지됩니다.</p>
    </div>
    {loading ? <p role="status">출처 그룹과 승인 이력을 불러오는 중입니다.</p> : null}
    {error && !loading ? <p className="admin-card-error" role="alert">{error}</p> : null}
    {message ? <p className="admin-card-success" role="status">{message}</p> : null}
    {topicId === null ? <p>먼저 자동화 주제를 선택하세요.</p> : loading ? null : <>
      <form className="stack" onSubmit={(event) => void submitGroup(event)}>
        <h3>출처 그룹 만들기</h3>
        <label className="field">그룹 이름<input name="name" required maxLength={120} /></label>
        <label className="field">그룹 근거<textarea name="rationale" required maxLength={1000} /></label>
        <button type="submit" disabled={pending}>그룹 기록</button>
      </form>
      <div>
        <h3>현재 주제의 그룹</h3>
        {groups.length ? <ul className="admin-list">{groups.map((group) =>
          <li key={group.id}><strong>{group.name}</strong><p>{group.rationale}</p></li>)}</ul> : <p>기록된 그룹이 없습니다.</p>}
      </div>
      <form className="stack" onSubmit={(event) => void submitApproval(event)}>
        <h3>기사 호스트 승인</h3>
        <label className="field">설정된 소스
          <select value={selectedSource?.id ?? ""} onChange={(event) => setSourceId(Number(event.target.value))} disabled={!sources.length}>
            {sources.map((source) => <option key={source.id} value={source.id}>#{source.id} · {source.sourceUrl}</option>)}
          </select>
        </label>
        <label className="field">실제 응답에서 관찰한 기사 호스트
          <input name="originHost" required maxLength={255} placeholder="news.example.org" list="observed-article-hosts" autoComplete="off" />
        </label>
        <datalist id="observed-article-hosts">{observedHosts.map((host) => <option key={host} value={host} />)}</datalist>
        <p>선택한 실행에서 이 소스에 연결된 관찰 호스트: {observedHosts.length ? observedHosts.join(", ") : "없음. 수집 증거에서 호스트를 확인한 후 정확히 입력하세요."}</p>
        <label className="field">출처 그룹
          <select name="groupId" required defaultValue="" key={groups.map((group) => group.id).join(",")}>
            <option value="" disabled>그룹 선택</option>
            {groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
          </select>
        </label>
        <label className="field">승인 근거<textarea name="rationale" required maxLength={1000} /></label>
        <button type="submit" disabled={pending || !selectedSource || !groups.length}>호스트 승인 기록</button>
      </form>
      <div>
        <h3>소스별 승인 이력</h3>
        {sources.map((source) => <section key={source.id} className="stack" aria-label={`소스 ${source.id} 승인 이력`}>
          <h4>#{source.id} · {source.sourceUrl}</h4>
          {approvals.filter((approval) => approval.sourceId === source.id).length ?
            <ul className="admin-list">{approvals.filter((approval) => approval.sourceId === source.id).map((approval) =>
              <li key={approval.id}>
                <strong>{approval.originHost}</strong> · {approval.active ? "활성" : "취소됨"} · {approval.groupName} · revision {approval.revision}
                <p>승인 근거: {approval.rationale}</p>
                {approval.revocationRationale ? <p>취소 근거: {approval.revocationRationale}</p> : null}
                <p>승인: {approval.approvedAt} · 취소: {approval.revokedAt ?? "없음"}</p>
                {approval.active ? <div className="stack">
                  {revokeId === approval.id ? <form className="stack" onSubmit={(event) => void submitRevocation(event, approval)}>
                    <label className="field">취소 근거<textarea name="rationale" required maxLength={1000} /></label>
                    <div className="inline-actions"><button type="submit" disabled={pending}>승인 취소 확정</button>
                      <button type="button" className="secondary-button" onClick={() => setRevokeId(null)}>닫기</button></div>
                  </form> : <button type="button" className="secondary-button" disabled={pending} onClick={() => setRevokeId(approval.id)}>승인 취소</button>}
                </div> : null}
              </li>)}</ul> : <p>승인 이력이 없습니다.</p>}
        </section>)}
      </div>
    </>}
  </section>;
}
