import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createAgentAnalysis,
  getAgentAnalysis,
  listAgentAnalyses,
  type AgentTask,
} from './api';

export interface AgentPanelLabels {
  goalPlaceholder: string;
  ask: string;
  asking: string;
  history: string;
  plan: string;
  conclusions: string;
  evidence: string;
  suggestions: string;
  passed: string;
  reserved: string; // Critic 未通过但仍返回结果时的"有保留"标记
  failed: string;
  empty: string;
  processing: string;
}

const POLL_MS = 2500;

function formatMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  const mm = String(Math.floor(total / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

export default function AgentPanel({ videoId, labels }: { videoId: number; labels: AgentPanelLabels }) {
  const [goal, setGoal] = useState('');
  const [tasks, setTasks] = useState<AgentTask[]>([]);
  const [active, setActive] = useState<AgentTask | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<number | null>(null);

  const refreshList = useCallback(async () => {
    try {
      const list = await listAgentAnalyses(videoId);
      setTasks(list);
      setActive((prev) => prev ?? list[0] ?? null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [videoId]);

  useEffect(() => {
    setActive(null);
    setError(null);
    void refreshList();
  }, [refreshList]);

  // 活动任务处于 PENDING/PROCESSING 时轮询,终态自动停(与工作台 2.4s 轮询同思路)
  useEffect(() => {
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (!active || (active.status !== 'PENDING' && active.status !== 'PROCESSING')) return;
    timerRef.current = window.setInterval(async () => {
      try {
        const fresh = await getAgentAnalysis(active.id);
        setActive(fresh);
        setTasks((prev) => prev.map((t) => (t.id === fresh.id ? fresh : t)));
      } catch (e) {
        setError((e as Error).message);
      }
    }, POLL_MS);
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
    };
  }, [active]);

  const ask = async () => {
    const trimmed = goal.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const task = await createAgentAnalysis(videoId, trimmed);
      setGoal('');
      setActive(task);
      setTasks((prev) => [task, ...prev.filter((t) => t.id !== task.id)]);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const running = active && (active.status === 'PENDING' || active.status === 'PROCESSING');

  return (
    <div className="agent-panel">
      <div className="agent-ask-row">
        <textarea
          className="agent-goal-input"
          value={goal}
          placeholder={labels.goalPlaceholder}
          maxLength={500}
          rows={2}
          onChange={(e) => setGoal(e.target.value)}
        />
        <button className="agent-ask-btn" disabled={submitting || !goal.trim()} onClick={() => void ask()}>
          {submitting ? labels.asking : labels.ask}
        </button>
      </div>

      {error && <div className="agent-error">{error}</div>}

      {tasks.length > 1 && (
        <div className="agent-history">
          <span className="agent-history-label">{labels.history}</span>
          {tasks.map((t) => (
            <button
              key={t.id}
              className={`agent-history-item${active?.id === t.id ? ' active' : ''}`}
              onClick={() => setActive(t)}
            >
              {t.goal.length > 24 ? `${t.goal.slice(0, 24)}…` : t.goal}
            </button>
          ))}
        </div>
      )}

      {!active && <div className="agent-empty">{labels.empty}</div>}

      {running && (
        <div className="agent-processing">
          <span className="vi-spin">⟳</span> {labels.processing}
        </div>
      )}

      {active?.status === 'FAILED' && (
        <div className="agent-error">{labels.failed}{active.errorMessage ? `: ${active.errorMessage}` : ''}</div>
      )}

      {active?.status === 'COMPLETED' && active.answer && (
        <div className="agent-result">
          <div className="agent-result-head">
            <h4>{active.answer.title}</h4>
            <span className={`agent-badge${active.critic?.passed ? ' pass' : ' warn'}`}>
              {active.critic?.passed ? labels.passed : labels.reserved}
              {active.roundCount ? ` · R${active.roundCount}` : ''}
            </span>
          </div>

          {active.plan && (
            <section>
              <h5>{labels.plan}</h5>
              <ol>{active.plan.tasks.map((t, i) => <li key={i}>{t}</li>)}</ol>
            </section>
          )}

          <section>
            <h5>{labels.conclusions}</h5>
            <ul>{active.answer.conclusions.map((c, i) => <li key={i}>{c}</li>)}</ul>
          </section>

          <section>
            <h5>{labels.evidence}</h5>
            <ul className="agent-evidence-list">
              {active.answer.evidence.map((e, i) => (
                <li key={i}>
                  <span className="agent-ts">[{formatMs(e.timestampMs)}]</span>
                  <span className="agent-src">{e.source}</span>
                  <span>{e.content}</span>
                </li>
              ))}
            </ul>
          </section>

          {active.answer.suggestions.length > 0 && (
            <section>
              <h5>{labels.suggestions}</h5>
              <ul>{active.answer.suggestions.map((s, i) => <li key={i}>{s}</li>)}</ul>
            </section>
          )}
        </div>
      )}
    </div>
  );
}
