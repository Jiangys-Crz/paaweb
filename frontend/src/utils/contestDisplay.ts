const CONTEST_TYPE_TEXT: Record<string, string> = {
  XCPC: 'XCPC',
  LANQIAO: 'LANQIAO',
  GPLT: 'GPLT'
};

const SIGNUP_STATUS_TEXT: Record<string, string> = {
  approved: '已通过',
  pending: '待审核',
  rejected: '已拒绝',
  cancelled: '已取消'
};

export const contestTypeText = (value?: string | null, fallback = '未设置') => (
  value ? CONTEST_TYPE_TEXT[value] || value : fallback
);

export const signupGroupText = (value?: string | null, isTeam = false, fallback = '未设置') => {
  if (!value) return fallback;
  if (value === 'official') return isTeam ? '正式队伍' : '正式参赛';
  if (value === 'star') return isTeam ? '打星队伍' : '打星参赛';
  if (value === 'girl') return '女生队伍';
  return value;
};

export const signupSourceText = (value?: string | null) => {
  if (value === 'school_admin') return '学校管理员报名';
  if (value === 'admin_import') return '赛事管理员导入';
  if (value === 'personal') return '用户自主报名';
  return '用户自主报名';
};

export const signupStatusText = (value?: string | null) => (
  value ? SIGNUP_STATUS_TEXT[value] || '未知状态' : '未知状态'
);

export const rankingText = (value?: number | string | null) => {
  if (value === 0 || value === '0') return '否';
  if (value === 1 || value === '1') return '是';
  return '未设置';
};
