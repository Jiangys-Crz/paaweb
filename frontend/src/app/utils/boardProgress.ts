import dayjs from 'dayjs';

type ContestStatus = 'PENDING' | 'RUNNING' | 'FROZEN' | 'ENDED';

export interface BoardUnfreezeState {
  unfrozen?: boolean;
  isUnfrozen?: boolean;
}

export function readBoardUnfreezeState(board: BoardUnfreezeState): boolean | undefined {
  if (typeof board.unfrozen === 'boolean') return board.unfrozen;
  if (typeof board.isUnfrozen === 'boolean') return board.isUnfrozen;
  return undefined;
}

/** Older leaderboard responses omit this flag; the public event detail still supplies it. */
export async function resolveBoardUnfreezeState(
  board: BoardUnfreezeState,
  loadEvent: () => Promise<BoardUnfreezeState>
): Promise<boolean | undefined> {
  const state = readBoardUnfreezeState(board);
  if (state !== undefined) return state;
  return readBoardUnfreezeState(await loadEvent());
}

interface ProgressBoard extends BoardUnfreezeState {
  status: ContestStatus;
  type?: string;
  startTime?: string;
  endTime?: string;
  freezeTime?: string;
}

export function hasBoardFreezeWindow(board: { type?: string; freezeTime?: string }): boolean {
  return board.type !== 'GPLT' && board.type !== 'LANQIAO'
    && !!board.freezeTime && dayjs(board.freezeTime).isValid();
}

/** Use the displayed contest time, including replay, rather than a cached status. */
export function getBoardProgressStatus(board: ProgressBoard, timePoint = Date.now()): ContestStatus {
  const start = board.startTime ? dayjs(board.startTime).valueOf() : NaN;
  const end = board.endTime ? dayjs(board.endTime).valueOf() : NaN;
  if (Number.isFinite(start) && Number.isFinite(end)) {
    if (timePoint < start) return 'PENDING';
    if (timePoint >= end) return !hasBoardFreezeWindow(board) || readBoardUnfreezeState(board) === true ? 'ENDED' : 'FROZEN';
    if (!hasBoardFreezeWindow(board)) return 'RUNNING';
    const freeze = dayjs(board.freezeTime).valueOf();
    return timePoint >= freeze ? 'FROZEN' : 'RUNNING';
  }
  if (!hasBoardFreezeWindow(board)) return board.status === 'FROZEN' ? 'RUNNING' : board.status;
  if (board.status === 'ENDED') return readBoardUnfreezeState(board) === true ? 'ENDED' : 'FROZEN';
  return board.status;
}
