"use client";

import { Card, Col, Empty, List, Progress, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { AudioOutlined, BookOutlined, CalendarOutlined, ClockCircleOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import ActivityLayout from '../contest/ContestNav';

const { Title, Text } = Typography;

interface TrainingItem {
  trainId: number;
  name: string;
  signupStart: string;
  signupEnd: string;
  trainStart: string;
  trainEnd: string;
  termName: string;
  teacherName: string;
  publisherName: string;
  signupCount: number;
  maxCount: number;
}

const getTrainingStatus = (item: TrainingItem) => {
  const now = dayjs();
  if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', order: 2 };
  if (now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', order: 1 };
  if (now.isBefore(item.trainStart)) return { text: '报名已结束', color: 'blue', order: 3 };
  if (now.isBefore(item.trainEnd)) return { text: '培训进行中', color: 'red', order: 4 };
  return { text: '已结束', color: 'default', order: 5 };
};

export default function ActivityTrainingPage() {
  const router = useRouter();
  const [list, setList] = useState<TrainingItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get('/api/training/list').then(res => {
      const data = [...(res.data.data || [])].sort((a, b) => getTrainingStatus(a).order - getTrainingStatus(b).order);
      setList(data);
    }).finally(() => setLoading(false));
  }, []);

  return (
    <ActivityLayout>
      <section className="activity-content">
        <div className="activity-toolbar">
          <Title level={3}>培训</Title>
        </div>
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {list.length ? (
            <List
              dataSource={list}
              renderItem={item => {
                const status = getTrainingStatus(item);
                const percent = item.maxCount > 0 ? Math.min(100, Math.round((item.signupCount / item.maxCount) * 100)) : 0;
                return (
                  <List.Item style={{ width: '100%', padding: '12px 0' }}>
                    <div style={{ width: '100%' }}>
                      <Card
                        className="bits-card training-card uniform-activity-card"
                        variant="borderless"
                        hoverable
                        onClick={() => router.push(`/training/${item.trainId}`)}
                      >
                        <Row gutter={[18, 14]} align="middle" style={{ width: '100%' }}>
                          <Col xs={24} md={18}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
                              <Text strong className="activity-list-title" style={{ margin: 0, fontSize: 18, color: '#17212b' }}>
                                {item.name}
                              </Text>
                              <Tag color={status.color} style={{ margin: 0 }}>{status.text}</Tag>
                            </div>
                            <Space wrap className="activity-meta-row" style={{ marginBottom: 8, gap: '8px 16px' }}>
                              <span><CalendarOutlined /> 学期：{item.termName}</span>
                              <span><UserOutlined /> 讲师：{item.teacherName}</span>
                              <span><AudioOutlined /> 发布：{item.publisherName}</span>
                            </Space>
                            <Space className="activity-time-row" direction="vertical" style={{ display: 'flex', marginTop: 0 }}>
                              <span><ClockCircleOutlined /> 报名：{dayjs(item.signupStart).format('YYYY-MM-DD HH:mm')} ~ {dayjs(item.signupEnd).format('YYYY-MM-DD HH:mm')}</span>
                              <span><ClockCircleOutlined /> 课程：{dayjs(item.trainStart).format('YYYY-MM-DD HH:mm')} ~ {dayjs(item.trainEnd).format('YYYY-MM-DD HH:mm')}</span>
                            </Space>
                          </Col>
                          <Col xs={24} md={6}>
                            <div className="signup-progress">
                              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                <span>报名人数</span>
                                <span style={{ fontWeight: 700, color: '#262626' }}>{item.signupCount}/{item.maxCount}</span>
                              </div>
                              <Progress percent={percent} showInfo={false} />
                            </div>
                          </Col>
                        </Row>
                      </Card>
                    </div>
                  </List.Item>
                );
              }}
            />
          ) : <Empty description="暂无培训安排" />}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
