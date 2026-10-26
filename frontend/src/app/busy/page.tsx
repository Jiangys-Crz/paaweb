"use client";

import { Suspense, useEffect, useState } from 'react';
import { Card, Progress, Spin, Typography } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { useSearchParams } from 'next/navigation';
import SiteHeader from '../components/SiteHeader';

const RETRY_SECONDS = 10;
const { Title, Paragraph, Text } = Typography;

const safeReturnPath = (value: string) => {
  try {
    const target = new URL(value || '/', window.location.origin);
    if (target.origin !== window.location.origin) return '/';
    return `${target.pathname}${target.search}${target.hash}` || '/';
  } catch {
    return '/';
  }
};

const BusyPageContent = () => {
  const searchParams = useSearchParams();
  const returnTo = searchParams.get('returnTo') || '/';
  const status = searchParams.get('status');
  const [remaining, setRemaining] = useState(RETRY_SECONDS);

  useEffect(() => {
    const target = safeReturnPath(returnTo);
    const deadline = Date.now() + RETRY_SECONDS * 1000;

    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setRemaining(next);
      if (next === 0) {
        window.clearInterval(timer);
        window.location.replace(target);
      }
    }, 250);

    return () => window.clearInterval(timer);
  }, [returnTo]);

  const title = status === '503' ? '服务正在缓冲' : '当前访问人数较多';

  return (
    <main style={{ minHeight: '100vh' }}>
      <SiteHeader />
      <section style={{ minHeight: 'calc(100vh - 100px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '96px 20px 48px' }}>
        <Card bordered={false} style={{ width: 'min(560px, 100%)', textAlign: 'center', borderRadius: 20, boxShadow: '0 20px 60px rgba(22, 82, 118, 0.16)', background: 'rgba(248, 252, 255, 0.94)' }}>
          <div style={{ width: 76, height: 76, margin: '0 auto 20px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'linear-gradient(135deg, #1677ff, #36cfc9)', color: '#fff', fontSize: 34 }}>
            <ThunderboltOutlined />
          </div>
          <Title level={2} style={{ marginBottom: 12 }}>{title}</Title>
          <Paragraph type="secondary" style={{ fontSize: 16 }}>
            为保护服务稳定性，系统会在 10 秒后自动返回刚才的页面并重试，请不要关闭当前页面。
          </Paragraph>
          <div style={{ margin: '26px auto 14px', maxWidth: 320 }}>
            <Progress percent={(RETRY_SECONDS - remaining) * 10} showInfo={false} status="active" />
          </div>
          <Spin size="small" />
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">将在 <Text strong>{remaining}</Text> 秒后自动重试</Text>
          </div>
        </Card>
      </section>
    </main>
  );
};

const BusyPage = () => (
  <Suspense fallback={<div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>}>
    <BusyPageContent />
  </Suspense>
);

export default BusyPage;
