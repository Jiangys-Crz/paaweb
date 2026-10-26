"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import { Alert, Button, Card, Descriptions, Divider, Popconfirm, Result, Skeleton, Space, Tag, Typography, message } from 'antd';
import { ArrowLeftOutlined, DownloadOutlined, PaperClipOutlined, SmileOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { readStoredUser } from '@/utils/auth';
import ActivityLayout from '../../contest/ContestNav';
import MarkdownContent from '../../components/content/MarkdownContent';

const { Title } = Typography;

interface TrainingData {
  name: string;
  signupStart: string;
  signupEnd: string;
  signupCount: number;
  maxCount: number;
  termName: string;
  teacherName: string;
  publisherName: string;
  trainStart: string;
  trainEnd: string;
  description?: string;
  attachments?: { fileId: string; name: string }[];
}

export default function TrainingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const user = readStoredUser();
  const [data, setData] = useState<TrainingData | null>(null);
  const [signupStatus, setSignupStatus] = useState('none');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const refresh = async () => {
    const detailRes = await axios.get(`/api/training/detail/${id}`);
    setData(detailRes.data.data || null);
    if (user) {
      const statusRes = await axios.get('/api/training/signup-status', { params: { trainId: id } });
      setSignupStatus(statusRes.data.data || 'none');
    }
  };

  useEffect(() => {
    refresh().finally(() => setLoading(false));
  }, [id, user?.userId]);

  const handleSignup = async () => {
    if (!user) return router.push('/login');
    if (user.accountStatus !== 'normal') return message.error('账号状态异常，未进行认证或受限');
    setActionLoading(true);
    try {
      const res = await axios.post('/api/training/signup', { trainId: id, userId: user.userId });
      if (res.data.code === 200) {
        message.success('报名成功');
        await refresh();
      } else message.error(res.data.msg || '报名失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!user) return;
    setActionLoading(true);
    try {
      const res = await axios.post('/api/training/cancel', { trainId: id, userId: user.userId });
      if (res.data.code === 200) {
        message.success('已取消报名');
        await refresh();
      } else message.error(res.data.msg || '取消失败');
    } finally {
      setActionLoading(false);
    }
  };

  const now = dayjs();
  const statusTag = data ? (
    now.isBefore(data.signupStart) ? <Tag color="orange">报名未开始</Tag> :
    now.isBefore(data.signupEnd) ? <Tag color="green">报名进行中</Tag> :
    now.isBefore(data.trainStart) ? <Tag color="blue">报名已结束</Tag> :
    now.isBefore(data.trainEnd) ? <Tag color="red">培训进行中</Tag> :
    <Tag>已结束</Tag>
  ) : null;
  const canSignup = data && now.isAfter(data.signupStart) && now.isBefore(data.signupEnd) && data.signupCount < data.maxCount;
  const signupEnded = data ? !now.isBefore(data.signupEnd) : false;
  const isSignedUp = signupStatus === 'approved' || signupStatus === 'pending';
  const signupButtonText = data
    ? signupEnded
      ? '已结束'
      : data.signupCount >= data.maxCount
        ? '名额已满'
        : '立即报名'
    : '立即报名';

  return (
    <ActivityLayout>
      <section className="activity-content">
        <Button className="bits-ghost-button" icon={<ArrowLeftOutlined />} onClick={() => router.push('/training')} style={{ marginBottom: 16 }}>返回培训列表</Button>
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {data && (
            <Card className="content-main-card intro-content-card" variant="borderless" style={{ width: 'min(980px, 100%)', margin: '0 auto' }}>
              <div className="activity-detail-heading" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
                <Title level={2} style={{ margin: 0 }}>{data.name}</Title>
                {statusTag}
              </div>
              <Space wrap className="activity-meta-row" style={{ marginBottom: 20 }}>
                <span><UserOutlined /> 讲师：{data.teacherName}</span>
                <span><TeamOutlined /> 报名：{data.signupCount}/{data.maxCount}</span>
                <span>发布者：{data.publisherName}</span>
              </Space>
              
              <Divider style={{ margin: '0 0 24px 0' }} />
              
              <Descriptions bordered column={1} style={{ marginBottom: 24 }}>
                <Descriptions.Item label="报名时间">{dayjs(data.signupStart).format('YYYY-MM-DD HH:mm')} 至 {dayjs(data.signupEnd).format('YYYY-MM-DD HH:mm')}</Descriptions.Item>
                <Descriptions.Item label="课程时间">{dayjs(data.trainStart).format('YYYY-MM-DD HH:mm')} 至 {dayjs(data.trainEnd).format('YYYY-MM-DD HH:mm')}</Descriptions.Item>
              </Descriptions>
              
              <div className="center-readable">
                <Title level={4} style={{ marginTop: 24, marginBottom: 16 }}>课程详情</Title>
                <MarkdownContent content={data.description} />
              </div>
              
              {!!data.attachments?.length && (
                <section className="attachment-panel" style={{ marginTop: 24 }}>
                  <strong><PaperClipOutlined /> 附件</strong>
                  {data.attachments.map(file => <Button key={file.fileId} type="link" icon={<DownloadOutlined />} href={`/api/file/download/${file.fileId}`} target="_blank" rel="noopener noreferrer">{file.name}</Button>)}
                </section>
              )}
              
              <Divider style={{ margin: '24px 0' }} />
              
              <div className="signup-action-zone">
                {!user ? <Button className="bits-primary-button" onClick={() => router.push('/login')}>请登录后报名</Button> : isSignedUp ? (
                  <Result
                    status="success"
                    icon={<SmileOutlined />}
                    title={signupStatus === 'approved' ? '您已成功报名本次培训' : '报名审核中'}
                    extra={signupEnded ? <Button disabled>报名已结束</Button> : <Popconfirm title="确定取消报名？" onConfirm={handleCancel}><Button danger loading={actionLoading}>取消报名</Button></Popconfirm>}
                  />
                ) : (
                  <>
                    {signupStatus === 'rejected' && <Alert type="error" showIcon message="报名已拒绝，可重新提交报名。" />}
                    <Button className="bits-primary-button" disabled={!canSignup} loading={actionLoading} onClick={handleSignup}>{signupButtonText}</Button>
                  </>
                )}
              </div>
            </Card>
          )}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
