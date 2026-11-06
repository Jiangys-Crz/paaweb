'use client';

import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ConfigProvider, Input, Modal, message, theme as antdTheme } from 'antd';
import axios from 'axios';
import type { BoardVO, RankItem, ProblemStatus } from '@/types/leaderboard';
import { installResolverInput, type ResolverAction } from '@/app/utils/resolverInput';
import { getResolverLayout } from '@/app/utils/resolverLayout';
import { ResolverAnimator, RESOLVER_DELAY_MS, RESOLVER_ROW_MOVEMENT, RESOLVER_SCROLL_MOVEMENT } from '@/app/utils/resolverMotion';
import { ResolverAwardView, ResolverGroupAwardView, type ResolverAward } from './ResolverAwards';
import ResolverSplash from './ResolverSplash';
import { decorateResolverTeamName, formatResolverName, resolverProblemLabel } from '@/app/utils/resolverDisplay';
import './BoardResolver.css';
import { BOARD_RESULT_STYLE } from '@/app/utils/boardResultColors';

type ResolverSubmission = { submissionId: number; participantId: number; problemId: string; timeMillis: number; result: string; frozen: boolean };
type ResolverStep = {
  type: 'screen' | 'scrollTeamList' | 'selectTeam' | 'selectProblem' | 'selectSubmission' | 'judge' | 'award' | 'listAward' | 'pause' | 'scroll' | 'delay' | 'toJudge' | 'complete';
  participantId?: number | null; participantIds?: number[]; problemId?: string | null; submissionId?: number | null;
  medal?: string | null; citation?: string; citations?: string[]; photos?: boolean; row?: number; kind?: string; durationMs?: number; number?: number;
  screen?: string; top?: boolean; awards?: ResolverAward[]; selectionType?: string; selections?: Record<string, string>; awardType?: string;
};
type ResolverSettings = {
  awards?: { showMedals?: boolean; goldLabel?: string; silverLabel?: string; bronzeLabel?: string };
  display?: { rowsPerScreen?: number; theme?: 'dark' | 'light'; showOrganizationLogo?: boolean; showTeamPhoto?: boolean; showOrganization?: boolean; showMembers?: boolean; nameTemplate?: string };
};
type ResolverData = { coverUrl?: string; frozenBoard: BoardVO; finalBoard: BoardVO; submissions: ResolverSubmission[]; resolutionSteps: ResolverStep[]; resolverSpeed?: number; resolverSingleStep?: boolean; resolverSettings?: ResolverSettings; medalConfig?: { gold: number; silver: number; bronze: number } };
type PhotoRow = RankItem & { photo?: string; photoThumbnail?: string; organizationLogo?: string; hasSubmission?: boolean; hasTeamPhoto?: boolean; englishName?: string; coach?: string };
type Selection = { itemId: number; problemId: string };
type Frame = { screen: string; judged: Record<string, boolean>; selection: Selection | null; focusItemId: number | null; selectedIds: number[]; selectionType: string; awards: number[]; award: number | null; awardMedal: string | null; awardCitations: string[]; awardDetails: ResolverAward[]; groupAward: { participantIds: number[]; citation: string; photos: boolean; medal?: string | null; selections?: Record<string,string> } | null; scrollRow: number | null; listAtTop: boolean };
const emptyFrame = (): Frame => ({ screen: 'SPLASH', judged: {}, selection: null, focusItemId: null, selectedIds: [], selectionType: 'NORMAL', awards: [], award: null, awardMedal: null, awardCitations: [], awardDetails: [], groupAward: null, scrollRow: 0, listAtTop: true });
type DisplayRow = PhotoRow & { visible: Record<string, ProblemStatus>; resolved: number; cost: number; costMillis: number; initialIndex: number; displayRank: number };
const pairKey = (itemId: number, problemId: string) => `${itemId}:${problemId}`;
const accepted = (result: string) => ['AC', 'ACCEPTED', 'CORRECT'].includes((result || '').trim().toUpperCase());
const compileError = (result: string) => ['CE', 'COMPILE ERROR', 'COMPILATION ERROR'].includes((result || '').trim().toUpperCase());

