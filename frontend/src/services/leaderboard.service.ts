import axios from 'axios';
import type { ExternalBoardConfig, ExternalBoardSummary, BoardVO, BoardStats, SubmissionVO } from '../types/leaderboard';

const API_PREFIX = '/api'; 
export const getLeaderboard = async (
    boardId: string | number,
    timePoint?: number, 
    scope?: string, 
    group?: string
) => {
  const params: Record<string, string | number> = {};
  if (timePoint) params.timePoint = timePoint;
  if (scope) params.scope = scope;
  if (group && group !== 'all') params.group = group;
  
  return axios.get<{ code: number; data: BoardVO }>(`${API_PREFIX}/leaderboard/${boardId}`, { params });
};

export const getBoardStats = async (boardId: string | number, timePoint?: number) => {
  const params = timePoint ? { timePoint } : {};
  return axios.get<{ code: number; data: BoardStats }>(`${API_PREFIX}/leaderboard/${boardId}/stats`, { params });
};

export const getBoardConfig = async (boardId: string | number) => {
  return axios.get<{ code: number; data: ExternalBoardConfig }>(`${API_PREFIX}/leaderboard/admin/config/${boardId}`);
};

export const updateBoardConfig = async (data: ExternalBoardConfig) => {
  return axios.post<{ code: number; msg: string }>(`${API_PREFIX}/leaderboard/admin/config/update`, data);
};

export const getBoardSubmissions = (boardId: string | number, params: Record<string, string | number | undefined>) => {
    return axios.get<{ code: number, data: { records: SubmissionVO[], total: number } }>(
        `/api/leaderboard/submissions/${boardId}`,
        { params }
    );
};

export const getSubmissionCode = (submissionId: string | number) => {
    return axios.get<{ code: number, msg?: string, data: { content: string, language: string } }>(
        `/api/leaderboard/submission/${submissionId}/code`
    );
};

export const listEventBoards = (eventId: number) => axios.get<{ code: number; data: ExternalBoardSummary[] }>(
  `${API_PREFIX}/leaderboard/admin/event/${eventId}/boards`
);

export const createBoard = (eventId: number, name: string) => axios.post<{ code: number; data: ExternalBoardConfig; msg?: string }>(
  `${API_PREFIX}/leaderboard/admin/boards`, { eventId, name }
);

export const deleteBoard = (boardId: number) => axios.delete<{ code: number; msg?: string }>(
  `${API_PREFIX}/leaderboard/admin/boards/${boardId}`
);
