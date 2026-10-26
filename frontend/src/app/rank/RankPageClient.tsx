"use client";

import {
  Avatar,
  Card,
  Col,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CrownOutlined,
  FieldTimeOutlined,
  FireOutlined,
  RiseOutlined,
  TrophyOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { readStoredUser } from '@/utils/auth';
import SiteHeader from '../components/SiteHeader';
import PageLoader from '../components/PageLoader';

const { Title, Paragraph, Text } = Typography;

interface LeaderboardItem {
  rank: number;
  userId: number;
  nickname: string;
  realName?: string;
  avatar?: string | null;
  bio?: string;
  role?: string;
  ratingAll: number;
  ratingContest: number;
  ratingCertificate: number;
  ratingPractice: number;
}

interface Term {
  termId: number;
  termName: string;
  startTime: string;
  endTime: string | null;
}

const roleMap: Record<string, string> = {
  supper_admin: '超级管理员',
  super_admin: '超级管理员',
  stu_admin: '学生管理员',
  school_admin: '学校管理员',
  department: '部门'
};

const normalizeAvatar = (url?: string | null) => url?.replace('/file/preview/', '/file/download/');

const RankPageClient = () => {
  const router = useRouter();
  const [data, setData] = useState<LeaderboardItem[]>([]);
  const [terms, setTerms] = useState<Term[]>([]);
  const [activeTermId, setActiveTermId] = useState<number | null>(null);
  const [selectedTerm, setSelectedTerm] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  const currentUserId = readStoredUser()?.userId;
  const myRankInfo = useMemo(
    () => data.find(item => Number(item.userId) === Number(currentUserId)),
    [currentUserId, data]
  );

  useEffect(() => {
    const fetchTerms = async () => {
      const res = await axios.get<{ code: number; data: Term[] }>('/api/rating/terms');
      const termList = [...(res.data.data || [])].sort(
        (a, b) => dayjs(b.startTime).valueOf() - dayjs(a.startTime).valueOf()
      );
      setTerms(termList);

      const now = dayjs();
      const current = termList.find(term => {
        const start = dayjs(term.startTime);
        const end = term.endTime ? dayjs(term.endTime) : null;
        return !start.isAfter(now) && (!end || end.isAfter(now));
      });
      const activeId = current?.termId || (termList[0]?.termId) || null;
      setActiveTermId(activeId);
      setSelectedTerm(activeId);
    };

    fetchTerms().catch(() => setTerms([]));
  }, []);

  useEffect(() => {
    const fetchLeaderboard = async () => {
      if (selectedTerm === null) return;
      setLoading(true);
      try {
        const res = await axios.get<{ code: number; data: LeaderboardItem[] }>('/api/rating/leaderboard', {
          params: { termId: selectedTerm }
        });
        setData(res.data.data || []);
      } finally {
        setLoading(false);
      }
    };

    fetchLeaderboard().catch(() => setLoading(false));
  }, [selectedTerm]);

  const renderRole = (role?: string) => {
    if (!role || !roleMap[role]) return null;
    return <Tag color={role.includes('admin') ? 'gold' : 'processing'}>{roleMap[role]}</Tag>;
  };

  const renderRank = (rank: number) => {
    if (rank === 1) return <CrownOutlined className="rank-icon rank-first" />;
    if (rank === 2) return <TrophyOutlined className="rank-icon rank-second" />;
    if (rank === 3) return <TrophyOutlined className="rank-icon rank-third" />;
    return <span className="rank-number">{rank}</span>;
  };

  const columns: ColumnsType<LeaderboardItem> = [
    {
      title: '排名',
      dataIndex: 'rank',
      width: 86,
      align: 'center',
      render: (rank: number) => renderRank(rank)
    },
    {
      title: '用户',
      width: 280,
      render: (_, record) => (
        <button className="rank-user" type="button" onClick={() => router.push(`/profile/${record.userId}`)}>
          <Avatar src={normalizeAvatar(record.avatar)} icon={<UserOutlined />} size={42} />
          <span>
            <strong>
              {record.nickname || '未命名用户'}
              {record.rank <= 3 && <FireOutlined />}
            </strong>
            <em>{record.realName || '未实名'} {renderRole(record.role)}</em>
          </span>
        </button>
      )
    },
    {
      title: <Tooltip title="总分 = 比赛分 + 证书分 + 刷题分">总排位分</Tooltip>,
      dataIndex: 'ratingAll',
      align: 'center',
      sorter: (a, b) => a.ratingAll - b.ratingAll,
      defaultSortOrder: 'descend',
      render: (value: number) => <strong className="score-main">{value}</strong>
    },
    {
      title: '比赛',
      dataIndex: 'ratingContest',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '认证',
      dataIndex: 'ratingCertificate',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '刷题',
      dataIndex: 'ratingPractice',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '简介',
      dataIndex: 'bio',
      ellipsis: true,
      render: (value?: string) => value || <Text type="secondary">-</Text>
    }
  ];

  return (
    <>
      <PageLoader loading={loading} />
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero">
          <Tag className="bits-pill">RATING BOARD</Tag>
          <Title className="content-hero-title">全站积分排行榜</Title>
          <Paragraph>汇聚校内算法竞赛成长记录，见证每一次比赛、认证与刷题积累。</Paragraph>
        </section>

        <div className="rank-toolbar" style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px', padding: '0 10px' }}>
          <Space>
            <FieldTimeOutlined />
            <Select
              value={selectedTerm || undefined}
              onChange={value => setSelectedTerm(value)}
              placeholder="选择学期"
              options={terms.map(term => ({
                value: term.termId,
                label: `${term.termName}${term.termId === activeTermId ? ' · 进行中' : ''}`
              }))}
              style={{ width: 250 }}
            />
          </Space>
        </div>

        {myRankInfo && (
          <Card className="bits-card my-rank-card" variant="borderless" style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px' }}>
            <Row gutter={[24, 18]} align="middle">
              <Col xs={24} md={8}>
                <button className="rank-user rank-user-large" type="button" onClick={() => router.push(`/profile/${myRankInfo.userId}`)}>
                  <Avatar src={normalizeAvatar(myRankInfo.avatar)} icon={<UserOutlined />} size={54} />
                  <span>
                    <strong>我的排名 #{myRankInfo.rank}</strong>
                    <em>{myRankInfo.nickname}</em>
                  </span>
                </button>
              </Col>
              <Col xs={12} md={4}><Statistic title="总积分" value={myRankInfo.ratingAll} prefix={<RiseOutlined />} /></Col>
              <Col xs={12} md={4}><Statistic title="比赛" value={myRankInfo.ratingContest} /></Col>
              <Col xs={12} md={4}><Statistic title="认证" value={myRankInfo.ratingCertificate} /></Col>
              <Col xs={12} md={4}><Statistic title="刷题" value={myRankInfo.ratingPractice} /></Col>
            </Row>
          </Card>
        )}

        <Card className="bits-card rank-table-card" variant="borderless" style={{ width: 'min(1120px, 100%)', margin: '0 auto' }}>
          <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
            <Table
              columns={columns}
              dataSource={data}
              rowKey="userId"
              pagination={{ pageSize: 50, showSizeChanger: false }}
              scroll={{ x: 900 }}
              rowClassName={record => Number(record.userId) === Number(currentUserId) ? 'is-my-rank' : ''}
            />
          </Skeleton>
        </Card>
      </main>
    </>
  );
};

export default RankPageClient;
