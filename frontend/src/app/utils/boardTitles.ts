export function formatBoardTitle(board: { title: string; sessionName?: string }) {
  return board.sessionName?.trim() ? `${board.title} - ${board.sessionName.trim()}` : board.title;
}
