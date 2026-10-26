import axios from 'axios';

export interface ExternalApp {
    appId: number;
    appName: string;
    appKey: string;
    status: number;
    externalLoginEnabled: boolean;
    externalBoardEnabled: boolean;
    createTime: string;
}

export type AppKeyPermissions = Pick<ExternalApp, 'externalLoginEnabled' | 'externalBoardEnabled'>;

const API_PREFIX = '/api/admin/app-key';

export const getAppKeyList = () => {
  return axios.get<{ code: number; data: ExternalApp[]; msg?: string }>(`${API_PREFIX}/list`);
};

export const generateAppKey = (appName: string, permissions: AppKeyPermissions) => {
  return axios.post<{ code: number; msg: string; data: string }>(
      `${API_PREFIX}/generate`, 
      null, 
      { params: { appName, ...permissions } }
  );
};

export const updateAppKeyPermissions = (appId: number, permissions: AppKeyPermissions) => {
  return axios.post<{ code: number; msg: string; data: string }>(
      `${API_PREFIX}/permissions`, { appId, ...permissions }
  );
};

export const deleteAppKey = (appId: number) => {
  return axios.delete<{ code: number; msg: string; data: string }>(`${API_PREFIX}/${appId}`);
};

export const toggleAppKeyStatus = (appId: number, status: number) => {
  return axios.post<{ code: number; msg: string }>(
      `${API_PREFIX}/toggle-status`, 
      null, 
      { params: { appId, status } }
  );
};
