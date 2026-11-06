"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  ConfigProvider,
  Descriptions,
  Dropdown,
  Empty,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Slider,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message
} from 'antd';
import enUS from 'antd/locale/en_US';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  BankOutlined,
  ExportOutlined,
  DownOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  NumberOutlined,
  PlayCircleOutlined,
  SearchOutlined,
  SolutionOutlined,
  SyncOutlined,
  TeamOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import duration from 'dayjs/plugin/duration';
import ReactECharts from 'echarts-for-react';
import * as XLSX from 'xlsx';
import { useParams, useRouter } from 'next/navigation';
import { exportBoardPdf } from '@/app/utils/exportBoardPdf';
import { useBoardModalScrollLock } from '@/app/utils/useBoardModalScrollLock';
import type { SubmissionVO } from '@/types/leaderboard';
import SubmissionCodeBlock from '@/app/components/SubmissionCodeBlock';
import BoardResolver from '@/app/components/BoardResolver';
import { BOARD_RESULT_COLORS } from '@/app/utils/boardResultColors';
import { BOARD_SCORE_LEVELS as GPLT_SCORE_LEVELS } from '@/app/utils/boardScoreColors';
import { formatBoardTitle } from '@/app/utils/boardTitles';
import ExternalBoardStandings from '@/app/components/ExternalBoardStandings';
import BoardParticipantCard from '@/app/components/BoardParticipantCard';
import { useBoardProblemHover } from '@/app/components/BoardProblemHover';
import { BoardParticipantMarkers } from '@/app/components/BoardParticipantIdentity';
import { getBoardProgressStatus, hasBoardFreezeWindow, resolveBoardUnfreezeState } from '@/app/utils/boardProgress';
import { buildBoardDisplayRows, compareBoardRank, matchesBoardParticipantSearch, matchesBoardParticipantType, type BoardDisplayRowMeta, type BoardParticipantFilter } from '@/app/utils/boardParticipants';
import { desktopModalStyle, useBoardDesktopLayout, useViewportSize } from '@/app/utils/useBoardDesktopLayout';

dayjs.extend(duration);

const { Option } = Select;
const { Title } = Typography;

const BOARD_COLORS = {
  ...BOARD_RESULT_COLORS,
  gold: '#e4b011', silver: '#b6c1d1', bronze: '#b9783c'
};

interface BoardProblem {
  id: string;
  name: string;
  score: number;
  accepted: number;
  submitted: number;
  attempted: number;
  firstSolved: number;
  lastSolved: number;
  avgScore?: number;
  maxScore?: number;
  minScore?: number;
}

interface ProblemStatus {
  status: 'AC' | 'WA' | 'PENDING' | 'NONE' | 'Scored';
  score?: number;
  maxScore?: number;
  time?: number;
  tryCount?: number;
  isFirstAc?: boolean;
}

interface MemberInfo {
  name: string;
  numberId: string;
}

interface RankItem {
  rank: number;
  itemId: number;
  name: string;
  members?: string;
  memberInfos?: MemberInfo[];
  avatar?: string;
  organization?: string;
  organizationLogo?: string;
  advisor?: string;
  coach?: string;
  englishName?: string;
  isStar?: boolean;
  isGirl?: boolean;
  medal?: 'Gold' | 'Silver' | 'Bronze';
  group?: string;
  solved?: number;
  penalty?: number;
  totalScore?: number;
  problemStatus: Record<string, ProblemStatus>;
}

interface BoardVO {
  boardId: number;
  eventId: number;
  title: string;
  sessionName?: string;
  type: 'XCPC' | 'GPLT' | 'LANQIAO';
  status: 'PENDING' | 'RUNNING' | 'FROZEN' | 'ENDED';
  isTeam?: number;
  unfrozen?: boolean;
  isUnfrozen?: boolean;
  resolverEnabled?: boolean;
  startTime: string;
  endTime: string;
  freezeTime?: string;
  problems: BoardProblem[];
  rows: RankItem[];
}

type BoardDisplayRow = RankItem & BoardDisplayRowMeta;

interface ParticipantSearchOption {
  value: number;
  label: string;
  participant: RankItem;
}

interface BoardStats {
  totalSubmissions: number;
  problemCount: number;
  participantCount: number;
  organizationCount: number;
  submissionChart: { problemId: string; acCount: number; waCount: number }[];
  problemPassChart: { problemId: string; passCount: number }[];
  teamPassChart: { solvedCount: number; teamCount: number }[];
}

function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);
  useEffect(() => {
    const handler = window.setTimeout(() => setDebouncedValue(value), delay);
    return () => window.clearTimeout(handler);
  }, [value, delay]);
  return debouncedValue;
}

