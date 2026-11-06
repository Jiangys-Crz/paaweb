export type BoardParticipantFilter = 'all' | 'official' | 'star' | 'girl';

export const getBoardParticipantKind = (row: { isStar?: boolean; isGirl?: boolean; group?: string; rank?: number }) =>
  row.isStar || row.group === 'star' || row.rank === -1 ? 'star' : row.isGirl || row.group === 'girl' ? 'girl' : 'official';

export const compareBoardRank = (a: { rank?: number }, b: { rank?: number }) => {
  const left = a.rank != null && a.rank > 0 ? a.rank : Infinity;
  const right = b.rank != null && b.rank > 0 ? b.rank : Infinity;
  return left === right ? 0 : left < right ? -1 : 1;
};

export const matchesBoardParticipantType = (
  participant: { isStar?: boolean; isGirl?: boolean },
  filter: BoardParticipantFilter
) => {
  if (filter === 'official') return !participant.isStar;
  if (filter === 'star') return !!participant.isStar;
  if (filter === 'girl') return !participant.isStar && !!participant.isGirl;
  return true;
};

export const matchesBoardParticipantSearch = (
  participant: { name: string; organization?: string },
  query: string
) => {
  const terms = query.normalize('NFKC').trim().toLowerCase().split(/\s+/).filter(Boolean);
  const fields = [participant.name, participant.organization || ''].map(value => value.normalize('NFKC').toLowerCase());
  return terms.every(term => fields.some(field => field.includes(term)));
};

export interface BoardDisplayRowMeta {
  boardRowKey: string;
  boardPosition: number;
  isPinned: boolean;
}

export const buildBoardDisplayRows = <T extends { itemId: number; rank?: number }>(
  rows: readonly T[],
  selectedIds: readonly number[]
): (T & BoardDisplayRowMeta)[] => {
  const rankedRows = rows.map((row, index) => ({
    ...row,
    boardRowKey: `rank-${row.itemId}`,
    boardPosition: index + 1,
    isPinned: false
  }));
  const rowsById = new Map(rankedRows.map(row => [row.itemId, row]));
  const pinnedRows: (T & BoardDisplayRowMeta)[] = [];
  for (const itemId of new Set(selectedIds)) {
    const row = rowsById.get(itemId);
    if (row) pinnedRows.push({ ...row, boardRowKey: `pinned-${itemId}`, isPinned: true });
  }
  pinnedRows.sort((a, b) => compareBoardRank(a, b) || a.boardPosition - b.boardPosition);
  return [...pinnedRows, ...rankedRows];
};
