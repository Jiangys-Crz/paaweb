import axios from 'axios';

export interface QqBotConfig {
  enabled: boolean;
  wsUrl: string;
  accessToken?: string | null;
  accessTokenConfigured: boolean;
  hydroBaseUrl: string;
  hydroApiKey?: string | null;
  hydroApiKeyConfigured: boolean;
  reconnectSeconds: number;
  typingMillisPerChar: number;
  minReplyDelayMillis: number;
  maxReplyDelayMillis: number;
  queryGroups: string[];
  pushGroups: string[];
  pushOffline: boolean;
  pushOnline: boolean;
  pushTraining: boolean;
  pushBoard: boolean;
}

export interface QqBotStatus {
  enabled: boolean;
  connected: boolean;
  connecting: boolean;
  lastConnectedAt?: string | null;
  lastError?: string | null;
  queueSize?: number;
  self?: { user_id?: number | string; nickname?: string } | null;
  scheduledTasks?: number;
}

export type QqBotRecurrence = 'ONCE' | 'DAILY' | 'WEEKLY' | 'INTERVAL';

export interface QqBotTask {
  id: string;
  title: string;
  groupId: string;
  content: string;
  images: string[];
  dueAt: string;
  recurrence: QqBotRecurrence;
  intervalMinutes: number | null;
  enabled: boolean;
  lastSentAt?: string | null;
  lastError?: string | null;
}

export type QqBotTaskInput = Omit<QqBotTask, 'id' | 'lastSentAt' | 'lastError'>;

interface ApiResult<T> { code: number; data: T; msg?: string }
const prefix = '/api/admin/qq-bot';

async function unwrap<T>(result: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const { data } = await result;
  if (data.code !== 200) throw new Error(data.msg || '机器人服务请求失败');
  return data.data;
}

export const getQqBotConfig = () => unwrap(axios.get<ApiResult<QqBotConfig>>(`${prefix}/config`));
export const updateQqBotConfig = (config: Omit<QqBotConfig, 'accessTokenConfigured' | 'hydroApiKeyConfigured'>) =>
  unwrap(axios.put<ApiResult<string>>(`${prefix}/config`, config));
export const getQqBotStatus = () => unwrap(axios.get<ApiResult<QqBotStatus>>(`${prefix}/status`));
export const reconnectQqBot = () => unwrap(axios.post<ApiResult<string>>(`${prefix}/reconnect`));
export const getQqBotTasks = () => unwrap(axios.get<ApiResult<QqBotTask[]>>(`${prefix}/tasks`));
export const createQqBotTask = (task: QqBotTaskInput) =>
  unwrap(axios.post<ApiResult<QqBotTask>>(`${prefix}/tasks`, task));
export const updateQqBotTask = (id: string, task: QqBotTaskInput) =>
  unwrap(axios.put<ApiResult<QqBotTask>>(`${prefix}/tasks/${encodeURIComponent(id)}`, task));
export const deleteQqBotTask = (id: string) =>
  unwrap(axios.delete<ApiResult<unknown>>(`${prefix}/tasks/${encodeURIComponent(id)}`));
