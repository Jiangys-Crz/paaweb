import axios from 'axios';
import type { NotificationVO } from '../types/notification';

const API_PREFIX = '/api/notification';

export const getNotificationList = async (page: number, size: number) => {
    return axios.get<{ code: number; data: { records: NotificationVO[]; total: number } }>(
        `${API_PREFIX}/list`,
        { params: { page, size } }
    );
};

export const getUnreadCount = async () => {
    return axios.get<{ code: number; data: number }>(`${API_PREFIX}/unread-count?_t=${Date.now()}`);
};

export const markAsRead = async (msgId: number) => {
    return axios.post<{ code: number; msg: string }>(`${API_PREFIX}/read/${msgId}`);
};

export const markAllAsRead = async () => {
    return axios.post<{ code: number; msg: string }>(`${API_PREFIX}/read-all`);
};