import axios from 'axios';

// 定义接口返回的数据类型
export interface UserPracticeStats {
  userId: number;
  userName: string;
  realName: string;
  numberId: string;
  collegeName: string;
  majorName: string;
  className: string;
  luoguSolved: number;
  nowcoderSolved: number;
  codeforcesSolved: number;
  atcoderSolved: number;
  cwnuSolved: number;
  totalSolved: number;
  isEnrolledStu: number;
  gradeYear: string;
  eduBg: string;
}

export interface OverallStats {
  userCount: number;
  totalSolved: number;
  averageSolved: string;
  last30DaysSolved: number;
  last7DaysSolved: number;
}

export interface UserStatsParams {
  page?: number;
  size?: number;
  keyword?: string;
  universityId?: number;
  schoolId?: number;
  majorId?: number;
  className?: string;
  gradeYear?: string;
  isEnrolledStu?: number;
  eduBg?: string;
}

// 获取整体统计数据
export const getOverallStats = async (params?: UserStatsParams) => {
  const response = await axios.get('/api/admin/data-panel/stats', { params });
  return response.data;
};

// 获取用户刷题统计列表
export const getUserPracticeStats = async (params: UserStatsParams) => {
  const response = await axios.get('/api/admin/data-panel/users', { params });
  return response.data;
};
