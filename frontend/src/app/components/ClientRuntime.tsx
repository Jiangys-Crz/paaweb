"use client";

// Import client-wide authentication and traffic protection interceptors once
// from the root layout. Individual pages may use axios without rendering the
// site header, so relying on a page component to initialize this side effect
// would allow raw 429/503 responses to leak through on those pages.
import { useEffect, useState } from 'react';
import { readStoredUser, refreshAuth } from '@/utils/auth';

// SSE 断线（网关重启、网络切换、休眠唤醒）时兜底轮询的间隔。正常情况下权限
// 变更由服务端 auth-state-change 事件即时推送，这里只作为最后的保障。
const AUTH_SYNC_INTERVAL_MS = 60_000;

const ClientRuntime = () => {
  const [userId, setUserId] = useState<number | null>(null);

  useEffect(() => {
    const syncStoredUser = () => setUserId(readStoredUser()?.userId ?? null);
    const refreshIfLoggedIn = () => {
      if (document.visibilityState === 'hidden') return;
      if (readStoredUser()?.userId) void refreshAuth();
    };

    syncStoredUser();
    refreshIfLoggedIn();
    window.addEventListener('storage', syncStoredUser);
    window.addEventListener('auth-state-change', syncStoredUser);
    window.addEventListener('focus', refreshIfLoggedIn);
    // 从 bfcache 恢复或网络恢复后，本地快照可能已经过期。
    window.addEventListener('pageshow', refreshIfLoggedIn);
    window.addEventListener('online', refreshIfLoggedIn);
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') refreshIfLoggedIn();
    };
    document.addEventListener('visibilitychange', handleVisibility);

    // 权限/身份状态必须实时生效，而 SSE 在移动端切后台、网关重启后会断开，
    // 因此额外做一次低频兜底同步：只要页面可见就定期向服务端核对一次。
    const interval = window.setInterval(refreshIfLoggedIn, AUTH_SYNC_INTERVAL_MS);

    return () => {
      window.removeEventListener('storage', syncStoredUser);
      window.removeEventListener('auth-state-change', syncStoredUser);
      window.removeEventListener('focus', refreshIfLoggedIn);
      window.removeEventListener('pageshow', refreshIfLoggedIn);
      window.removeEventListener('online', refreshIfLoggedIn);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (!userId) return;

    // 鉴权变更与站内通知共用同一条 SSE，保证权限撤销也能立刻反映到当前页面。
    const eventSource = new EventSource('/api/notification/stream', { withCredentials: true });
    const handleNotification = (event: MessageEvent) => {
      try {
        window.dispatchEvent(new CustomEvent('realtime-notification', { detail: JSON.parse(event.data) }));
      } catch {
        // 忽略格式异常的单条消息，浏览器会继续保持连接。
      }
    };
    const handleAuthStateChange = () => {
      void refreshAuth();
    };
    // 长连接出错时（例如会话已失效）立刻核对一次登录态，避免界面继续以
    // 已登录身份渲染；EventSource 自身会在 3 秒后自动重连。
    const handleError = () => {
      void refreshAuth();
    };

    eventSource.addEventListener('notification', handleNotification as EventListener);
    eventSource.addEventListener('auth-state-change', handleAuthStateChange);
    eventSource.addEventListener('error', handleError);
    return () => eventSource.close();
  }, [userId]);

  return null;
};

export default ClientRuntime;
