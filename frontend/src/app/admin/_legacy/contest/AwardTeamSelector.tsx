'use client';
import React, { useMemo, useState } from 'react';
import { Alert, Button, Input, Modal, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { mergeVisibleAwardSelection } from './awardTeamSelection';

export type AwardParticipant = { itemId: number; name: string; organization?: string | null; rank?: number | null; isStar?: boolean; isGirl?: boolean; solved?: number | null; penalty?: number | null };
export type AwardParticipants = { rankUnavailable: boolean; participants: AwardParticipant[] };

export default function AwardTeamSelector({ value = [], onChange, data, loading }: { value?: number[]; onChange?: (value: number[]) => void; data: AwardParticipants; loading: boolean }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<number[]>([]);
  const [search, setSearch] = useState('');
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return query ? data.participants.filter(item => `${item.name} ${item.organization || ''}`.toLocaleLowerCase().includes(query)) : data.participants;
  }, [data.participants, search]);
  const columns: ColumnsType<AwardParticipant> = [
    { title: '最终名次', width: 88, render: (_, row) => data.rankUnavailable || row.rank == null ? '—' : row.isStar || row.rank < 0 ? '⭐' : row.rank },
    { title: '队名 / 姓名', dataIndex: 'name', width: 200 },
    { title: '组织', dataIndex: 'organization', width: 185, render: value => value || '—' },
    { title: '类型', width: 115, render: (_, row) => <Space size={2}>{row.isStar && <Tag color="gold">⭐ 打星</Tag>}{row.isGirl && <Tag color="magenta">👧 女队</Tag>}{!row.isStar && !row.isGirl && '—'}</Space> },
    { title: '解题数', dataIndex: 'solved', width: 70, render: value => value ?? '—' },
    { title: '罚时', dataIndex: 'penalty', width: 75, render: value => value ?? '—' },
  ];
  return <>
    <Space wrap><Button onClick={() => { setDraft([...value]); setSearch(''); setOpen(true); }}>选择获奖队伍</Button><span>已选 {value.length} 队 / 人</span></Space>
    <Modal title="选择获奖队伍 / 参赛者" open={open} onCancel={() => setOpen(false)} onOk={() => { onChange?.(draft); setOpen(false); }} okText="确认选择" cancelText="取消" width={1000} destroyOnHidden>
      <Alert type={data.rankUnavailable ? 'warning' : 'info'} showIcon title={data.rankUnavailable ? '比赛尚未结束：当前为报名名单，尚非最终排名，可提前选择。' : '按当前外榜最终排名排序；可搜索并跨页选择。'} style={{ marginBottom: 12 }} />
      <Space style={{ marginBottom: 12 }} wrap><Input.Search aria-label="搜索队伍或组织" placeholder="搜索队名、姓名或组织" value={search} onChange={event => setSearch(event.target.value)} allowClear style={{ width: 300 }} /><span>已选择 {draft.length} 队 / 人</span></Space>
      <Table<AwardParticipant> rowKey="itemId" columns={columns} dataSource={filtered} loading={loading} size="small" scroll={{ x: 760, y: 420 }} pagination={{ pageSize: 10, showSizeChanger: false, showTotal: total => `共 ${total} 条` }}
        rowSelection={{ selectedRowKeys: draft, preserveSelectedRowKeys: true, onChange: keys => setDraft(previous => mergeVisibleAwardSelection(previous, filtered.map(item => item.itemId), keys.map(Number))) }} />
    </Modal>
  </>;
}