/** Authorized results are kept in memory and never stored in the URL or browser storage. */
export default function BoardResolver({ boardId, unfrozen, onClose }: { boardId: string; unfrozen: boolean; onClose: () => void }) {
  const [data, setData] = useState<ResolverData | null>(null);
  const [password, setPassword] = useState('');
  const [messageApi, messageHolder] = message.useMessage();
  const [loading, setLoading] = useState(false);
  const [frame, setFrame] = useState<Frame>(emptyFrame);
  const [history, setHistory] = useState<number[]>([0]);
  const [cursor, setCursor] = useState(0);
  const [position, setPosition] = useState(0);
  const [, setBusy] = useState(false);
  const runId = useRef(0);
  const busyRef = useRef(false);
  const finishDelay = useRef<(() => void) | null>(null);
  const [stepSpeed, setStepSpeed] = useState(1);
  const stepSpeedRef = useRef(1);
  const [scrollFactor, setScrollFactor] = useState(1);
  const [scrollPaused, setScrollPaused] = useState(false);
  const scrollPausedRef = useRef(false);
  const [showInfo, setShowInfo] = useState(false);
  const [fastMotion, setFastMotion] = useState(false);
  const resolverRef = useRef<HTMLDivElement>(null);
  const nativeActionRef = useRef<(action: ResolverAction) => void>(() => {});
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(0);
  const [viewportWidth, setViewportWidth] = useState(1200);
  const [headerHeight, setHeaderHeight] = useState(0);
  const viewportRef = useRef<HTMLDivElement>(null);
  const headerRef = useRef<HTMLDivElement>(null);
  const rowElements = useRef(new Map<number, HTMLDivElement>());
  const rowAnimators = useRef(new Map<number, ResolverAnimator>());
  const movingSelections = useRef(new Map<number, string>());
  const previousSelection = useRef({ ids: [] as number[], type: 'NORMAL' });
  const scrollAnimator = useRef(new ResolverAnimator(0, RESOLVER_SCROLL_MOVEMENT));
  const display = useMemo(() => ({ ...data?.resolverSettings?.display, theme: 'dark' as const }), [data]);
  const geometry = getResolverLayout(viewportHeight, headerHeight, viewportWidth, data?.frozenBoard.problems.length || 0);
  const { rowHeight } = geometry;
  const fontMetrics = useMemo(() => {
    const context = typeof document === 'undefined' ? null : document.createElement('canvas').getContext('2d');
    if (context) context.font = `700 ${geometry.rowFontSize}px ResolverReference,ResolverFont,sans-serif`;
    const measure = (text: string) => context?.measureText(text).width ?? text.length * geometry.rowFontSize * .56;
    return { rank: Math.round(measure('199 ')) + 8, rankText: Math.round(measure('199')), solved: Math.round(measure('99')), time: Math.round(measure('9999')), space: Math.round(measure(' ')), measure };
  }, [geometry.rowFontSize]);

  const open = async () => {
    setLoading(true);
    try {
      const response = await axios.post<{ code: number; data: ResolverData }>(`/api/leaderboard/${boardId}/resolver`, { password });
      if (response.data.code !== 200 || !response.data.data) throw new Error('request failed');
      if (!Array.isArray(response.data.data.resolutionSteps) || !Array.isArray(response.data.data.submissions)) throw new Error('invalid resolver data');
      const records = new Map(response.data.data.submissions.map(item => [item.submissionId, item]));
      if (response.data.data.resolutionSteps.some(step => {
        if (step.type !== 'judge') return false;
        const record = step.submissionId == null ? undefined : records.get(step.submissionId);
        return !record || !record.frozen || record.participantId !== step.participantId || record.problemId !== step.problemId;
      })) throw new Error('invalid resolver data');
      // Wait for both reference and CJK webfonts before presenting the first frame.
      await Promise.allSettled([
        document.fonts.load('400 16px ResolverReference', 'Resolver'),
        document.fonts.load('400 16px ResolverFont', '汉字')
      ]);
      const nextData = response.data.data;
      const initialSpeed = Math.min(4, Math.max(.25, nextData.resolverSpeed || 1));
      stepSpeedRef.current = initialSpeed;
      setStepSpeed(initialSpeed);
      const firstPause = nextData.resolutionSteps.findIndex(step => step.type === 'pause') + 1;
      setFrame(nextData.resolutionSteps.slice(0, firstPause).reduce(applyStep, emptyFrame()));
      setPosition(firstPause); setHistory([firstPause]); setCursor(0);
      setData(nextData);
      setPassword('');
    } catch (error) {
      messageApi.error(error instanceof Error && error.message === 'invalid resolver data' ? 'Resolver data is incomplete. Update the server before starting.' : 'Unable to start resolver. Check the password and contest settings.');
    } finally { setLoading(false); }
  };

  const submissionsByCell = useMemo(() => {
    const cells = new Map<string, ResolverSubmission[]>();
    for (const submission of data?.submissions || []) {
      if (!submission.frozen) continue;
      const key = pairKey(submission.participantId, submission.problemId);
      const list = cells.get(key) || [];
      list.push(submission);
      cells.set(key, list);
    }
    for (const list of cells.values()) list.sort((a, b) => a.timeMillis - b.timeMillis || a.submissionId - b.submissionId);
    return cells;
  }, [data]);
  const finalRows = useMemo(() => new Map((data?.finalBoard.rows || []).map(row => [row.itemId, row as PhotoRow])), [data]);

  const rows = useMemo<DisplayRow[]>(() => {
    if (!data) return [];
    const next = data.frozenBoard.rows.map((base, initialIndex) => {
      const row = base as PhotoRow;
      const visible = { ...row.problemStatus };
      for (const problem of data.frozenBoard.problems) {
        const key = pairKey(row.itemId, problem.id);
        if (visible[problem.id]?.status !== 'PENDING') continue;
        const events = submissionsByCell.get(key) || [];
        const judged = events.filter(event => frame.judged[event.submissionId]);
        if (!judged.length) continue;
        const solved = judged.some(event => accepted(event.result));
        const remaining = events.length - judged.length;
        if (solved || remaining <= 0 || !events.length) {
          const final = finalRows.get(row.itemId)?.problemStatus?.[problem.id];
          if (final) visible[problem.id] = final; else delete visible[problem.id];
        } else {
          const initial = row.problemStatus[problem.id];
          const errors = judged.filter(event => !accepted(event.result) && !compileError(event.result)).length;
          visible[problem.id] = { ...initial, status: 'PENDING', tryCount: remaining, score: (initial.score || 0) + errors };
        }
      }
      const solved = Object.values(visible).filter(status => status.status === 'AC');
      return { ...row, visible, initialIndex, resolved: solved.length,
        cost: solved.reduce((sum, status) => sum + (status.time || 0) + Math.max(0, (status.tryCount || 1) - 1) * 20, 0),
        costMillis: solved.reduce((sum, status) => sum + (status.timeMillis ?? (status.time || 0) * 60000) + Math.max(0, (status.tryCount || 1) - 1) * 1200000, 0) };
    });
    const finalOrder = new Map(data.finalBoard.rows.map((row, index) => [row.itemId, index]));
    const settled = next.every(row => Object.values(row.visible).every(status => status.status !== 'PENDING'));
    next.sort((a, b) => Number(!!b.hasSubmission) - Number(!!a.hasSubmission) || b.resolved - a.resolved || a.costMillis - b.costMillis || (settled ? (finalOrder.get(a.itemId) ?? a.initialIndex) - (finalOrder.get(b.itemId) ?? b.initialIndex) : a.initialIndex - b.initialIndex));
    let officialCount = 0;
    let previous: (typeof next)[number] | undefined;
    let rank = 0;
    return next.map(row => {
      const unofficial = row.isStar || row.rank === -1;
      if (!unofficial) {
        officialCount++;
        if (!previous || previous.resolved !== row.resolved || previous.costMillis !== row.costMillis) rank = officialCount;
        previous = row;
      }
      const medals = data.medalConfig;
      const gold = Math.max(0, Math.trunc(medals?.gold || 0));
      const silver = gold + Math.max(0, Math.trunc(medals?.silver || 0));
      const bronze = silver + Math.max(0, Math.trunc(medals?.bronze || 0));
      const medal: RankItem['medal'] = unofficial || data.resolverSettings?.awards?.showMedals === false ? undefined
        : medals ? officialCount <= gold ? 'Gold' : officialCount <= silver ? 'Silver' : officialCount <= bronze ? 'Bronze' : undefined
        : row.medal;
      return { ...row, isStar: unofficial, medal, displayRank: rank };
    });
  }, [data, finalRows, frame.judged, submissionsByCell]);

  // Replay the server's ICPC ResolverLogic steps; no client-side selection heuristic is used.
  const applyStep = useCallback((current: Frame, step: ResolverStep): Frame => {
    switch (step.type) {
      case 'screen': return { ...current, screen: step.screen || current.screen };
      case 'scrollTeamList': return { ...current, listAtTop: step.top !== false };
      case 'selectTeam':
        return { ...current, focusItemId: step.participantId ?? null, selectedIds: step.participantIds || (step.participantId == null ? [] : [step.participantId]), selectionType: step.selectionType || 'NORMAL' };
      case 'selectProblem':
      case 'selectSubmission':
        return { ...current, focusItemId: step.participantId ?? current.focusItemId,
          selection: step.participantId != null && step.problemId != null ? { itemId: step.participantId, problemId: step.problemId } : null,
        };
      case 'judge': {
        if (step.participantId == null || step.problemId == null || step.submissionId == null) return current;
        return { ...current, judged: { ...current.judged, [step.submissionId]: true } };
      }
      case 'award':
        return step.participantId == null ? current : { ...current, award: step.participantId, awardMedal: step.medal || null, awardCitations: step.citations?.length ? step.citations : step.citation ? [step.citation] : [], awardDetails: step.awards || [],
          awards: current.awards.includes(step.participantId) ? current.awards : [...current.awards, step.participantId] };
      case 'listAward':
        return { ...current, groupAward: { participantIds: step.participantIds || [], citation: step.citation || '', photos: !!step.photos, medal: step.awardType === 'Medal' ? step.medal : null, selections: step.selections } };
      case 'scroll':
        return { ...current, scrollRow: step.row ?? null };
      default: return current;
    }
  }, []);

  // Step speed shortens waits only; visual animations always use their own clocks.
  const waitForDelay = useCallback((duration: number) => new Promise<void>(resolve => {
    let remaining = duration;
    let previous = performance.now();
    let animationFrame = 0;
    const finish = () => {
      cancelAnimationFrame(animationFrame);
      if (finishDelay.current === finish) finishDelay.current = null;
      resolve();
    };
    const tick = (now: number) => {
      remaining -= Math.max(0, now - previous) * stepSpeedRef.current;
      previous = now;
      if (remaining <= 0) finish();
      else animationFrame = requestAnimationFrame(tick);
    };
    animationFrame = requestAnimationFrame(tick);
    finishDelay.current = finish;
  }), []);

  // One keypress runs all actions up to the next official pause, including its delays.
  const advance = useCallback(async (fast = false) => {
    if (!data || busyRef.current || position >= data.resolutionSteps.length) return;
    setScrollPaused(false);
    scrollPausedRef.current = false;
    const token = ++runId.current;
    busyRef.current = true;
    setBusy(true);
    let next = position;
    try {
      while (next < data.resolutionSteps.length) {
        const step = data.resolutionSteps[next++];
        if (step.type === 'pause') break;
        setFrame(current => applyStep(current, step));
        const delay = step.type === 'delay' ? (step.durationMs ?? RESOLVER_DELAY_MS[step.kind as keyof typeof RESOLVER_DELAY_MS] ?? 0) : 0;
        if (delay && !fast) await waitForDelay(delay);
        if (runId.current !== token) return;
      }
      setPosition(next);
      setHistory(current => [...current.slice(0, cursor + 1), next]);
      setCursor(cursor + 1);
    } finally {
      if (runId.current === token) {
        busyRef.current = false; setBusy(false);
      }
    }
  }, [applyStep, cursor, data, position, waitForDelay]);
  const back = useCallback(async (fast = false) => {
    if (!data || !cursor || busyRef.current) return;
    setScrollPaused(false);
    scrollPausedRef.current = false;
    const target = history[cursor - 1];
    const token = ++runId.current;
    busyRef.current = true; setBusy(true);
    try {
      for (let index = position - 2; index >= target; index--) {
        const step = data.resolutionSteps[index];
        if (step.type === 'delay') {
          const delay = step.durationMs ?? RESOLVER_DELAY_MS[step.kind as keyof typeof RESOLVER_DELAY_MS] ?? 0;
          if (!fast && delay) await waitForDelay(delay);
        } else if (step.type === 'judge' && step.submissionId != null) {
          setFrame(current => { const judged = { ...current.judged }; delete judged[step.submissionId!]; return { ...current, judged }; });
        } else {
          const previous = data.resolutionSteps.slice(0, index).findLast(item => item.type === step.type);
          if (previous) setFrame(current => applyStep(current, previous));
        }
        if (runId.current !== token) return;
      }
      setPosition(target); setCursor(cursor - 1);
    } finally {
      if (runId.current === token) {
        busyRef.current = false; setBusy(false);
      }
    }
  }, [applyStep, cursor, data, history, position, waitForDelay]);
  useEffect(() => () => { runId.current++; finishDelay.current?.(); }, []);
  const changeStepSpeed = useCallback((value: number) => {
    const next = Math.min(4, Math.max(.25, Math.round(value * 1000) / 1000));
    stepSpeedRef.current = next;
    setStepSpeed(next);
  }, []);
  const handleNativeAction = useCallback((action: ResolverAction) => {
    switch (action) {
      case 'forward': void advance(); break;
      case 'fastForward': void advance(true); break;
      case 'reverse': void back(); break;
      case 'fastReverse': void back(true); break;
      case 'quit': onClose(); break;
      case 'reset':
        if (busyRef.current) break;
        const firstPause = data ? data.resolutionSteps.findIndex(step => step.type === 'pause') + 1 : 0;
        setFastMotion(true); setFrame(data ? data.resolutionSteps.slice(0, firstPause).reduce(applyStep, emptyFrame()) : emptyFrame()); setPosition(firstPause); setHistory([firstPause]); setCursor(0);
        setScrollPaused(false); scrollPausedRef.current = false;
        viewportRef.current?.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        requestAnimationFrame(() => requestAnimationFrame(() => setFastMotion(false)));
        break;
      case 'faster': changeStepSpeed(stepSpeedRef.current * 1.25); break;
      case 'slower': changeStepSpeed(stepSpeedRef.current / 1.25); break;
      case 'scrollFaster': setScrollFactor(value => value * 1.2); break;
      case 'scrollSlower': setScrollFactor(value => value * .8); break;
      case 'resetSpeed': changeStepSpeed(data?.resolverSpeed || 1); setScrollFactor(1); break;
      case 'pauseScroll':
        scrollPausedRef.current = !scrollPausedRef.current;
        setScrollPaused(scrollPausedRef.current);
        break;
      case 'info': setShowInfo(value => !value); break;
      // The native S key switches loaded contests. One external board loads one contest.
      case 'screen': break;
    }
  }, [advance, applyStep, back, changeStepSpeed, data, onClose]);
  useEffect(() => { nativeActionRef.current = handleNativeAction; }, [handleNativeAction]);
  useEffect(() => {
    if (!data || !resolverRef.current) return;
    return installResolverInput(action => nativeActionRef.current(action), resolverRef.current, () => busyRef.current, () => stepSpeedRef.current);
  }, [data]);
  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;
    const measure = () => {
      setViewportHeight(el.clientHeight);
      setViewportWidth(el.clientWidth);
      setHeaderHeight(headerRef.current?.offsetHeight || 0);
    };
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    if (headerRef.current) observer.observe(headerRef.current);
    measure();
    return () => observer.disconnect();
  }, [data]);
  const motionOptions = useRef({ fastMotion, scrollFactor, selectedIds: frame.selectedIds, rowCount: rows.length });
  motionOptions.current = { fastMotion, scrollFactor, selectedIds: frame.selectedIds, rowCount: rows.length };
  useLayoutEffect(() => {
    for (const [index, row] of rows.entries()) {
      const animator = rowAnimators.current.get(row.itemId) || new ResolverAnimator(index, RESOLVER_ROW_MOVEMENT);
      animator.setTarget(index);
      if (fastMotion) animator.resetToTarget();
      rowAnimators.current.set(row.itemId, animator);
      const selectedType = frame.selectedIds.includes(row.itemId) ? frame.selectionType
        : previousSelection.current.ids.includes(row.itemId) ? previousSelection.current.type : undefined;
      if (animator.isMoving() && selectedType && !fastMotion && frame.screen === 'SCOREBOARD') movingSelections.current.set(row.itemId, selectedType);
      if (!animator.isMoving() || fastMotion || frame.screen !== 'SCOREBOARD') movingSelections.current.delete(row.itemId);
      const element = rowElements.current.get(row.itemId);
      const retained = movingSelections.current.get(row.itemId);
      if (element) {
        if (retained) element.dataset.motionSelection = retained;
        else delete element.dataset.motionSelection;
        element.style.zIndex = String(retained ? rows.length + 3 : frame.selectedIds.includes(row.itemId) ? rows.length + 2 : rows.length - index);
      }
    }
    previousSelection.current = { ids: frame.selectedIds, type: frame.selectionType };
  }, [rows, fastMotion, frame.selectedIds, frame.selectionType, frame.screen]);
  useEffect(() => {
    if (frame.scrollRow === null) return;
    scrollAnimator.current.setTarget(Math.max(0, frame.scrollRow - 12 + 3));
    if (fastMotion) scrollAnimator.current.resetToTarget();
  }, [frame.scrollRow, fastMotion]);
  useEffect(() => {
    // Native aboutToShow initializes team positions and snaps the scroll animator.
    if (frame.screen === 'SCOREBOARD') {
      for (const animator of rowAnimators.current.values()) animator.resetToTarget();
      scrollAnimator.current.resetToTarget();
    }
  }, [frame.screen]);
  useEffect(() => {
    if (!data) return;
    let id = 0;
    let previous: number | undefined;
    const tick = (now: number) => {
      const wholeTime = Math.trunc(now);
      const delta = previous === undefined ? 0 : wholeTime - previous;
      previous = wholeTime;
      const options = motionOptions.current;
      for (const [itemId, animator] of rowAnimators.current) {
        if (options.fastMotion) animator.resetToTarget(); else animator.incrementTimeMs(delta);
        if (!animator.isMoving()) movingSelections.current.delete(itemId);
        const element = rowElements.current.get(itemId);
        if (element) {
          element.style.transform = `translateY(${Math.trunc(animator.getValue() * rowHeight)}px)`;
          const retained = movingSelections.current.get(itemId);
          if (retained) element.dataset.motionSelection = retained;
          else delete element.dataset.motionSelection;
          element.style.zIndex = String(retained ? options.rowCount + 3 : options.selectedIds.includes(itemId) ? options.rowCount + 2 : options.rowCount - animator.getTarget());
        }
      }
      const scroll = scrollAnimator.current;
      if (options.fastMotion) scroll.resetToTarget();
      else if (!scrollPausedRef.current) scroll.incrementTimeMs(Math.trunc(delta * options.scrollFactor));
      if (viewportRef.current) viewportRef.current.scrollTop = Math.trunc(scroll.getValue() * rowHeight);
      id = requestAnimationFrame(tick);
    };
    id = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(id);
  }, [data, rowHeight]);
  // Warm browser caches with a bounded number of requests, so large contests do not flood the server.
  useEffect(() => {
    if (!data) return;
    const urls = [...new Set((data.frozenBoard.rows as PhotoRow[]).flatMap(row => [display?.showTeamPhoto === false ? undefined : row.photoThumbnail, display?.showOrganizationLogo === false ? undefined : row.organizationLogo]).filter((url): url is string => !!url))];
    let stopped = false;
    let next = 0;
    const load = () => {
      if (stopped || next >= urls.length) return;
      const image = new Image();
      image.onload = load;
      image.onerror = load;
      image.src = urls[next++];
    };
    for (let i = 0; i < 4; i++) load();
    return () => { stopped = true; };
  }, [data, display?.showTeamPhoto, display?.showOrganizationLogo]);
  useEffect(() => {
    if (!data || display?.showTeamPhoto === false) return;
    const upcoming = data.resolutionSteps.slice(position).filter(step => step.type === 'award' || step.type === 'listAward').slice(0, 3);
    const ids = [...new Set(upcoming.flatMap(step => step.participantId != null ? [step.participantId] : step.participantIds || []))].slice(0, 20);
    let stopped = false, next = 0;
    const load = () => {
      if (stopped || next >= ids.length) return;
      const row = finalRows.get(ids[next++]);
      if (!row?.photo) { load(); return; }
      const image = new Image(); image.onload = load; image.onerror = load; image.src = row.photo;
    };
    for (let i = 0; i < 3; i++) load();
    return () => { stopped = true; };
  }, [data, display?.showTeamPhoto, finalRows, position]);

  if (!data) return <ConfigProvider theme={{ algorithm: antdTheme.darkAlgorithm, token: { colorBgElevated: '#121e2f', colorBgContainer: '#0b1220', colorPrimary: '#4d8ee8', colorText: '#f1f5f9' } }}>{messageHolder}<Modal open centered title="Start Resolver" onCancel={onClose} onOk={() => void open()} okText="Start" confirmLoading={loading}>
    {unfrozen ? <p>The leaderboard is already unfrozen.</p> : <><p>Enter the resolver password.</p><Input.Password value={password} onChange={event => setPassword(event.target.value)} onPressEnter={() => void open()} autoFocus /></>}
    <p>Space / F / PageDown: next · Hold Space: continuous · ↑ / ↓: step speed · R / B / PageUp: previous · Shift+Q: exit</p>
  </Modal></ConfigProvider>;

  const awardRow = frame.award === null ? null : rows.find(row => row.itemId === frame.award);
  const award = awardRow ? (frame.awardMedal || finalRows.get(awardRow.itemId)?.medal) : null;
  const rankWidth = fontMetrics.rank;
  const logoWidth = display?.showOrganizationLogo === false ? 0 : geometry.logoWidth;
  const timeWidth = fontMetrics.time + fontMetrics.space + 8;
  const teamWidth = viewportWidth - rankWidth - logoWidth - fontMetrics.solved - timeWidth;
  const problemCount = data.frozenBoard.problems.length;
  const grid = `${rankWidth}px ${logoWidth}px minmax(0,1fr) ${fontMetrics.solved}px ${timeWidth}px`;
  const first = Math.max(0, Math.floor(scrollTop / rowHeight) - 5);
  const last = Math.ceil((scrollTop + viewportHeight) / rowHeight) + 5;
  const awardDisplayRow = awardRow ? { ...awardRow, displayRank: finalRows.get(awardRow.itemId)?.rank ?? awardRow.displayRank, name: decorateResolverTeamName(awardRow), coach: awardRow.coach || awardRow.advisor, firstSolveIds: data.frozenBoard.problems.filter(problem => finalRows.get(awardRow.itemId)?.problemStatus?.[problem.id]?.isFirstAc).map(problem => problem.id) } : null;
  return createPortal(<div className="icpc-resolver theme-dark" style={BOARD_RESULT_STYLE} role="dialog" aria-modal="true" aria-label="Contest resolver" ref={resolverRef} tabIndex={-1} >
    <div className="icpc-resolver-scroll" ref={viewportRef} onScroll={event => setScrollTop(event.currentTarget.scrollTop)}>
      <div className="icpc-resolver-table" style={{ '--resolver-row-height': `${rowHeight}px`, '--resolver-row-font-size': `${geometry.rowFontSize}px`, '--resolver-status-size': `${geometry.statusFontSize}px`, '--resolver-problem-size': `${geometry.problemFontSize}px`, '--resolver-cube-height': `${geometry.cubeHeight}px`, '--resolver-cube-top': `${geometry.cubeTop}px` } as React.CSSProperties}>
        <div ref={headerRef} className="icpc-resolver-head" style={{ gridTemplateColumns: grid, height: Math.floor(viewportHeight / 50), fontSize: Math.floor(viewportHeight * .021) }}><span style={{ marginLeft: 8, width: fontMetrics.rankText }}>Rank</span><span /><span>Organization / Team</span><span>Solved</span><span style={{ paddingLeft: fontMetrics.space, paddingRight: 8 }}>Time</span></div>
        <div className="icpc-resolver-body" style={{ height: (rows.length + 3) * rowHeight }}>
          {viewportHeight > 0 && rows.map((row, index) => {
            const animatedIndex = rowAnimators.current.get(row.itemId)?.getValue() ?? index;
            const selected = frame.selectedIds.includes(row.itemId);
            if (!selected && !movingSelections.current.has(row.itemId) && (index < first || index > last) && (animatedIndex < first || animatedIndex > last)) return null;
            const displayedName = formatResolverName(row, display?.nameTemplate);
            const markers = `${row.isStar ? '⭐' : ''}${row.isGirl ? '👧' : ''}`;
            const markerWidth = Array.from(markers).length * geometry.rowFontSize * .78;
            const organization = row.organization || '—';
            const institutionWidth = Math.max(20, Math.min(teamWidth * .47 - markerWidth, fontMetrics.measure(organization) * .95));
            const identityWidth = Math.max(1, teamWidth - institutionWidth - markerWidth - 18);
            const members = row.memberInfos?.map(member => member.name).filter(Boolean).join('、') || row.members?.replace(/[,，]\s*/g, '、') || '';
            return <div className={`icpc-resolver-row${selected ? ` selected selected-${frame.selectionType}` : ''}`} key={row.itemId} aria-label={displayedName} ref={element => { if (element) rowElements.current.set(row.itemId, element); else rowElements.current.delete(row.itemId); }} style={{ transform: `translateY(${Math.trunc(animatedIndex * rowHeight)}px)`, gridTemplateColumns: grid, zIndex: rows.length - index }}>
              <strong className={`icpc-resolver-rank resolver-rank-badge${!row.isStar && row.medal ? ` rank-${row.medal.toLowerCase()}` : ''}`} style={{ marginLeft: 8, width: fontMetrics.rankText }}>{row.isStar ? '*' : row.displayRank}</strong>
              <div className="icpc-resolver-logo">{display?.showOrganizationLogo !== false && row.organizationLogo && <img src={row.organizationLogo} alt="" />}</div>
              <div className="icpc-resolver-team">
                <div className="resolver-team-heading">
                  {markers && <span className="resolver-team-markers" style={{ width: markerWidth }} aria-label={`${row.isStar ? '打星队伍 ' : ''}${row.isGirl ? '女生队伍' : ''}`}>{markers}</span>}
                  <div className="resolver-institution" style={{ width: institutionWidth }}><strong style={{ transform: `scaleX(${Math.min(1, institutionWidth / Math.max(1, fontMetrics.measure(organization) * .95))})` }}>{organization}</strong></div>
                  <div className="resolver-team-identity">
                    <strong className="resolver-team-name" style={{ transform: `scaleX(${Math.min(1, identityWidth / Math.max(1, fontMetrics.measure(row.name) * .75))})` }}>{row.name}</strong>
                    <span className="resolver-team-members" style={{ transform: `scaleX(${Math.min(1, identityWidth / Math.max(1, fontMetrics.measure(members) * .45))})` }}>{members}</span>
                  </div>
                </div>
                <div className="icpc-resolver-problems" style={{ gridTemplateColumns: `repeat(${problemCount}, ${geometry.cubeWidth}px)` }}>
                {data.frozenBoard.problems.map(problem => {
                  const status = row.visible[problem.id];
                  const selectedSubmission = frame.selection?.itemId === row.itemId && frame.selection.problemId === problem.id;
                  const label = resolverProblemLabel(problem.id, status);
                  return <span key={problem.id} className={`icpc-resolver-cell ${status?.status || 'NONE'}${status?.isFirstAc ? ' first-ac' : ''}${selectedSubmission ? ' selected-submission' : ''}`} aria-label={`${problem.id}: ${label.label}${status?.isFirstAc ? ", First Solve" : ""}`}>
                    <span className="icpc-resolver-cell-fill">{label.attempted ? <><span className="resolver-attempts">{label.attempts}</span><span className="resolver-minutes">{label.minutes}</span></> : problem.id}</span>
                  </span>;

                })}
              </div></div>
              <strong className="icpc-resolver-score">{row.resolved || ''}</strong><strong className="icpc-resolver-score" style={{ paddingLeft: fontMetrics.space, paddingRight: 8 }}>{row.cost || ''}</strong>
            </div>;
          })}
        </div>
      </div>
    </div>
    {frame.screen === 'TEAM_AWARD' && awardDisplayRow && <ResolverAwardView key={awardDisplayRow.itemId} row={awardDisplayRow} awards={frame.awardDetails.length ? frame.awardDetails : undefined} citations={frame.awardCitations} medal={award} display={display} showInfo={showInfo} />}
    {['TEAM_LIST', 'TEAM_LIST_PHOTO'].includes(frame.screen) && frame.groupAward && <ResolverGroupAwardView key={`${frame.groupAward.citation}-${frame.groupAward.photos}`} rows={frame.groupAward.participantIds.map(id => rows.find(row => row.itemId === id)).filter((row): row is DisplayRow => !!row).map(row => ({ ...row, name: formatResolverName(row, display?.nameTemplate), rank: finalRows.get(row.itemId)?.rank ?? row.rank, displayRank: finalRows.get(row.itemId)?.rank ?? row.displayRank, medal: finalRows.get(row.itemId)?.medal }))} citation={frame.groupAward.citation} photos={frame.screen === 'TEAM_LIST_PHOTO'} medal={frame.groupAward.medal} selections={frame.groupAward.selections} display={display} scrollToTop={frame.listAtTop} scrollPaused={scrollPaused} speed={scrollFactor} />}
    {frame.screen === 'SPLASH' && <ResolverSplash cover={data.coverUrl} title={data.frozenBoard.title} subtitle={data.frozenBoard.sessionName} pending={data.submissions.filter(sub => sub.frozen && !frame.judged[sub.submissionId]).length} />}
    {showInfo && frame.screen === 'SCOREBOARD' && frame.selection && <div className="icpc-resolver-info">
      <strong>{rows.find(row => row.itemId === frame.selection?.itemId)?.name} · {frame.selection.problemId}</strong>
      <div>{(submissionsByCell.get(pairKey(frame.selection.itemId, frame.selection.problemId)) || []).map(submission => <span key={submission.submissionId}>{Math.floor(submission.timeMillis / 60000)}: {frame.judged[submission.submissionId] ? submission.result : 'Pending'}　</span>)}</div>
      <small>Step speed {stepSpeed.toFixed(2)}× · ↑ faster / ↓ slower{scrollPaused ? ' · Scrolling paused' : ''}</small>
    </div>}

  </div>, document.body);
}
