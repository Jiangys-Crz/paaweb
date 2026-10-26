"use client";

/* eslint-disable @typescript-eslint/no-explicit-any, react-hooks/set-state-in-effect */

import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Input, Table, Tag } from 'antd';
import { ReloadOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useNavigate } from '@/app/admin/_legacy/router';
import { contestTypeText } from '@/utils/contestDisplay';

const SchoolSignupEventList: React.FC = () => {
  const navigate = useNavigate();
  const [events, setEvents] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/event/list');
      if (res.data.code === 200) setEvents(res.data.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  const getStatus = (item: any) => {
    const now = dayjs();
    if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange' };
    if (now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green' };
    if (now.isBefore(item.eventStart)) return { text: '等待开赛', color: 'blue' };
    if (now.isBefore(item.eventEnd)) return { text: '正在比赛中', color: 'red' };
    return { text: '已结束', color: 'default' };
  };

  const filtered = useMemo(() => {
    const key = keyword.trim().toLowerCase();
    if (!key) return events;
    return events.filter(item => String(item.name || '').toLowerCase().includes(key));
  }, [events, keyword]);

  const columns = [
    { title: '赛事名称', dataIndex: 'name', ellipsis: true },
    { title: '赛制', dataIndex: 'type', width: 190, render: (type: string, record: any) => <Tag color={record.isTeam === 1 ? 'purple' : 'blue'}>{contestTypeText(type, record.isTeam === 1 ? '团队赛' : '个人赛')}</Tag> },
    { title: '报名时间', width: 260, render: (_: any, record: any) => `${dayjs(record.signupStart).format('YYYY-MM-DD HH:mm')} 至 ${dayjs(record.signupEnd).format('YYYY-MM-DD HH:mm')}` },
    { title: '状态', width: 120, render: (_: any, record: any) => { const status = getStatus(record); return <Tag color={status.color}>{status.text}</Tag>; } },
    { title: '报名名额', width: 120, render: (_: any, record: any) => `${record.signupCount || 0} / ${record.maxSignup}` },
    {
      title: '操作',
      width: 130,
      fixed: 'right' as const,
      render: (_: any, record: any) => (
        <Button type="primary" icon={<TeamOutlined />} onClick={() => navigate(`/admin/school/signups/${record.eventId}`)}>
          报名管理
        </Button>
      )
    }
  ];

  return (
    <Card
      title="报名管理"
      extra={<Button icon={<ReloadOutlined />} onClick={fetchEvents}>刷新</Button>}
    >
      <Input
        placeholder="搜索赛事名称"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={event => setKeyword(event.target.value)}
        style={{ width: 320, marginBottom: 16 }}
        allowClear
      />
      <Table
        rowKey="eventId"
        columns={columns}
        dataSource={filtered}
        loading={loading}
        scroll={{ x: 1000 }}
      />
    </Card>
  );
};

export default SchoolSignupEventList;
