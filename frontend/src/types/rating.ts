export interface LeaderboardItem {
  rank: number;
  userId: number;
  nickname: string;
  realName: string;
  avatar: string | null;
  bio: string;
  role?: string;
  ratingAll: number;
  ratingContest: number;
  ratingCertificate: number;
  ratingPractice: number;
}

export interface Term {
  termId: number;
  termName: string;
  startTime: string;
  endTime: string | null;
}