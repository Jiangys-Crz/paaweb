"use client";

import React, { useEffect, useState } from 'react';
import { Button, InputNumber, message, Space, Spin, Table, Tag, Typography } from 'antd';
import { SaveOutlined, TrophyOutlined } from '@ant-design/icons';
import axios from 'axios';

const { Title, Paragraph } = Typography;

interface LevelRow {
  levelId: number;
  name: string;
  levelType: string;
  rankType: string;
  baseRating: number;
  sortWeight: number;
}

const getLevelColor = (levelType?: string) => {
  if (!levelType) return 'default';
  if (levelType.includes("国")) return 'red';
  if (levelType.includes("省")) return 'volcano';
  if (levelType.includes("校") || levelType.includes("市")) return 'blue';
  if (levelType.includes("院")) return 'green';
  return 'default';
};

const LevelWeightsClient = () => {
  const [levels, setLevels] = useState<LevelRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const fetchLevels = async () => {
      setLoading(true);
      try {
        const res = await axios.get<{ code: number; data: LevelRow[] }>('/api/certificate/levels');
        if (res.data.code === 200) {
          setLevels(res.data.data || []);
        }
      } catch {
        message.error('加载等级数据失败');
      } finally {
        setLoading(false);
      }
    };
    fetchLevels();
  }, []);

  const handleWeightChange = (levelId: number, value: number | null) => {
    setLevels(prev =>
      prev.map(l => l.levelId === levelId ? { ...l, sortWeight: value ?? 0 } : l)
    );
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await axios.post('/api/certificate/admin/level/update-weights', levels.map(l => ({
        levelId: l.levelId,
        sortWeight: l.sortWeight
      })));
      message.success('荣誉排序权重已保存！');
    } catch {
      message.error('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: '等级名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, row: LevelRow) => (
        <span style={{ fontWeight: 600 }}>
          <Tag color={getLevelColor(row.levelType)} style={{ marginRight: 8 }}>{row.levelType}</Tag>
          {text}
        </span>
      )
    },
    {
      title: '奖项类别',
      dataIndex: 'rankType',
      key: 'rankType',
    },
    {
      title: '基础分值',
      dataIndex: 'baseRating',
      key: 'baseRating',
      render: (v: number) => <Tag color="purple">{v}</Tag>
    },
    {
      title: '排序权重',
      dataIndex: 'sortWeight',
      key: 'sortWeight',
      render: (value: number, row: LevelRow) => (
        <InputNumber
          min={0}
          max={99999}
          value={value}
          onChange={(v) => handleWeightChange(row.levelId, v)}
          style={{ width: 110 }}
        />
      )
    }
  ];

  return (
    <div style={{ padding: '24px 0' }}>
      <Space align="start" style={{ marginBottom: 24 }}>
        <TrophyOutlined style={{ fontSize: 28, color: '#faad14', marginTop: 4 }} />
        <div>
          <Title level={3} style={{ margin: 0 }}>荣誉墙排序权重</Title>
          <Paragraph style={{ margin: 0, color: '#8c8c8c' }}>
            配置荣誉等级在荣誉墙页面的展示排序优先级。数字越大，排在越前面。
          </Paragraph>
        </div>
      </Space>

      <Spin spinning={loading}>
        <Table
          dataSource={levels}
          columns={columns}
          rowKey="levelId"
          pagination={false}
          style={{ marginBottom: 24 }}
          bordered
        />
      </Spin>

      <Button
        type="primary"
        icon={<SaveOutlined />}
        size="large"
        loading={saving}
        onClick={handleSave}
        style={{ minWidth: 140 }}
      >
        保存权重设置
      </Button>
    </div>
  );
};

export default LevelWeightsClient;