const CustomTableStyles = () => (
  <style>{`
    .board-page-fit-viewport.external-board-centered { width:80%; margin-inline:auto; }
    @media (max-width:767px), (pointer:coarse) { .board-page-fit-viewport.external-board-centered { width:100%; } }
    .rank-container, .rank-fit-inner, .rank-fit-slot { transition:none !important; animation:none !important; }
    .rank-container { display: flow-root; min-width: 0; max-width: 100%; color: #111827; font-family: 'Times New Roman', 'Microsoft YaHei', sans-serif !important; }
    .rank-table, .rank-table .ant-table { font-family: Menlo, Monaco, 'Microsoft YaHei', monospace; font-size: 12px; color: #111827; }
    .rank-table .ant-table-tbody > tr { height: 60px; background-color: #ffffff; }
    .rank-table .ant-table-tbody > tr:nth-child(even) { background-color: #eff5fa; }
    .rank-table .ant-table-tbody > tr:hover { background-color: #dbeafe; }
    .rank-table .ant-table-tbody > tr.rank-row-pinned { background-color: transparent; }
    .rank-table .ant-table-tbody > tr.rank-row-pinned:last-of-type > td,
    .rank-table .ant-table-tbody > tr.rank-row-pinned:has(+ tr:not(.rank-row-pinned)) > td { border-bottom: 1px solid #d1d5db !important; }
    .rank-table .ant-table-tbody > tr > td { font-weight: 400; }
    .rank-table .rank-number { display: inline-flex; align-items: center; min-width: 0; height: auto; border-radius: 0; background: transparent; color: inherit; font-family: 'Times New Roman', 'Microsoft YaHei', serif; font-size: 16px; font-weight: 700; }
    .rank-table .rank-solved, .rank-table .rank-score { color: #1f2937; font-size: 16px; font-weight: 700; }
    .rank-table .rank-penalty { color: #4b5563; font-size: 14px; font-weight: 600; }
    .rank-table .rank-institution { color: #111827; font-size: 12px; }
    .rank-container .main-symbol { font-size: 18px; font-weight: 700; line-height: 1; margin-bottom: 2px; }
    .rank-container .frozen-symbol { font-size: 16px; font-weight: 700; line-height: 1; margin-bottom: 2px; }
    .rank-container .sub-info { font-size: 11px; font-weight: 400; line-height: 1.25; opacity: 0.8; white-space: nowrap; }
    .rank-page-progress { position: relative; margin-top: 8px; --progress-color: ${BOARD_RESULT_COLORS.ac}; }
    .rank-page-progress[data-status="FROZEN"] { --progress-color: ${BOARD_RESULT_COLORS.frozen}; }
    .rank-page-progress[data-status="ENDED"] { --progress-color: #3b82f6; }
    .rank-page-progress[data-freeze="false"] { --progress-color: ${BOARD_RESULT_COLORS.ac}; }
    .rank-page-progress .contest-slider { height: 20px; margin: 0; padding: 0; }
    .rank-page-progress .contest-slider .ant-slider-rail { height: 20px; background: #f3f4f6; border-radius: 999px; border: 1px solid #e5e7eb; }
    .rank-page-progress .contest-slider .ant-slider-track { height: 20px; background: var(--progress-color); border-radius: 999px; }
    .rank-page-progress[data-status="PENDING"] .ant-slider-rail { background: #ffffff; }
    .rank-page-progress[data-status="PENDING"] .ant-slider-track, .rank-page-progress[data-status="PENDING"] .ant-slider-handle { display: none; }
    .rank-page-progress .contest-slider .ant-slider-handle { inset-block-start: 3px; width: 14px; height: 14px; margin-top: 0; opacity: 1; z-index: 3; }
    .rank-page-progress .contest-slider .ant-slider-handle::before { inset: -12px; width: 38px; height: 38px; }
    .rank-page-progress .contest-slider .ant-slider-handle::after { inset: 0; width: 14px; height: 14px; box-sizing: border-box; border: 1px solid #ffffff59; background: color-mix(in srgb, var(--progress-color) 88%, #000000); box-shadow: none; outline: none; transition: box-shadow .15s ease; }
    .rank-page-progress .contest-slider .ant-slider-handle:hover::after,
    .rank-page-progress .contest-slider .ant-slider-handle:focus-visible::after,
    .rank-page-progress .contest-slider .ant-slider-handle:active::after { inset: 0; width: 14px; height: 14px; box-shadow: 0 0 0 2px color-mix(in srgb, var(--progress-color) 20%, transparent); outline: none; }
    .rank-page-progress-caption { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #6b7280; font-family: Arial, 'Microsoft YaHei', sans-serif; font-size: 13px; font-weight: 700; }
    .freeze-mark { position: absolute; top: 0; height: 20px; width: 2px; background-color: #f59e0b; z-index: 2; }
    .rank-table.ant-table-bordered > .ant-table-container { border: none !important; }
    .rank-table.ant-table-bordered > .ant-table-container > .ant-table-header > table,
    .rank-table.ant-table-bordered > .ant-table-container > .ant-table-body > table { border: none !important; }
    .rank-table .ant-table-thead > tr > th {
      background-color: #ffffff !important;
      color: #374151 !important;
      text-align: center !important;
      height: 60px;
      font-family: 'Times New Roman', 'Microsoft YaHei', serif;
      font-size: 16px;
      font-weight: 700;
      padding: 4px 1px !important;
      border: none !important;
      border-bottom: 2px solid #9ca3af !important;
    }
    /* Ordinary cells share the row background; verdict and medal cells override it below. */
    .rank-table .ant-table-tbody > tr > td {
      background-color: transparent !important;
      padding: 4px 1px !important;
      border: none !important;
    }
    .rank-table .ant-table-tbody > tr > td.rank-medal-gold { background-color: ${BOARD_COLORS.gold} !important; color: #422006; }
    .rank-table .ant-table-tbody > tr > td.rank-medal-silver { background-color: ${BOARD_COLORS.silver} !important; color: #020617; }
    .rank-table .ant-table-tbody > tr > td.rank-medal-bronze { background-color: ${BOARD_COLORS.bronze} !important; color: #ffffff; }
    .rank-table .ant-table-tbody > tr > td[class*="rank-medal-"] { box-shadow: inset 0 -2px 0 #ffffff; }
    .rank-fit-viewport {
      max-width: 100%;
      overflow: clip;
    }
    .rank-fit-inner {
      display: flow-root;
    }
    .rank-fit-slot {
      overflow: visible;
    }
    .rank-table .ant-table,
    .rank-table .ant-table-container,
    .rank-table .ant-table-content {
      overflow: visible !important;
    }
    .rank-table table {
      width: max-content !important;
      min-width: 100%;
      table-layout: auto !important;
    }
    .rank-table .ant-table-cell,
    .rank-table .ant-table-cell * {
      white-space: nowrap;
      overflow-wrap: normal;
      word-break: normal;
    }
    .rank-entry {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .rank-entry-text {
      min-width: 0;
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 2px;
      line-height: 1.25;
    }
    .rank-entry-title {
      color: #1e3a8a;
      display: flex;
      align-items: center;
      gap: 4px;
      flex-wrap: nowrap;
      flex-shrink: 0;
      font-weight: 700;
      white-space: nowrap;
    }
    .rank-entry-members {
      color: #111827;
      font-size: 12px !important;
      font-weight: 400 !important;
      line-height: 1.25;
      margin-top: 0;
      flex-shrink: 0;
      white-space: nowrap;
    }
    .rank-table .ant-table-tbody > tr > td.cell-problem-empty { background-color: transparent !important; }
    .rank-table .ant-table-tbody > tr > td.cell-problem-ac { background-color: ${BOARD_COLORS.ac} !important; }
    .rank-table .ant-table-tbody > tr > td.cell-problem-first-ac { background-color: ${BOARD_COLORS.firstAc} !important; }
    .rank-table .ant-table-tbody > tr > td.cell-problem-wa { background-color: ${BOARD_COLORS.wa} !important; }
    .rank-table .ant-table-tbody > tr > td.cell-problem-frozen { background-color: ${BOARD_COLORS.frozen} !important; color:#ffffff !important; }
    .rank-table .ant-table-tbody > tr > td.cell-gplt-level-1 { background-color: #ffccc7 !important; }
    .rank-table .ant-table-tbody > tr > td.cell-gplt-level-2 { background-color: #bae7ff !important; }
    .rank-table .ant-table-tbody > tr > td.cell-gplt-level-3 { background-color: #d9f7be !important; }
    .rank-table .ant-table-tbody > tr > td.cell-gplt-level-4 { background-color: #52c41a !important; color: #fff !important; }
    .rank-table .ant-table-tbody > tr > td:is(.cell-problem-ac, .cell-problem-first-ac, .cell-problem-wa, .cell-gplt-level-4) { color: #ffffff !important; }
    .rank-table .problem-cell-content { display: flex; flex-direction: column; gap: 4px; align-items: center; justify-content: center; min-height: 56px; color: inherit; font-weight: 700; }
    .rank-table .ant-table-cell.problem-col { padding: 2px !important; border: 1px solid #ffffff !important; }
    .rank-table .ant-table-summary { background-color: transparent !important; }
    .rank-table .ant-table-summary .ant-table-cell {
      text-align: center !important;
      padding: 6px 2px !important;
      border-radius: 0;
      border-right: 2px solid #ffffff !important;
      border-bottom: 2px solid #ffffff !important;
    }
    .rank-table .ant-table-summary .ant-table-cell:first-child { border-left: 2px solid #ffffff !important; }
    .rank-table .ant-table-summary .ant-table-cell:last-child { border-right: 2px solid #ffffff !important; }
    .summary-bg-dark { background-color: #dbeafe !important; }
    .summary-bg-light { background-color: #eff5fa !important; }
    .summary-empty-cell { background-color: transparent !important; border: none !important; }
    .rank-table .problem-history-cell { cursor: help; }
    .rank-table .problem-history-cell:focus-visible { outline: 2px solid #1d4ed8; outline-offset: -2px; }
    .rank-page-header { margin: 12px 24px; padding: 12px 20px; background: #ffffff; border: 1px solid #f3f4f6; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
    .rank-page-heading { display: block; }
    .rank-page-title { min-width: 0; text-align: center; }
    .rank-page-title h3 { color: #111827; font-family: 'Times New Roman', 'Microsoft YaHei', serif; font-size: 26px; font-weight: 700; line-height: 1.2; white-space: nowrap; margin: 0 0 6px !important; }
    .rank-page-meta { display: flex; flex-wrap: nowrap; justify-content: center; gap: 8px 16px; color: #6b7280; font-family: Arial, 'Microsoft YaHei', sans-serif; font-size: 14px; }
    .rank-page-meta-id { color: #374151; font-family: Menlo, Monaco, monospace; }
    .rank-page-meta-type { color: #1d4ed8; font-weight: 700; }
    .rank-page-meta-status { color: #dc2626; font-weight: 700; }
    .rank-page-timeline { margin-top: 12px; }
    .rank-page-timeline-bottom { display: flex; flex-wrap: nowrap; align-items: center; justify-content: space-between; gap: 10px 20px; margin-top: 8px; }
    .rank-page-time-progress { margin-right: auto; display: flex; align-items: center; gap: 12px; font-family: Menlo, Monaco, monospace; font-size: 16px; white-space: nowrap; color: #15803d; }
    .rank-page-time-progress[data-replay="true"] { color: #b45309; }
    .rank-page-time-progress-total { color: #9ca3af; }
    .rank-page-legend { display: flex; flex-wrap: nowrap; justify-content: flex-end; align-items: center; gap: 12px 24px; font-size: 12px; }
    .rank-page-times { display: flex; justify-content: space-between; flex-wrap: nowrap; gap: 4px 16px; font-size: 16px; font-weight: 700; color: #555; margin-bottom: 4px; }
    .rank-page-content { margin: 24px; padding: 24px; min-width: 0; background: #ffffff; border: 1px solid #f3f4f6; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
    .rank-page-back { margin-bottom: 2px; }
    .rank-page-filters { padding-bottom: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: nowrap; border-bottom: 1px solid #f0f0f0; }
    .rank-page-filters > * { max-width: 100%; }
    .rank-participant-search { flex: 0 0 320px; min-width: 0; }
    .rank-participant-option { display: flex; flex-direction: column; gap: 2px; line-height: 1.4; }
    .rank-participant-option-meta { color: #6b7280; font-size: 12px; }
    .rank-container .main-content-box {
      width: 100%;
      margin: 0;
      background: transparent;
    }
  `}</style>
);

