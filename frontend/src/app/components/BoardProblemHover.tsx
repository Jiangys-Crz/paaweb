'use client';

import { useCallback, useEffect, useRef, useState, type FocusEvent, type MouseEvent } from 'react';
import { createPortal } from 'react-dom';
import { Button, Popover, Spin } from 'antd';
import axios from 'axios';
import dayjs from 'dayjs';
import type { ProblemStatus, SubmissionVO } from '@/types/leaderboard';
import type { BoardParticipant } from './BoardParticipantIdentity';
import './BoardProblemHover.css';
import { BOARD_RESULT_COLORS } from '@/app/utils/boardResultColors';

type Participant = BoardParticipant & { problemStatus: Record<string, ProblemStatus> };
type Problem = { id: string; name: string; score?: number };
type Submission = SubmissionVO;
type HistoryResponse = { records: Submission[]; total: number };
type History = HistoryResponse & { page: number; atTime: number };
type RequestContext = { key: string; controller: AbortController; params: Record<string, string | number>; page: number; loading: boolean };
export type BoardProblemHoverHandlers = { onMouseEnter: (event: MouseEvent<HTMLElement>) => void; onMouseLeave: () => void; onFocus: (event: FocusEvent<HTMLElement>) => void; onBlur: () => void };
type Target<R, P> = { row: R; problem: P; rect: { left: number; top: number; width: number; height: number } };
const verdicts: Record<string, { chinese: string; english: string; color: string }> = {
  ACCEPTED: { chinese: '通过', english: 'Accepted', color: BOARD_RESULT_COLORS.ac },
  WRONG_ANSWER: { chinese: '答案错误', english: 'Wrong Answer', color: BOARD_RESULT_COLORS.wa },
  TIME_LIMIT_EXCEEDED: { chinese: '时间超限', english: 'Time Limit Exceeded', color: '#c47d05' },
  MEMORY_LIMIT_EXCEEDED: { chinese: '内存超限', english: 'Memory Limit Exceeded', color: '#c47d05' },
  OUTPUT_LIMIT_EXCEEDED: { chinese: '输出超限', english: 'Output Limit Exceeded', color: '#c47d05' },
  RUNTIME_ERROR: { chinese: '运行错误', english: 'Runtime Error', color: '#8c4ba1' },
  COMPILE_ERROR: { chinese: '编译错误', english: 'Compile Error', color: '#a17a16' },
  PRESENTATION_ERROR: { chinese: '格式错误', english: 'Presentation Error', color: '#c47d05' },
  SYSTEM_ERROR: { chinese: '系统错误', english: 'System Error', color: '#64748b' },
  PENDING: { chinese: '等待评测', english: 'Pending', color: '#64748b' },
  JUDGING: { chinese: '评测中', english: 'Judging', color: '#2863b6' },
};
const aliases: Record<string, string> = { AC: 'ACCEPTED', CORRECT: 'ACCEPTED', WA: 'WRONG_ANSWER', TLE: 'TIME_LIMIT_EXCEEDED', TIME_EXCEEDED: 'TIME_LIMIT_EXCEEDED', MLE: 'MEMORY_LIMIT_EXCEEDED', OLE: 'OUTPUT_LIMIT_EXCEEDED', RE: 'RUNTIME_ERROR', CE: 'COMPILE_ERROR', COMPILATION_ERROR: 'COMPILE_ERROR', PE: 'PRESENTATION_ERROR', SE: 'SYSTEM_ERROR', WAITING: 'PENDING', RUNNING: 'JUDGING' };
function verdict(value: string) {
  // Uppercase PENDING is the public API's frozen-result redaction.
  if (value === 'PENDING') return { chinese: '封榜待揭晓', english: 'Frozen', color: BOARD_RESULT_COLORS.frozen };
  const key = (value || '').trim().toUpperCase().replace(/[ -]+/g, '_');
  return verdicts[aliases[key] || key] || { chinese: value || '未知', english: '', color: '#64748b' };
}

