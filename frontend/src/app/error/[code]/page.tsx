"use client";

import { useParams } from 'next/navigation';
import ErrorStatePage from '../../components/ErrorStatePage';

export default function ErrorPage() {
  const params = useParams<{ code?: string }>();
  return <ErrorStatePage code={params.code || '404'} />;
}
