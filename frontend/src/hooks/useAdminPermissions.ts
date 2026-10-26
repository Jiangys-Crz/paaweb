"use client";

import { useEffect, useState } from 'react';
import { readStoredUser, type StoredUserInfo } from '@/utils/auth';

export default function useAdminPermissions() {
  const [user, setUser] = useState<StoredUserInfo | null>(null);
  useEffect(() => {
    const sync = () => setUser(readStoredUser());
    sync();
    window.addEventListener('auth-state-change', sync);
    return () => window.removeEventListener('auth-state-change', sync);
  }, []);
  const builtInAdmin = ['supper_admin', 'super_admin'].includes(user?.role || '') && user?.userAccount === 'admin';
  return (permission: string) => builtInAdmin || Boolean(user?.permissions?.includes(permission));
}
