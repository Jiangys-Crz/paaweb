"use client";

import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Popconfirm, Space, Table, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { createBoard, deleteBoard, listEventBoards } from '@/services/leaderboard.service';
import type { ExternalBoardSummary } from '@/types/leaderboard';
import ExternalBoardConfigModal from './ExternalBoardConfigModal';

interface Props {
  open: boolean;
  eventId: number;
  eventName: string;
  eventType?: string;
  onCancel: () => void;
}

export default function ExternalBoardManagerModal({ open, eventId, eventName, eventType, onCancel }: Props) {
  const [boards, setBoards] = useState<ExternalBoardSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [form] = Form.useForm<{ name: string }>();

  const refresh = useCallback(async () => {
    try {
      const res = await listEventBoards(eventId);
      if (res.data.code !== 200) throw new Error();
      setBoards(res.data.data || []);
      setError('');
    } catch {
      setError('外榜列表加载失败，请刷新重试。');
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    listEventBoards(eventId).then(res => {
      if (!active) return;
      if (res.data.code !== 200) throw new Error();
      setBoards(res.data.data || []);
      setError('');
    }).catch(() => {
      if (active) setError('外榜列表加载失败，请刷新重试。');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, eventId]);

  const add = async () => {
    if (saving) return;
    try {
      const { name } = await form.validateFields();
      setSaving(true);
      const res = await createBoard(eventId, name.trim());
      if (res.data.code !== 200) throw new Error(res.data.msg || '创建失败');
      setCreating(false);
      form.resetFields();
      await refresh();
      setEditingId(res.data.data.boardId);
      message.success('外榜已创建，可继续配置');
    } catch (error) {
      if (!(error && typeof error === 'object' && 'errorFields' in error)) message.error('创建外榜失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (boardId: number) => {
    try {
      const res = await deleteBoard(boardId);
      if (res.data.code !== 200) throw new Error();
      message.success('外榜已删除');
      await refresh();
    } catch {
      message.error('删除外榜失败，请重试');
    }
  };

  return (
    <>
      <Modal title={`${eventName} · 外榜管理`} open={open} onCancel={onCancel} footer={<Button onClick={onCancel}>关闭</Button>} width={860} destroyOnHidden>
        <Alert type="info" showIcon title="同一个比赛可添加多个外榜，分别设置名称、开放状态、时间和题目。推送时请使用对应的外榜 ID。" style={{ marginBottom: 16 }} />
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>添加外榜</Button>
          <Button icon={<ReloadOutlined />} onClick={() => { setLoading(true); void refresh(); }}>刷新</Button>
        </Space>
        <Table<ExternalBoardSummary>
          rowKey="boardId" dataSource={boards} loading={loading} pagination={false} scroll={{ x: 680 }}
          columns={[
            { title: '外榜 ID', dataIndex: 'boardId', width: 100 },
            { title: '外榜名称', dataIndex: 'name' },
            { title: '状态', dataIndex: 'externalOpen', width: 100, render: value => <Tag color={value ? 'green' : 'default'}>{value ? '已开放' : '未开放'}</Tag> },
            { title: '操作', width: 230, render: (_, board) => <Space>
              <Button icon={<EditOutlined />} size="small" onClick={() => setEditingId(board.boardId)}>配置 / 改名</Button>
              <Popconfirm title={`删除外榜“${board.name}”？`} description="将同时删除该外榜的题目和提交记录，无法恢复。比赛和其他外榜不受影响。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => remove(board.boardId)}>
                <Button danger size="small" icon={<DeleteOutlined />}>删除</Button>
              </Popconfirm>
            </Space> },
          ]}
          locale={{ emptyText: '暂无外榜，点击“添加外榜”创建' }}
        />
      </Modal>
      <Modal title="添加外榜" open={creating} onCancel={() => { setCreating(false); form.resetFields(); }} onOk={add} confirmLoading={saving} okText="创建" cancelText="取消">
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="外榜名称" rules={[{ required: true, whitespace: true, message: '请输入外榜名称' }, { max: 200 }]}>
            <Input maxLength={200} placeholder="例如：正式榜、热身赛榜" onPressEnter={add} />
          </Form.Item>
        </Form>
      </Modal>
      {editingId !== null && <ExternalBoardConfigModal open boardId={editingId} eventId={eventId} eventType={eventType} onCancel={() => { setEditingId(null); void refresh(); }} />}
    </>
  );
}
