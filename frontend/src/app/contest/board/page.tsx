"use client";

import { Alert, Button, Card, Col, Empty, Input, List, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { CalendarOutlined, EyeOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import type { ExternalBoardSummary } from '@/types/leaderboard';
import ActivityLayout from '../ContestNav';

const { Title, Text } = Typography;

const getStatusTag = (event: ExternalBoardSummary) => {
  const now = dayjs();
  const start = event.actualStartTime ? dayjs(event.actualStartTime) : dayjs(event.eventStart);
  const end = event.actualEndTime ? dayjs(event.actualEndTime) : dayjs(event.eventEnd);
  if (now.isBefore(start)) return <Tag color="gold">未开始</Tag>;
  if (now.isAfter(end)) return <Tag color="green">已结束</Tag>;
  if (event.unfrozen === false && now.isAfter(event.freezeTime ? dayjs(event.freezeTime) : end.subtract(1, 'hour'))) return <Tag color="red">封榜中</Tag>;
  return <Tag color="blue">进行中</Tag>;
};

export default function BoardListPage() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('');
  const [list, setList] = useState<ExternalBoardSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    axios.get('/api/leaderboard/list').then(res => {
      if (res.data.code !== 200) throw new Error();
      setList(res.data.data || []);
    }).catch(() => setError('榜单加载失败，请刷新页面重试。')).finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => list.filter(item => `${item.name} ${item.eventName}`.toLowerCase().includes(keyword.toLowerCase())), [keyword, list]);

  return (
    <ActivityLayout>
      <section className="activity-content">
        <div className="activity-toolbar">
          <Title level={3}>外榜</Title>
          <Input className="bits-input" prefix={<SearchOutlined />} allowClear placeholder="搜索榜单" value={keyword} onChange={event => setKeyword(event.target.value)} />
        </div>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {filtered.length ? (
            <List rowKey="boardId" dataSource={filtered} renderItem={item => (
              <List.Item style={{ width: '100%', padding: '12px 0' }}>
                <div style={{ width: '100%' }}>
                  <Card className="bits-card board-list-card uniform-activity-card" variant="borderless" hoverable onClick={() => router.push(`/contest/board/${item.boardId}`)}>
                    <Row gutter={[18, 14]} align="middle" style={{ width: '100%' }}>
                      <Col xs={24} md={18}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
                          <Text strong className="activity-list-title" style={{ margin: 0, fontSize: 18, color: '#17212b' }}>
                            {item.name}
                          </Text>
                          <Tag color="orange" style={{ margin: 0 }}>外榜</Tag>
                          {getStatusTag(item)}
                          {item.type && <Tag color="geekblue" style={{ margin: 0 }}>{item.type}</Tag>}
                        </div>
                        <p style={{ marginBottom: 10, color: '#62798c' }}>所属比赛：{item.eventName}</p>
                        <Space wrap className="activity-meta-row" style={{ marginBottom: 0, gap: '8px 16px' }}>
                          <span><CalendarOutlined /> 比赛：{dayjs(item.actualStartTime || item.eventStart).format('YYYY-MM-DD HH:mm')} ~ {dayjs(item.actualEndTime || item.eventEnd).format('YYYY-MM-DD HH:mm')}</span>
                          <span><UserOutlined /> 参赛数：{item.signupCount || 0} {item.isTeam ? '队' : '人'}</span>
                          <Tag style={{ margin: 0 }}>{item.isTeam ? '团队赛' : '个人赛'}</Tag>
                        </Space>
                      </Col>
                      <Col xs={24} md={6} style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <Button type="primary" className="bits-primary-button" icon={<EyeOutlined />} style={{ minWidth: 120 }}>
                          查看榜单
                        </Button>
                      </Col>
                    </Row>
                  </Card>
                </div>
              </List.Item>
            )} />
          ) : <Empty description="暂无开放榜单" />}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
