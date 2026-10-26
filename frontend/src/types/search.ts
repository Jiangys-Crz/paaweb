import type { UserSearchResult } from './user';

export interface SearchNoticeItem {
  infoId: number;
  title: string;
  createTime: string;
}

export interface SearchContestCategoryItem {
  categoryId: number;
  name: string;
  description?: string;
}

export interface SearchEventItem {
  boardId?: number;
  actualStartTime?: string;
  actualEndTime?: string;
  eventId: number;
  name: string;
  status: number;
  type: string;
  signupStart: string;
  signupEnd: string;
  eventStart: string;
  eventEnd: string;
}

export interface SearchTrainingItem {
  trainId: number;
  name: string;
}

export type SearchUserItem = UserSearchResult;

export interface GlobalSearchResult {
  notices: SearchNoticeItem[];
  contestIntros: SearchContestCategoryItem[]; 
  contestSignups: SearchEventItem[];          
  contestBoards: SearchEventItem[];           
  trainings: SearchTrainingItem[];
  users: SearchUserItem[];
}