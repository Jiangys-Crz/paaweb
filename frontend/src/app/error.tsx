"use client";

import { useEffect } from 'react';
import ErrorStatePage from './components/ErrorStatePage';

interface AppErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function AppError({ error, reset }: AppErrorProps) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return <ErrorStatePage code="500" reset={reset} message={error.digest ? `错误编号：${error.digest}` : undefined} />;
}