/** One hover card and one active request for the entire board, including pinned copies. */
export function useBoardProblemHover<R extends Participant, P extends Problem>({ boardId, isTeam, timePoint, revision, scored = false, onViewSubmission }: {
  boardId: string; isTeam: boolean; timePoint?: number; revision: unknown; scored?: boolean;
  onViewSubmission?: (record: SubmissionVO) => void;
}) {
  const [target, setTarget] = useState<Target<R, P> | null>(null);
  const [history, setHistory] = useState<(History & { key: string; pending?: boolean; error?: boolean; morePending?: boolean; moreError?: boolean }) | null>(null);
  const openTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestRef = useRef<RequestContext | null>(null);
  const cache = useRef(new Map<string, { data: History; at: number }>());
  const close = useCallback(() => {
    if (openTimer.current) clearTimeout(openTimer.current);
    if (closeTimer.current) clearTimeout(closeTimer.current);
    openTimer.current = closeTimer.current = null;
    setTarget(null);
  }, []);
  const keepOpen = useCallback(() => { if (closeTimer.current) clearTimeout(closeTimer.current); closeTimer.current = null; }, []);
  const leave = useCallback(() => {
    if (openTimer.current) clearTimeout(openTimer.current);
    if (closeTimer.current) clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(close, 150);
  }, [close]);
  const enter = useCallback((row: R, problem: P, element: HTMLElement) => {
    if (openTimer.current) clearTimeout(openTimer.current);
    keepOpen();
    openTimer.current = setTimeout(() => {
      if (!element.isConnected) return;
      const { left, top, width, height } = element.getBoundingClientRect();
      setTarget({ row, problem, rect: { left, top, width, height } });
    }, 150);
  }, [keepOpen]);
  const remember = useCallback((key: string, data: History) => {
    cache.current.delete(key);
    if (cache.current.size >= 40) cache.current.delete(cache.current.keys().next().value!);
    cache.current.set(key, { data, at: Date.now() });
  }, []);
  useEffect(() => { cache.current.clear(); close(); }, [boardId, isTeam, timePoint, revision, close]);
  useEffect(() => {
    const scrolled = (event: Event) => {
      if (event.target instanceof Element && event.target.closest('.board-problem-popover')) return;
      close();
    };
    const keydown = (event: KeyboardEvent) => { if (event.key === 'Escape') close(); };
    window.addEventListener('scroll', scrolled, { capture: true, passive: true });
    window.addEventListener('resize', close);
    window.addEventListener('keydown', keydown);
    document.addEventListener('fullscreenchange', close);
    return () => {
      if (openTimer.current) clearTimeout(openTimer.current);
      if (closeTimer.current) clearTimeout(closeTimer.current);
      window.removeEventListener('scroll', scrolled, true);
      window.removeEventListener('resize', close);
      window.removeEventListener('keydown', keydown);
      document.removeEventListener('fullscreenchange', close);
    };
  }, [close]);
  const key = target ? `${boardId}:${isTeam}:${target.row.itemId}:${target.problem.id}:${timePoint ?? 'live'}` : '';
  useEffect(() => {
    if (!target || !key) return;
    const cached = cache.current.get(key);
    const fresh = cached && Date.now() - cached.at < 15000 ? cached.data : null;
    const controller = new AbortController();
    const atTime = fresh?.atTime ?? timePoint ?? Date.now();
    const params = { pageSize: 50, problemId: target.problem.id, ...(isTeam ? { signupTeamId: target.row.itemId } : { userId: target.row.itemId }), timePoint: atTime };
    const request: RequestContext = { key, controller, params, page: fresh?.page ?? 0, loading: !fresh };
    requestRef.current = request;
    if (fresh) setHistory({ ...fresh, key });
    else {
      setHistory({ key, records: [], total: 0, page: 0, atTime, pending: true });
      // The public endpoint redacts frozen results; all pages share one snapshot time.
      axios.get<{ code: number; data: HistoryResponse }>(`/api/leaderboard/submissions/${boardId}`, { params: { ...params, page: 1 }, signal: controller.signal }).then(({ data }) => {
        if (controller.signal.aborted) return;
        if (data.code !== 200 || !Array.isArray(data.data?.records)) throw new Error('Unable to load submissions');
        const value = { records: data.data.records, total: data.data.total, page: 1, atTime };
        request.page = 1;
        remember(key, value);
        setHistory({ ...value, key });
      }).catch(() => { if (!controller.signal.aborted) setHistory({ key, records: [], total: 0, page: 0, atTime, error: true }); }).finally(() => { request.loading = false; });
    }
    return () => { controller.abort(); if (requestRef.current === request) requestRef.current = null; };
  }, [key, boardId, isTeam, timePoint, remember]);
  const getTriggerProps = useCallback((row: R, problem: P): BoardProblemHoverHandlers => ({
    onMouseEnter: event => enter(row, problem, event.currentTarget), onMouseLeave: leave,
    onFocus: event => enter(row, problem, event.currentTarget), onBlur: leave,
  }), [enter, leave]);
  const visible = history?.key === key ? history : null;
  const fetchMore = () => {
    const request = requestRef.current;
    if (!request || request.key !== key || request.loading || !visible || visible.pending || visible.error || visible.records.length >= visible.total) return;
    request.loading = true;
    setHistory(previous => previous?.key === key ? { ...previous, morePending: true, moreError: false } : previous);
    const nextPage = request.page + 1;
    axios.get<{ code: number; data: HistoryResponse }>(`/api/leaderboard/submissions/${boardId}`, { params: { ...request.params, page: nextPage }, signal: request.controller.signal }).then(({ data }) => {
      if (request.controller.signal.aborted) return;
      if (data.code !== 200 || !Array.isArray(data.data?.records)) throw new Error('Unable to load submissions');
      request.page = nextPage;
      setHistory(previous => {
        if (!previous || previous.key !== key) return previous;
        const ids = new Set(previous.records.map(record => record.submissionId));
        const value = { records: [...previous.records, ...data.data.records.filter(record => !ids.has(record.submissionId))], total: data.data.total, page: nextPage, atTime: previous.atTime };
        remember(key, value);
        return { ...value, key };
      });
    }).catch(() => { if (!request.controller.signal.aborted) setHistory(previous => previous?.key === key ? { ...previous, morePending: false, moreError: true } : previous); }).finally(() => { request.loading = false; });
  };
  const status = target?.row.problemStatus[target.problem.id];
  const summary = scored && status?.status === 'Scored' ? { chinese: `${status.score ?? 0} / ${status.maxScore ?? target?.problem.score ?? 100} 分`, english: isTeam ? 'Team score' : 'Score' } : status?.status === 'AC' ? { chinese: status.isFirstAc ? '首答' : '已通过', english: status.isFirstAc ? 'First Solve' : 'Accepted' } : status?.status === 'WA' ? { chinese: '未通过', english: 'Wrong Answer' } : status?.status === 'PENDING' ? { chinese: '封榜待揭晓', english: 'Frozen' } : { chinese: '未提交', english: 'No submissions' };
  const popup = target && createPortal(<Popover open trigger={[]} placement="topLeft" arrow={false} classNames={{ root: 'board-problem-popover' }} content={
    <section className="board-problem-details" onMouseEnter={keepOpen} onMouseLeave={leave} aria-label={`${target.problem.id} 提交信息`}>
      <header><span><strong>题目 {target.problem.id}</strong><small>Problem {target.problem.id}</small></span><span><strong>{summary.chinese}</strong><small>{summary.english}</small></span><span><strong>{visible && !visible.pending && !visible.error ? visible.total : status?.tryCount ?? 0} 次提交</strong><small>submissions</small></span><span className="board-problem-participant-id">ID {target.row.itemId}</span></header>
      <p className="board-problem-title">{target.problem.name}</p>
      <dl><div><dt>学校/组织<small>School / Org</small></dt><dd>{target.row.organization || '—'}</dd></div><div><dt>{isTeam ? '队名' : '姓名'}<small>{isTeam ? 'Team' : 'Participant'}</small></dt><dd>{target.row.name}</dd></div></dl>
      {!visible || visible.pending ? <div className="board-problem-loading"><Spin size="small" /> 正在加载提交记录</div> : visible.error ? <p className="board-problem-empty">暂时无法加载，请移开后重新悬停。</p> : !visible.records.length ? <p className="board-problem-empty">暂无提交记录</p> : <div className="board-problem-submissions" onScroll={event => { const element = event.currentTarget; if (element.scrollTop + element.clientHeight >= element.scrollHeight - 24) fetchMore(); }}>{visible.records.map((record, index) => {
        const result = verdict(record.status);
        return <div className="board-problem-submission" key={record.submissionId}><span>#{visible.total - index}</span><time>{dayjs(record.submitTime).isValid() ? dayjs(record.submitTime).format('YYYY-MM-DD HH:mm:ss') : '—'}</time><span className="board-problem-verdict" style={{ color: result.color }}>{scored && record.status !== 'PENDING' && record.scoreDisplay ? <><strong>{record.scoreDisplay} 分</strong><br /></> : null}{result.chinese}<small>{result.english}</small></span><span className="board-problem-language" title={record.language}>{record.language || '—'}</span>{onViewSubmission && <Button size="small" type="link" aria-label={`查看提交记录 #${record.submissionId}`} title={`查看提交记录 #${record.submissionId}`} onClick={() => { onViewSubmission({ ...record, displayId: record.displayId || target.problem.id, problemName: record.problemName || target.problem.name, userName: record.userName || target.row.name, teamName: isTeam ? record.teamName || target.row.name : record.teamName }); close(); }}>详情</Button>}</div>;
      })}</div>}
      {visible && !visible.pending && !visible.error && visible.total > visible.records.length && <div className="board-problem-page-info">{visible.morePending ? <><Spin size="small" /> 正在加载更多记录</> : visible.moreError ? '加载失败，滚动后重试或重新悬停。' : `已显示 ${visible.records.length} / ${visible.total} 条，向下滚动继续查看`}</div>}
    </section>
  }><span aria-hidden="true" style={{ position: 'fixed', ...target.rect, pointerEvents: 'none' }} /></Popover>, document.body);
  return { getTriggerProps, popup };
}
