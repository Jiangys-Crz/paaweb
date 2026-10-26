import axios from 'axios';
import type { LeaderboardItem, Term } from '../types/rating';

const API_PREFIX = '/api/rating';

export const getLeaderboard = (termId?: number | null) => {
  const params: Record<string, number> = {};
  if (termId) {
    params.termId = termId;
  }
  return axios.get<{ code: number; data: LeaderboardItem[] }>(`${API_PREFIX}/leaderboard`, { params });
};

export const getTerms = () => {
  return axios.get<{ code: number; data: Term[] }>(`${API_PREFIX}/terms`);
};

export interface RatingHistoryItem {
  contestName: string;
  rank: number;
  oldRating: number;
  newRating: number;
  date: string;
}

export const getRatingHistory = (userId: string | number) => {
  return axios.get<{ code: number; data: RatingHistoryItem[] }>(`${API_PREFIX}/history`, { params: { userId } });
};