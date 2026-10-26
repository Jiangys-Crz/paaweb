'use client';

import { useRef, useState } from 'react';
import { Alert, Button, DatePicker, Form, Input, Modal, Table, message } from 'antd';
import axios from 'axios';
import dayjs, { Dayjs } from 'dayjs';

interface BanUser {
  userId: number;
  numberId: string;
  realName: string;
  universityName?: string;
  email?: string;
  accountStatus: string;
}

export default function AccountBanModal({ onBanned }: { onBanned: () => void }) {
  const [open, setOpen] = useState(false);
  const [numberId, setNumberId] = useState('');
  const [users, setUsers] = useState<BanUser[]>([]);
  const [selected, setSelected] = useState<BanUser>();
  const [searching, setSearching] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [searched, setSearched] = useState(false);
  const [form] = Form.useForm<{ banUntil: Dayjs; reason: string }>();
  const searchVersion = useRef(0);

  const resetSearch = () => {
    searchVersion.current += 1;
    setUsers([]);
    setSelected(undefined);
    setSearched(false);
    setSearching(false);
  };
  const search = async () => {
    if (!numberId.trim()) { message.warning('请输入学号'); return; }
    const version = ++searchVersion.current;
    setSearching(true);
    setSelected(undefined);
    setUsers([]);
    try {
      const { data } = await axios.get('/api/admin/user/ban/search', { params: { numberId: numberId.trim() } });
      if (version !== searchVersion.current) return;
      if (data.code !== 200) { message.error(data.msg || '搜索失败'); return; }
      setUsers(data.data || []);
      setSearched(true);
    } catch {
      if (version === searchVersion.current) message.error('搜索失败，请重试');
    } finally {
      if (version === searchVersion.current) setSearching(false);
    }
  };
  const submit = async () => {
    if (!selected) { message.warning('请先搜索并选择用户'); return; }
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    setSubmitting(true);
    try {
      const { data } = await axios.post('/api/admin/user/ban', {
        userId: selected.userId,
        banUntil: values.banUntil.format('YYYY-MM-DDTHH:mm:ss'),
        reason: values.reason.trim(),
      });
      if (data.code !== 200) { message.error(data.msg || '封禁失败'); return; }
      if (data.data.emailSent) message.success('账号已封禁，通知邮件已发送');
      else message.warning('账号已封禁，但通知邮件发送失败，请检查邮件配置并联系用户', 8);
      setOpen(false);
      onBanned();
    } catch { message.error('请求失败，请刷新用户列表确认账号状态'); }
    finally { setSubmitting(false); }
  };

  return <>
    <Button danger onClick={() => {
      resetSearch(); setNumberId(''); form.resetFields(); setOpen(true);
    }}>账号封禁</Button>
    <Modal title="账号封禁" open={open} width={720} okText="确认封禁"
      okButtonProps={{ danger: true, disabled: !selected }} confirmLoading={submitting}
      cancelButtonProps={{ disabled: submitting }} closable={!submitting} maskClosable={false}
      onOk={submit} onCancel={() => { resetSearch(); setOpen(false); }}>
      <Input.Search placeholder="输入完整学号搜索用户" value={numberId} enterButton="搜索"
        loading={searching} disabled={submitting} onSearch={search}
        onChange={event => { setNumberId(event.target.value); resetSearch(); }} />
      <Table<BanUser> style={{ marginTop: 16 }} size="small" rowKey="userId" dataSource={users}
        loading={searching} pagination={false} locale={{ emptyText: searched ? '未找到该学号的用户' : '请先输入学号搜索' }}
        rowSelection={{ type: 'radio', selectedRowKeys: selected ? [selected.userId] : [],
          onChange: (_, rows) => setSelected(rows[0]),
          getCheckboxProps: user => ({ disabled: submitting || user.accountStatus === 'banned' || !user.email }),
        }} columns={[
          { title: '姓名', dataIndex: 'realName' }, { title: '学号', dataIndex: 'numberId' },
          { title: '学校', dataIndex: 'universityName' }, { title: '通知邮箱', dataIndex: 'email' },
          { title: '状态', render: (_, user) => user.accountStatus === 'banned' ? '已封禁' : !user.email ? '未绑定邮箱' : '可选择' },
        ]} />
      {selected && <Alert style={{ marginTop: 16 }} type="warning" showIcon
        message={`将封禁 ${selected.realName}（${selected.numberId}），并发送邮件至 ${selected.email}`} />}
      <Form form={form} layout="vertical" disabled={submitting} style={{ marginTop: 16 }}>
        <Form.Item name="banUntil" label="解封日期和时间（北京时间）" rules={[
          { required: true, message: '请选择解封时间' },
          { validator: (_, value) => !value || value.isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('解封时间必须晚于当前时间')) },
        ]}>
          <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }}
            disabledDate={date => date.isBefore(dayjs().startOf('day'))} />
        </Form.Item>
        <Form.Item name="reason" label="封禁原因" rules={[{ required: true, whitespace: true, message: '请输入封禁原因' }]}>
          <Input.TextArea rows={4} maxLength={1000} showCount placeholder="请输入封禁原因，此内容将发送给用户" />
        </Form.Item>
      </Form>
      <Alert type="info" showIcon message="到期后系统将在30秒内自动解除本次封禁，恢复封禁前的账号状态。" />
    </Modal>
  </>;
}
