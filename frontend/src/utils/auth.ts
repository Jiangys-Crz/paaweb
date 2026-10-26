"use client";

import axios from 'axios';

export interface StoredUserInfo {
  userId?: number;
  userName?: string;
  userAccount?: string;
  realName?: string;
  avatarUrl?: string;
  accountStatus?: string;
  role?: string;
  gender?: number;
  permissions?: string[];
  token?: string;
  accessToken?: string;
}

const USER_COOKIE = 'paa_user_info';
const TOKEN_COOKIE = 'paa_auth_token';
const MAX_AGE = 60 * 60 * 24 * 15;

const encode = (value: string) => encodeURIComponent(value);
const decode = (value: string) => decodeURIComponent(value);

const setCookie = (name: string, value: string, maxAge = MAX_AGE) => {
  const isSecure = typeof window !== 'undefined' && window.location.protocol === 'https:';
  document.cookie = `${name}=${encode(value)}; Max-Age=${maxAge}; Path=/; SameSite=Lax${isSecure ? '; Secure' : ''}`;
};

const getCookie = (name: string) => {
  const prefix = `${name}=`;
  const found = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return found ? decode(found.slice(prefix.length)) : '';
};

const deleteCookie = (name: string) => {
  document.cookie = `${name}=; Max-Age=0; Path=/; SameSite=Lax`;
};

export const persistAuth = (user: StoredUserInfo) => {
  const token = user.token || user.accessToken || String(user.userId || '');
  setCookie(USER_COOKIE, JSON.stringify(user));
  if (token) setCookie(TOKEN_COOKIE, token);
  localStorage.setItem('user_info', JSON.stringify(user));
  window.dispatchEvent(new Event('auth-state-change'));
};

export const readStoredUser = (): StoredUserInfo | null => {
  try {
    const cookieUser = getCookie(USER_COOKIE);
    if (cookieUser) {
      const parsed = JSON.parse(cookieUser);
      localStorage.setItem('user_info', cookieUser);
      return parsed;
    }

    const legacyUser = localStorage.getItem('user_info');
    if (legacyUser) {
      const parsed = JSON.parse(legacyUser);
      setCookie(USER_COOKIE, legacyUser);
      const token = parsed.token || parsed.accessToken || String(parsed.userId || '');
      if (token) setCookie(TOKEN_COOKIE, token);
      return parsed;
    }
  } catch {
    return null;
  }
  return null;
};

export const clearAuth = () => {
  deleteCookie(USER_COOKIE);
  deleteCookie(TOKEN_COOKIE);
  localStorage.removeItem('user_info');
  window.dispatchEvent(new Event('auth-state-change'));
};

/**
 * 退出登录：除了清理本地缓存，还必须让服务端 HttpSession 失效。
 * 只清浏览器缓存会让会话在服务端继续有效 30 天，换账号登录时可能拿到旧身份。
 */
export const logout = async () => {
  try {
    await axios.post('/api/user/logout', null, { withCredentials: true });
  } catch {
    // 网络异常或会话已失效时依然要清理本地状态，不能把用户卡在登录态。
  }
  clearAuth();
};

/** 标记"鉴权同步"请求，网关限流时不应把用户从当前页面重定向到 /busy。 */
export const AUTH_SYNC_HEADER = 'X-Auth-Sync';

let authRefreshPromise: Promise<StoredUserInfo | null> | null = null;

/**
 * 从服务端重新获取当前用户。身份状态、角色和权限以服务端为准，本地存储仅作为
 * 首屏展示缓存；并发触发时复用同一个请求，避免焦点切换和 SSE 事件造成请求风暴。
 *
 * 该请求失败（限流、网络抖动、502）时保留上一次状态：鉴权同步是后台行为，
 * 既不能把用户踢到 /busy，也不能因为一次失败就当成退出登录。
 */
export const refreshAuth = (): Promise<StoredUserInfo | null> => {
  if (authRefreshPromise) return authRefreshPromise;

  authRefreshPromise = axios.get<{ code: number; data: StoredUserInfo | null }>('/api/user/me', {
    withCredentials: true,
    headers: { 'Cache-Control': 'no-cache', [AUTH_SYNC_HEADER]: '1' },
  }).then((response) => {
    if (response.data?.code !== 200 || !response.data.data) {
      // 服务端明确回答"当前没有登录会话"，此时才清理本地缓存。
      clearAuth();
      return null;
    }
    persistAuth(response.data.data);
    return response.data.data;
  }).catch(() => null).finally(() => {
    authRefreshPromise = null;
  });

  return authRefreshPromise;
};

const isAuthSyncRequest = (config?: { headers?: unknown }) => {
  const headers = config?.headers as Record<string, unknown> | { get?: (name: string) => unknown } | undefined;
  if (!headers) return false;
  const getter = (headers as { get?: (name: string) => unknown }).get;
  if (typeof getter === 'function') return getter.call(headers, AUTH_SYNC_HEADER) === '1';
  return (headers as Record<string, unknown>)[AUTH_SYNC_HEADER] === '1'
    || (headers as Record<string, unknown>)[AUTH_SYNC_HEADER.toLowerCase()] === '1';
};

// Global Axios response interceptor to handle session/cookie expiration (401 Unauthorized)
if (typeof window !== 'undefined') {
  let redirectingForTraffic = false;

  axios.interceptors.response.use(
    (response) => {
      // If the backend returns a custom 401 code within a 200 OK HTTP response
      if (response.data && response.data.code === 401) {
        clearAuth();
        if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
          window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`;
        }
      }
      return response;
    },
    (error) => {
      const status = error.response?.status;
      const responseCode = error.response?.data?.code;

      // Nginx and the backend both use 429 (and 503 for an exhausted expensive
      // work slot) when the server is overloaded. Navigate away from the page
      // that received the response so the raw JSON body is never shown to the
      // user. The busy page will retry this exact URL after its countdown.
      const trafficLimited = status === 429 || status === 503 || responseCode === 429 || responseCode === 503;
      if (trafficLimited && !isAuthSyncRequest(error.config)
          && !window.location.pathname.startsWith('/busy') && !redirectingForTraffic) {
        redirectingForTraffic = true;
        const returnTo = window.location.href;
        window.location.replace(`/busy?returnTo=${encodeURIComponent(returnTo)}&status=${status || responseCode}`);
        // Do not let individual page catch blocks render the raw gateway JSON
        // while the browser is navigating to the friendly retry page.
        return new Promise<never>(() => undefined);
      }

      // If the backend returns HTTP 401 status code
      if (error.response && error.response.status === 401) {
        clearAuth();
        if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
          window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`;
        }
      }
      return Promise.reject(error);
    }
  );
}