const formatDuration = (seconds: number) => {
  if (seconds < 0) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

const renderParticipantName = (name: string, record: RankItem) => (
  <>
    <span>{name}</span>
    <BoardParticipantMarkers row={record} />
  </>
);

const formatSubmissionParticipant = (record: SubmissionVO, isPersonal: boolean) => {
  return (isPersonal ? record.userName : record.teamName) || '-';
};

const SUBMISSION_VERDICTS: Record<string, { text: string; color: string; icon: React.ReactNode }> = {
  ACCEPTED: { text: 'Accepted', color: BOARD_RESULT_COLORS.ac, icon: <CheckCircleOutlined /> },
  WRONG_ANSWER: { text: 'Wrong Answer', color: BOARD_RESULT_COLORS.wa, icon: <CloseCircleOutlined /> },
  TIME_LIMIT_EXCEEDED: { text: 'Time Limit Exceeded', color: '#ea580c', icon: <ClockCircleOutlined /> },
  MEMORY_LIMIT_EXCEEDED: { text: 'Memory Limit Exceeded', color: '#ea580c', icon: <ExclamationCircleOutlined /> },
  OUTPUT_LIMIT_EXCEEDED: { text: 'Output Limit Exceeded', color: '#ea580c', icon: <ExclamationCircleOutlined /> },
  RUNTIME_ERROR: { text: 'Runtime Error', color: '#9333ea', icon: <ExclamationCircleOutlined /> },
  COMPILE_ERROR: { text: 'Compile Error', color: '#ca8a04', icon: <ExclamationCircleOutlined /> },
  PRESENTATION_ERROR: { text: 'Presentation Error', color: '#f97316', icon: <ExclamationCircleOutlined /> },
  SYSTEM_ERROR: { text: 'System Error', color: '#111827', icon: <ExclamationCircleOutlined /> },
  CANCELLED: { text: 'Cancelled', color: '#6b7280', icon: <CloseCircleOutlined /> },
  IGNORED: { text: 'Ignored', color: '#6b7280', icon: <ExclamationCircleOutlined /> },
  HACKED: { text: 'Hacked', color: '#dc2626', icon: <CloseCircleOutlined /> },
  HACK_SUCCESSFUL: { text: 'Hack Successful', color: '#16a34a', icon: <CheckCircleOutlined /> },
  HACK_UNSUCCESSFUL: { text: 'Hack Unsuccessful', color: '#dc2626', icon: <CloseCircleOutlined /> },
  FETCHED: { text: 'Fetched', color: '#2563eb', icon: <SyncOutlined spin /> },
  FROZEN: { text: 'Frozen', color: BOARD_RESULT_COLORS.frozen, icon: <span aria-hidden="true">❄</span> },
  PENDING: { text: 'Pending', color: '#6b7280', icon: <SyncOutlined spin /> },
  JUDGING: { text: 'Judging', color: '#2563eb', icon: <SyncOutlined spin /> },
  COMPILING: { text: 'Compiling', color: '#2563eb', icon: <SyncOutlined spin /> }
};

const renderStatusTag = (status: string) => {
  // The public board API uses uppercase PENDING for hidden frozen results.
  const normalized = status === 'PENDING' ? 'FROZEN' : status.trim().toUpperCase().replace(/[ -]+/g, '_');
  const aliases: Record<string, string> = {
    AC: 'ACCEPTED', CORRECT: 'ACCEPTED', WA: 'WRONG_ANSWER',
    TLE: 'TIME_LIMIT_EXCEEDED', TIME_EXCEEDED: 'TIME_LIMIT_EXCEEDED', TIME_LIMIT_EXCEED: 'TIME_LIMIT_EXCEEDED',
    MLE: 'MEMORY_LIMIT_EXCEEDED', MEMORY_EXCEEDED: 'MEMORY_LIMIT_EXCEEDED',
    OLE: 'OUTPUT_LIMIT_EXCEEDED', OUTPUT_EXCEEDED: 'OUTPUT_LIMIT_EXCEEDED',
    RE: 'RUNTIME_ERROR', CE: 'COMPILE_ERROR', COMPILATION_ERROR: 'COMPILE_ERROR',
    PE: 'PRESENTATION_ERROR', FE: 'PRESENTATION_ERROR', FORMAT_ERROR: 'PRESENTATION_ERROR',
    SE: 'SYSTEM_ERROR', UNKNOWN_ERROR: 'SYSTEM_ERROR',
    CANCELED: 'CANCELLED', IGN: 'IGNORED', HK: 'HACKED',
    WAITING: 'PENDING', RUNNING: 'JUDGING'
  };
  const verdict = SUBMISSION_VERDICTS[aliases[normalized] || normalized];
  return <span className="board-submission-verdict" style={{ color: verdict?.color || '#9ca3af' }}>{verdict?.icon}<span>{verdict?.text || status}</span></span>;
};

export default function BoardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  useLayoutEffect(() => {
    document.documentElement.classList.add('external-board-page');
    document.body.classList.add('external-board-page');
    return () => {
      document.documentElement.classList.remove('external-board-page');
      document.body.classList.remove('external-board-page');
    };
  }, []);

  const [loading, setLoading] = useState(false);
  const [board, setBoard] = useState<BoardVO | null>(null);
  const [submissions, setSubmissions] = useState<SubmissionVO[]>([]);
  const [subTotal, setSubTotal] = useState(0);
  const [subPage, setSubPage] = useState(1);
  const [stats, setStats] = useState<BoardStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [now, setNow] = useState(dayjs());
  const [displayTime, setDisplayTime] = useState(0);
  const [isLiveMode, setIsLiveMode] = useState(true);
  const [activeTab, setActiveTab] = useState('rank');
  const [resolverOpen, setResolverOpen] = useState(false);
  const closingResolverRef = useRef(false);
  const closeResolver = useCallback(async () => {
    if (closingResolverRef.current) return;
    closingResolverRef.current = true;
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      // Keep the resolver covering the board while responsive layouts settle.
      await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
      setResolverOpen(false);
    } catch {
      setResolverOpen(false);
    } finally {
      closingResolverRef.current = false;
    }
  }, []);
  const [filterType, setFilterType] = useState<BoardParticipantFilter>('all');
  const [searchText, setSearchText] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [selectedParticipantIds, setSelectedParticipantIds] = useState<number[]>([]);
  const [gpltGroup, setGpltGroup] = useState<string | undefined>(undefined);
  const [gpltScope, setGpltScope] = useState<'team' | 'personal'>('team');
  const isPersonalBoard = board?.type === 'LANQIAO'
    || (board?.type === 'GPLT' ? gpltScope === 'personal' : board?.isTeam !== 1);
  const participantLabel = isPersonalBoard ? 'Person' : 'Team';

  const [subProblemId, setSubProblemId] = useState<string | undefined>(undefined);
  const [subStatus, setSubStatus] = useState<string | undefined>(undefined);
  const [subKeyword, setSubKeyword] = useState('');
  const submissionsRequest = useRef<AbortController | null>(null);
  const submissionsViewRef = useRef<HTMLDivElement>(null);
  useEffect(() => () => submissionsRequest.current?.abort(), []);
  const [codeModalOpen, setCodeModalOpen] = useState(false);
  const [codeContent, setCodeContent] = useState('');
  const [codeLanguage, setCodeLanguage] = useState('');
  const [codeLoading, setCodeLoading] = useState(false);
  const [codeError, setCodeError] = useState('');
  const submissionCodeRequest = useRef<AbortController | null>(null);
  const [currentSubmission, setCurrentSubmission] = useState<SubmissionVO | null>(null);
  useEffect(() => {
    setCodeModalOpen(false);
    return () => submissionCodeRequest.current?.abort();
  }, [id]);
  const [exportLoading, setExportLoading] = useState(false);
  const [pdfExporting, setPdfExporting] = useState(false);
  const { viewportRef: boardExportRef, contentRef: boardContentRef, fit: boardFit } = useBoardDesktopLayout(`${board ? formatBoardTitle(board) : ''}:${activeTab}:${gpltScope}`);
  const viewportSize = useViewportSize();

  useBoardModalScrollLock(codeModalOpen);

  const debouncedKeyword = useDebounce(subKeyword, 500);
  const submissionKeyword = subKeyword.trim() ? debouncedKeyword : '';
  const lastSliderUpdateRef = useRef(0);


  const loadData = useCallback(async (silent = false, timePoint?: number) => {
    if (!id) return;
    if (!silent) setLoading(true);
    try {
      const params: Record<string, string | number> = { _t: Date.now() };
      if (timePoint) params.timePoint = timePoint;
      if (gpltScope) params.scope = gpltScope;
      if (gpltGroup && gpltGroup !== 'all') params.group = gpltGroup;
      const res = await axios.get<{ code: number; data: BoardVO }>(`/api/leaderboard/${id}`, { params });
      if (res.data.code === 200) {
        const nextBoard = res.data.data;
        const unfrozen = hasBoardFreezeWindow(nextBoard) ? await resolveBoardUnfreezeState(nextBoard, async () => {
          const event = await axios.get<{ code: number; data: { unfrozen?: boolean; isUnfrozen?: boolean } }>(
            `/api/event/detail/${id}`, { params: { _t: Date.now() } }
          );
          if (event.data.code !== 200) throw new Error('Failed to load contest unfreeze status');
          return event.data.data;
        }) : nextBoard.unfrozen;
        setBoard({ ...nextBoard, unfrozen });
      }
    } catch (error) {
      console.error(error);
      message.error('Failed to load leaderboard');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [gpltGroup, gpltScope, id]);

  const loadStats = useCallback(async (timePoint?: number) => {
    if (!id) return;
    setStatsLoading(true);
    try {
      const res = await axios.get<{ code: number; data: BoardStats }>(`/api/leaderboard/${id}/stats`, { params: timePoint ? { timePoint } : {} });
      if (res.data.code === 200) setStats(res.data.data);
    } catch {
      message.error('Failed to load statistics');
    } finally {
      setStatsLoading(false);
    }
  }, [id]);

  const filteredRows = useMemo(() => (
    (board?.rows || []).filter(row => matchesBoardParticipantType(row, filterType))
  ), [board, filterType]);

  const participantOptions = useMemo<ParticipantSearchOption[]>(() => [...filteredRows].sort(compareBoardRank).map(row => ({
    value: row.itemId,
    label: row.name,
    participant: row
  })), [filteredRows]);

  const processedRows = useMemo(() => (
    buildBoardDisplayRows(filteredRows, selectedParticipantIds)
  ), [filteredRows, selectedParticipantIds]);

  useEffect(() => {
    setSearchText('');
    setSearchOpen(false);
    setSelectedParticipantIds([]);
  }, [id, gpltGroup, gpltScope]);

  const totalDuration = useMemo(() => {
    if (!board) return 1;
    return Math.max(1, dayjs(board.endTime).diff(dayjs(board.startTime), 'second'));
  }, [board]);

  const pending = !!board && now.isBefore(dayjs(board.startTime));
  const wasPending = useRef(false);
  useEffect(() => {
    if (wasPending.current && !pending && board) void loadData(true);
    wasPending.current = pending;
  }, [pending, board, loadData]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(dayjs()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!board || !isLiveMode) return;
    const start = dayjs(board.startTime);
    const end = dayjs(board.endTime);
    let elapsed = now.diff(start, 'second');
    if (now.isAfter(end)) elapsed = end.diff(start, 'second');
    if (elapsed < 0) elapsed = 0;
    setDisplayTime(elapsed);
  }, [now, board, isLiveMode]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    const dataTimer = window.setInterval(() => {
      if (isLiveMode && activeTab === 'rank' && !pdfExporting) {
        if ((board?.type === 'GPLT' || board?.type === 'LANQIAO') && !gpltGroup) return;
        loadData(true);
      }
    }, 30000);
    return () => window.clearInterval(dataTimer);
  }, [activeTab, board?.type, gpltGroup, isLiveMode, loadData, pdfExporting]);

  useEffect(() => {
    const refresh = () => {
      if (!isLiveMode || pdfExporting) return;
      if ((board?.type === 'GPLT' || board?.type === 'LANQIAO') && !gpltGroup) return;
      void loadData(true);
    };
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, [board?.type, gpltGroup, isLiveMode, loadData, pdfExporting]);

  const historyTimePoint = !isLiveMode && board
    ? dayjs(board.startTime).add(displayTime, 'second').valueOf()
    : undefined;

  const loadSubmissions = useCallback(async (page = 1) => {
    if (!id) return;
    submissionsRequest.current?.abort();
    const controller = new AbortController();
    submissionsRequest.current = controller;
    setSubPage(page);
    const params: Record<string, string | number | undefined> = { page, pageSize: 20 };
    if (subProblemId) params.problemId = subProblemId;
    if (subStatus) params.status = subStatus;
    const submissionTime = historyTimePoint;
    if (submissionTime !== undefined) params.timePoint = submissionTime;
    if (submissionKeyword) {
      const matched = board?.problems.find(p => p.name.toLowerCase().includes(submissionKeyword.toLowerCase()) || p.id.toLowerCase() === submissionKeyword.toLowerCase());
      if (matched) params.problemId = matched.id;
      else params.keyword = submissionKeyword;
    }
    try {
      const res = await axios.get<{ code: number; data: { records: SubmissionVO[]; total: number } }>(`/api/leaderboard/submissions/${id}`, { params, signal: controller.signal });
      if (!controller.signal.aborted && res.data.code === 200) {
        setSubmissions(res.data.data.records);
        setSubTotal(res.data.data.total || 0);
      }
    } catch {
      if (!controller.signal.aborted) message.error('Failed to load submissions');
    }
  }, [board, submissionKeyword, id, subProblemId, subStatus, historyTimePoint]);

  useEffect(() => {
    if (activeTab === 'submissions') loadSubmissions(1);
    if (activeTab === 'statistics') loadStats(historyTimePoint);
  }, [activeTab, loadStats, loadSubmissions, historyTimePoint]);



  const handleViewCode = useCallback(async (record: SubmissionVO) => {
    submissionCodeRequest.current?.abort();
    const controller = new AbortController();
    submissionCodeRequest.current = controller;
    setCurrentSubmission(record);
    setCodeContent('');
    setCodeLanguage(record.language || '');
    setCodeError('');
    setCodeLoading(true);
    setCodeModalOpen(true);
    try {
      const res = await axios.get<{ code: number; msg?: string; data: { content: string; language: string } }>(`/api/leaderboard/submission/${record.submissionId}/code`, { signal: controller.signal });
      if (controller.signal.aborted) return;
      if (res.data.code === 200) {
        setCodeContent(res.data.data.content || '');
        setCodeLanguage(res.data.data.language || record.language || '');
      } else setCodeError(res.data.msg || '暂时无法查看该提交的代码');
    } catch (error: unknown) {
      if (!controller.signal.aborted) setCodeError(axios.isAxiosError(error) ? (error.response?.data as { msg?: string })?.msg || '暂时无法查看该提交的代码' : '暂时无法查看该提交的代码');
    } finally {
      if (!controller.signal.aborted) setCodeLoading(false);
    }
  }, []);

  const { getTriggerProps: getProblemHoverProps, popup: problemPopup } = useBoardProblemHover<RankItem, BoardProblem>({ boardId: id, isTeam: !isPersonalBoard, timePoint: historyTimePoint, scored: board?.type === 'GPLT' || board?.type === 'LANQIAO', revision: processedRows, onViewSubmission: handleViewCode });

  const onSliderChange = (val: number) => {
    setIsLiveMode(false);
    const current = Date.now();
    if (current - lastSliderUpdateRef.current > 40) {
      setDisplayTime(val);
      lastSliderUpdateRef.current = current;
    }
  };

  const onSliderAfterChange = (val: number) => {
    setDisplayTime(val);
    if (!board) return;
    const targetTimeMs = dayjs(board.startTime).add(val, 'second').valueOf();
    loadData(false, targetTimeMs);
    if (activeTab === 'statistics') loadStats(targetTimeMs);
    message.info(`Viewing leaderboard at ${formatDuration(val)}`);
  };

  const handleResetLive = () => {
    setIsLiveMode(true);
    loadData(false);
    if (activeTab === 'statistics') loadStats();
    message.success('Back to Live');
  };

  const handleExportPdf = async () => {
    if (!board || !boardExportRef.current || pdfExporting || exportLoading) return;
    if ((board.type === 'GPLT' || board.type === 'LANQIAO') && !gpltGroup) {
      message.warning('Select a group to view and export the leaderboard');
      return;
    }
    setPdfExporting(true);
    const hide = message.loading('Generating leaderboard PDF...', 0);
    try {
      await exportBoardPdf(boardExportRef.current, formatBoardTitle(board));
      message.success('PDF exported successfully');
    } catch (error) {
      console.error(error);
      message.error(error instanceof Error ? error.message : 'PDF export failed. Please try again.');
    } finally {
      hide();
      setPdfExporting(false);
    }
  };

  const handleExport = async () => {
    if (!board) return;
    setExportLoading(true);
    const hide = message.loading('Exporting leaderboard...', 0);
    try {
      const wb = XLSX.utils.book_new();

      const getAwardName = (medal: string | null | undefined) => {
        if (medal === 'Gold') return 'Gold';
        if (medal === 'Silver') return 'Silver';
        if (medal === 'Bronze') return 'Bronze';
        return '';
      };
      const formatRank = (rank: number) => rank === -1 ? '*' : rank;
      const fetchBoard = async (scope?: string, group?: string) => {
        const params: Record<string, string> = {};
        if (scope) params.scope = scope;
        if (group) params.group = group;
        const res = await axios.get<{ code: number; data: BoardVO }>(`/api/leaderboard/${id}`, { params });
        return res.data.code === 200 ? (res.data.data.rows || []) : [];
      };

      if (board.type === 'XCPC') {
        const rows = await fetchBoard();
        const excelData = rows.map((row, index) => {
          const item: Record<string, string | number> = {
            'No.': index + 1,
            'Rank': formatRank(row.rank),
            'Institution': row.organization || '',
            'Team': row.name,
            'Members': row.members ? row.members.replace(/, /g, '、') : '',
            'Solved': row.solved || 0,
            'Penalty': row.penalty || 0,
            'Medal': getAwardName(row.medal)
          };
          row.memberInfos?.forEach((member, memberIndex) => {
            item[`Member ${memberIndex + 1} Name`] = member.name;
            item[`Member ${memberIndex + 1} Student ID`] = member.numberId;
          });
          return item;
        });
        XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(excelData), 'Rank');
      } else if (board.type === 'GPLT') {
        const groups = ['星芒初绽', '长风破浪', '凌霄问鼎'];
        for (const group of groups) {
          const teamRows = await fetchBoard('team', group);
          const teamData = teamRows.map((row, index) => {
            const item: Record<string, string | number> = {
              'No.': index + 1,
              'Rank': formatRank(row.rank),
              'Institution': row.organization || '',
              'Team': row.name,
              'Members': row.members ? row.members.replace(/, /g, '、') : '',
              'Score': row.totalScore || 0,
              'Medal': getAwardName(row.medal)
            };
            row.memberInfos?.forEach((member, memberIndex) => {
              item[`Member ${memberIndex + 1} Name`] = member.name;
              item[`Member ${memberIndex + 1} Student ID`] = member.numberId;
            });
            return item;
          });
          XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(teamData), `${group}-Team`);

          const personalRows = await fetchBoard('personal', group);
          const personalData = personalRows.map((row, index) => {
            const item: Record<string, string | number> = {
              'No.': index + 1,
              'Rank': formatRank(row.rank),
              'Institution': row.organization || '',
              'Name': row.name,
              'Total Score': row.totalScore || 0,
              'Medal': getAwardName(row.medal)
            };
            if (row.memberInfos?.length) {
              item['Name'] = row.memberInfos[0].name;
              item['Student ID'] = row.memberInfos[0].numberId;
            }
            return item;
          });
          XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(personalData), `${group}-Person`);
        }
      } else if (board.type === 'LANQIAO') {
        const groups = ['C/C++程序设计', 'Python程序设计', 'Java软件开发'];
        const safeSheetName = (name: string) => name.replace('/', '_');
        for (const group of groups) {
          const rows = await fetchBoard('personal', group);
          const data = rows.map((row, index) => {
            const item: Record<string, string | number> = {
              'No.': index + 1,
              'Rank': formatRank(row.rank),
              'Institution': row.organization || '',
              'Name': row.name,
              'Total Score': row.totalScore || 0,
              'Medal': getAwardName(row.medal)
            };
            if (row.memberInfos?.length) {
              item['Name'] = row.memberInfos[0].name;
              item['Student ID'] = row.memberInfos[0].numberId;
            }
            return item;
          });
          XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(data), safeSheetName(group));
        }
      }

      XLSX.writeFile(wb, `Contest_Rank_${formatBoardTitle(board) || 'Export'}.xlsx`);
      message.success('Exported successfully');
    } catch (error) {
      console.error(error);
      message.error('Export failed');
    } finally {
      hide();
      setExportLoading(false);
    }
  };

  const progressStatus = board ? getBoardProgressStatus(
    board,
    isLiveMode ? now.valueOf() : dayjs(board.startTime).add(displayTime, 'second').valueOf()
  ) : undefined;

  const freezePoint = useMemo(() => {
    if (!board || !hasBoardFreezeWindow(board)) return null;
    const start = dayjs(board.startTime).valueOf();
    const end = dayjs(board.endTime).valueOf();
    const freeze = dayjs(board.freezeTime).valueOf();
    const total = end - start;
    return total > 0 ? ((freeze - start) / total) * 100 : null;
  }, [board]);

  const submissionColumns = useMemo((): ColumnsType<SubmissionVO> => {
    const cols: ColumnsType<SubmissionVO> = [
      { title: 'ID', dataIndex: 'submissionId', width: 80 },
      { title: 'Problem', width: 200, render: (_, record) => <span className="board-submission-problem">{record.displayId} - {record.problemName}</span> },
      { title: 'Status', dataIndex: 'status', width: 120, render: renderStatusTag }
    ];
    if (board?.type === 'GPLT' || board?.type === 'LANQIAO') {
      cols.push({
        title: 'Score',
        width: 120,
        align: 'center',
        render: (_, record) => {
          if (record.scoreDisplay) return <span style={{ fontWeight: 'bold' }}>{record.scoreDisplay}</span>;
          const problem = board.problems.find(p => p.id === record.displayId);
          const maxScore = problem ? problem.score : 0;
          return <span style={{ fontWeight: 'bold' }}>{Math.ceil(((record.score || 0) / 100) * maxScore)} / {maxScore}</span>;
        }
      });
    }
    cols.push(
      { title: participantLabel, render: (_, record) => <span className="board-submission-person">{formatSubmissionParticipant(record, isPersonalBoard)}</span> },
      { title: 'Time', dataIndex: 'time', render: value => value || '-' },
      { title: 'Memory', dataIndex: 'memory', render: value => value ?? '-' },
      { title: 'Date', dataIndex: 'submitTime', width: 160, render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
      { title: 'Details', width: 100, render: (_, record) => <Button type="link" size="small" onClick={() => handleViewCode(record)}>View Details</Button> }
    );
    return cols.map(column => ({ ...column, align: 'center' }));
  }, [board, handleViewCode, isPersonalBoard, participantLabel]);

  const submissionChart = stats ? {
    title: { text: 'Submission Status by Problem', left: 'center' },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 45 },
    grid: { top: 85, left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: stats.submissionChart.map(item => item.problemId) },
    yAxis: { type: 'value' },
    series: [
      { name: 'Passed', type: 'bar', stack: 'total', data: stats.submissionChart.map(item => item.acCount), itemStyle: { color: '#52c41a' } },
      { name: 'Failed', type: 'bar', stack: 'total', data: stats.submissionChart.map(item => item.waCount), itemStyle: { color: '#ff4d4f' } }
    ]
  } : null;
  const problemPassChart = stats ? {
    title: { text: 'Accepted Count by Problem (Sorted)', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: stats.problemPassChart.map(item => item.problemId) },
    yAxis: { type: 'value' },
    series: [{ data: stats.problemPassChart.map(item => item.passCount), type: 'bar', itemStyle: { color: '#1890ff' }, label: { show: true, position: 'top' } }]
  } : null;
  const teamPassChart = stats ? {
    title: { text: 'Team Distribution by Solved Count', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', name: 'Solved', data: stats.teamPassChart.map(item => item.solvedCount) },
    yAxis: { type: 'value', name: 'Teams' },
    series: [{ data: stats.teamPassChart.map(item => item.teamCount), type: 'bar', itemStyle: { color: '#722ed1' }, label: { show: true, position: 'top' } }]
  } : null;

  return (
    <ConfigProvider locale={enUS}>
    <div className="board-page-fit-viewport external-board-centered" ref={boardExportRef} style={{ height: boardFit.height }}>
    <div className="rank-container" ref={boardContentRef} style={{ width: boardFit.width, maxWidth: 'none', transform: `scale(${boardFit.scale})`, transformOrigin: 'top left' }}>
      <CustomTableStyles />
      <div className="main-content-box">
        <div className="rank-page-header">
          <div className="rank-page-back">
            <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => router.push('/contest/board')}>Back</Button>
          </div>
          <div className="rank-page-heading">
            <div className="rank-page-title">
              <Title level={3}>{board ? formatBoardTitle(board) : 'Loading Contest...'}</Title>
              {board && (
                <div className="rank-page-meta">
                  <span>ID: <span className="rank-page-meta-id">{board.boardId}</span></span>
                  <span>Type: <span className="rank-page-meta-type">{board.type}</span></span>
                  <span>Status: <span className="rank-page-meta-status">{board.status}</span></span>
                  {!isLiveMode && <Tag color="orange" icon={<PlayCircleOutlined />}>REPLAY MODE</Tag>}
                </div>
              )}
            </div>
          </div>
          <div className="rank-page-timeline">
              <div className="rank-page-times">
                <span>{board ? dayjs(board.startTime).format('YYYY-MM-DD HH:mm') : '--:--'}</span>
                <span>{board ? dayjs(board.endTime).format('YYYY-MM-DD HH:mm') : '--:--'}</span>
              </div>
              <div className="rank-page-progress" data-status={progressStatus} data-freeze={board ? hasBoardFreezeWindow(board) : undefined}>
                <Slider className="contest-slider" disabled={!board || progressStatus === 'PENDING'} aria-label="Contest time replay" min={0} max={totalDuration} value={displayTime} onChange={onSliderChange} onChangeComplete={onSliderAfterChange} tooltip={{ formatter: value => formatDuration(value || 0) }} />
                <span className="rank-page-progress-caption" aria-live="polite">
                  {progressStatus && progressStatus !== 'PENDING' ? (isLiveMode ? { PENDING: '', RUNNING: 'Contest Running', FROZEN: 'Contest Frozen', ENDED: 'Contest Ended' }[progressStatus] : `Replay ${formatDuration(displayTime)}`) : ''}
                </span>
                {progressStatus !== 'PENDING' && freezePoint !== null && <Tooltip title={`Freeze at ${dayjs(board?.freezeTime).format('HH:mm')}`}><div className="freeze-mark" style={{ left: `${freezePoint}%` }} /></Tooltip>}
              </div>
              <div className="rank-page-timeline-bottom">
                <div className="rank-page-time-progress" data-replay={!isLiveMode}>
                  <span>{formatDuration(displayTime)}<span className="rank-page-time-progress-total"> / {formatDuration(totalDuration)}</span></span>
                  {!isLiveMode && <Button type="primary" size="small" icon={<SyncOutlined />} onClick={handleResetLive}>Return to Live</Button>}
                </div>
                {((board?.type === 'GPLT' && gpltScope === 'personal') || board?.type === 'LANQIAO') && (
                  <div className="rank-page-legend">
                    {GPLT_SCORE_LEVELS.map(level => (
                      <Tag
                        key={level.label}
                        style={{ backgroundColor: level.color, borderColor: level.color, color: level.textColor }}
                      >
                        {level.label}
                      </Tag>
                    ))}
                  </div>
                )}
                {board?.type === 'XCPC' && (
                  <div className="rank-page-legend">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><div style={{ width: 16, height: 16, background: BOARD_COLORS.firstAc, borderRadius: 3 }} /> First AC</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><div style={{ width: 16, height: 16, background: BOARD_COLORS.ac, borderRadius: 3 }} /> AC</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><div style={{ width: 16, height: 16, background: BOARD_COLORS.frozen, borderRadius: 3 }} /> Frozen</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><div style={{ width: 16, height: 16, background: BOARD_COLORS.wa, borderRadius: 3 }} /> WA</div>
                  </div>
                )}
              </div>
            </div>
        </div>

        <div className="rank-page-content">
          {!board ? <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div> : pending ? (
            <div className="board-prestart">
              <div className="board-countdown" aria-label="Time until contest begins">
                <div>距离比赛开始还有：</div>
                <div className="board-countdown-units">
                  {(() => {
                    const seconds = Math.max(0, dayjs(board.startTime).diff(now, 'second'));
                    return [[Math.floor(seconds / 3600), '小时'], [Math.floor(seconds / 60) % 60, '分钟'], [seconds % 60, '秒']].map(([value, label]) => (
                      <div className="board-countdown-unit" key={label}><strong>{String(value).padStart(2, '0')}</strong><span>{label}</span></div>
                    ));
                  })()}
                </div>
              </div>
              <div className="board-participant-grid">
                {board.rows.map(row => <BoardParticipantCard key={row.itemId} row={row} isTeam={!isPersonalBoard} />)}
              </div>
              {!board.rows.length && <Empty description="暂无参赛人员" />}
            </div>
          ) : <>
          <Tabs activeKey={activeTab} onChange={setActiveTab} items={[{ label: 'Leaderboard', key: 'rank' }, { label: 'Submissions', key: 'submissions' }, { label: 'Statistics', key: 'statistics' }]} />

          {activeTab === 'rank' && (
            <>
              <div className="rank-page-filters">
                {(board?.type === 'GPLT' || board?.type === 'LANQIAO') && (
                  <>
                    <Select getPopupContainer={trigger => trigger.parentElement || trigger} placeholder="Select a group" style={{ width: 180 }} value={gpltGroup} onChange={setGpltGroup} status={!gpltGroup ? 'warning' : ''}>
                      {board.type === 'GPLT' ? <><Option value="星芒初绽">星芒初绽</Option><Option value="长风破浪">长风破浪</Option><Option value="凌霄问鼎">凌霄问鼎</Option></> : <><Option value="C/C++程序设计">C/C++程序设计</Option><Option value="Python程序设计">Python程序设计</Option><Option value="Java软件开发">Java软件开发</Option></>}
                    </Select>
                    {board.type === 'GPLT' && <Radio.Group value={gpltScope} onChange={event => setGpltScope(event.target.value)} buttonStyle="solid"><Radio.Button value="team">Team Ranking</Radio.Button><Radio.Button value="personal">Person Ranking</Radio.Button></Radio.Group>}
                  </>
                )}
                <Select getPopupContainer={trigger => trigger.parentElement || trigger} value={filterType} onChange={setFilterType} style={{ width: 150 }}>
                  <Option value="all">All Participants</Option><Option value="official">Official</Option><Option value="star">Unofficial</Option>{board?.type !== 'GPLT' && board?.type !== 'LANQIAO' && <Option value="girl">Female Team</Option>}
                </Select>
                <Select<number[], ParticipantSearchOption>
                  className="rank-participant-search"
                  mode="multiple"
                  allowClear
                  maxTagCount="responsive"
                  placeholder={isPersonalBoard ? 'Search name or institution to pin...' : 'Search team or institution to pin...'}
                  aria-label={isPersonalBoard ? 'Search and pin people by name or institution' : 'Search and pin teams by team name or institution'}
                  prefix={<SearchOutlined style={{ color: '#9ca3af' }} />}
                  value={selectedParticipantIds}
                  options={participantOptions}
                  disabled={!board || loading || ((board.type === 'GPLT' || board.type === 'LANQIAO') && !gpltGroup)}
                  showSearch={{
                    searchValue: searchText,
                    onSearch: value => { setSearchText(value); setSearchOpen(true); },
                    autoClearSearchValue: true,
                    filterOption: (input, option) => !!option && matchesBoardParticipantSearch(option.participant, input)
                  }}
                  open={searchOpen && !!searchText.trim()}
                  onOpenChange={setSearchOpen}
                  onChange={setSelectedParticipantIds}
                  onSelect={() => setSearchText('')}
                  onClear={() => { setSearchText(''); setSearchOpen(false); }}
                  notFoundContent="No matching participants"
                  getPopupContainer={trigger => trigger.parentElement || trigger}
                  optionRender={({ data }) => (
                    <div className="rank-participant-option">
                      <span>{renderParticipantName(data.participant.name, data.participant)}</span>
                      <span className="rank-participant-option-meta">
                        {data.participant.organization || 'Unknown institution'} · Rk {data.participant.rank === -1 ? '*' : data.participant.rank}
                      </span>
                    </div>
                  )}
                />
                <div style={{ flex: 1 }} />
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [
                      { key: 'excel', label: 'Export Spreadsheet', icon: <FileExcelOutlined />, disabled: exportLoading || pdfExporting || !board },
                      { key: 'pdf', label: 'Export Long Image', icon: <FilePdfOutlined />, disabled: exportLoading || pdfExporting || !board }
                    ],
                    onClick: ({ key }) => { if (key === 'pdf') void handleExportPdf(); else void handleExport(); }
                  }}
                >
                  <Button icon={<ExportOutlined />} loading={exportLoading}>Export Leaderboard <DownOutlined /></Button>
                </Dropdown>
                {board?.type === 'XCPC' && board.resolverEnabled && <Button icon={<PlayCircleOutlined />} onClick={() => { void document.documentElement.requestFullscreen?.({ navigationUI: 'hide' }).catch(() => {}); setResolverOpen(true); }}>Start Resolver</Button>}
                <Button type="text" icon={<SyncOutlined />} onClick={() => loadData(false)}>Refresh</Button>
              </div>
              {(board?.type === 'GPLT' || board?.type === 'LANQIAO') && !gpltGroup ? <Empty description="Select a group to view the leaderboard" style={{ margin: '40px 0' }} /> : board && <ExternalBoardStandings rows={processedRows} problems={board.problems} isTeam={!isPersonalBoard} scored={board.type !== 'XCPC'} showSummary={board.type !== 'GPLT' || isPersonalBoard} getProblemHoverProps={getProblemHoverProps} />}
            </>
          )}

          {activeTab === 'submissions' && (
            <div ref={submissionsViewRef}>
              <div className="board-submission-filters" style={{ padding: '0 0 12px 0', display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12, flexWrap: 'nowrap' }}>
                <Select getPopupContainer={trigger => trigger.parentElement || trigger} placeholder="Problem" allowClear style={{ width: 140 }} value={subProblemId} onChange={setSubProblemId}>{board?.problems.map(problem => <Option key={problem.id} value={problem.id}>{problem.id}. {problem.name}</Option>)}</Select>
                <Select getPopupContainer={trigger => trigger.parentElement || trigger} placeholder="Status" allowClear style={{ width: 140 }} value={subStatus} onChange={setSubStatus}>{['Accepted', 'Wrong Answer', 'Time Exceeded', 'Runtime Error', 'Compile Error', 'Pending'].map(status => <Option key={status} value={status}>{status}</Option>)}</Select>
                <Input placeholder="Search..." prefix={<SearchOutlined />} value={subKeyword} onChange={event => setSubKeyword(event.target.value)} style={{ width: 240 }} />
                <Button type="primary" icon={<SyncOutlined />} onClick={() => { void loadSubmissions(1); }}>Refresh</Button>
              </div>
              <Table className="board-submissions-table" dataSource={submissions} rowKey="submissionId" size="small" pagination={{ current: subPage, total: subTotal, pageSize: 20, onChange: loadSubmissions }} columns={submissionColumns} />
            </div>
          )}

          {activeTab === 'statistics' && (
            <div style={{ padding: '16px 0' }}>
              {statsLoading && !stats ? <div style={{ textAlign: 'center', padding: 50 }}><Spin /></div> : stats ? (
                <>
                  <Row gutter={16} style={{ marginBottom: 32 }}>
                    <Col span={6}><Card><Statistic title="Problems" value={stats.problemCount} prefix={<SolutionOutlined />} /></Card></Col>
                    <Col span={6}><Card><Statistic title="Organizations" value={stats.organizationCount} prefix={<BankOutlined />} /></Card></Col>
                    <Col span={6}><Card><Statistic title="Participants" value={stats.participantCount} prefix={<TeamOutlined />} /></Card></Col>
                    <Col span={6}><Card><Statistic title="Total Submissions" value={stats.totalSubmissions} prefix={<NumberOutlined />} /></Card></Col>
                  </Row>
                  <Row gutter={[24, 24]}>
                    <Col span={24}>{submissionChart && <Card variant="borderless">{<ReactECharts option={submissionChart} style={{ height: 400 }} />}</Card>}</Col>
                    <Col span={24}>{problemPassChart && <Card variant="borderless">{<ReactECharts option={problemPassChart} style={{ height: 400 }} />}</Card>}</Col>
                    <Col span={24}>{teamPassChart && <Card variant="borderless">{<ReactECharts option={teamPassChart} style={{ height: 400 }} />}</Card>}</Col>
                  </Row>
                </>
              ) : <Empty description="No statistics available" />}
            </div>
          )}
          </>}
        </div>
      </div>

      {resolverOpen && board && <BoardResolver boardId={id} unfrozen={!!board.unfrozen} onClose={() => { void closeResolver(); }} />}
      {problemPopup}

      <Modal transitionName="" rootClassName="external-board-modal board-submission-detail-modal board-desktop-modal" style={desktopModalStyle(900, viewportSize)} title={`提交记录 #${currentSubmission?.submissionId || ''}`} open={codeModalOpen} onCancel={() => { submissionCodeRequest.current?.abort(); setCodeModalOpen(false); }} width={900} footer={null} centered>
        <div className="board-submission-info">
          {currentSubmission && (
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label={participantLabel}>{formatSubmissionParticipant(currentSubmission, isPersonalBoard)}</Descriptions.Item>
              <Descriptions.Item label="Problem"><span className="board-submission-problem">{currentSubmission.displayId} - {currentSubmission.problemName}</span></Descriptions.Item>
              <Descriptions.Item label="Status">{renderStatusTag(currentSubmission.status)}</Descriptions.Item>
              <Descriptions.Item label="Score">{currentSubmission.scoreDisplay || currentSubmission.score}</Descriptions.Item>
              <Descriptions.Item label="Time">{currentSubmission.time || '-'}</Descriptions.Item>
              <Descriptions.Item label="Memory">{currentSubmission.memory || '-'}</Descriptions.Item>
              <Descriptions.Item label="Language">{codeLanguage || '-'}</Descriptions.Item>
              <Descriptions.Item label="Date">{dayjs(currentSubmission.submitTime).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
            </Descriptions>
          )}
        </div>
        <Spin spinning={codeLoading}>{codeError ? <Alert type="info" showIcon title={codeError} /> : <SubmissionCodeBlock code={codeContent} language={codeLanguage} />}</Spin>
      </Modal>
    </div>
    </div>
    </ConfigProvider>
  );
}
