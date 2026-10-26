"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import axios from 'axios';

interface StudentOption {
  userId: number;
  realName: string;
  numberId: string;
  accountStatus?: string;
}

interface TeamItem {
  teamId: number;
  teamName: string;
  introduce?: string;
  leaderId: number;
  leaderName?: string;
  membersContent?: string;
  members?: StudentOption[];
}

const SchoolTeamManager: React.FC = () => {
  const [teams, setTeams] = useState<TeamItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTeam, setEditingTeam] = useState<TeamItem | null>(null);
  const [studentOptions, setStudentOptions] = useState<StudentOption[]>([]);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchTeams = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/school-signup/teams');
      if (res.data.code === 200) setTeams(res.data.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeams();
  }, []);

  const searchStudents = async (keyword: string) => {
    if (!keyword) return;
    const res = await axios.get('/api/admin/school-signup/students/search', { params: { keyword } });
    if (res.data.code === 200) {
      const next = res.data.data || [];
      setStudentOptions(prev => {
        const map = new Map<number, StudentOption>();
        [...prev, ...next].forEach(item => map.set(item.userId, item));
        return Array.from(map.values());
      });
    }
  };

  const openCreate = () => {
    setEditingTeam(null);
    setStudentOptions([]);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (team: TeamItem) => {
    setEditingTeam(team);
    const members = team.members || [];
    setStudentOptions(members);
    form.setFieldsValue({
      teamName: team.teamName,
      introduce: team.introduce,
      leaderId: team.leaderId,
      memberIds: members.filter(member => member.userId !== team.leaderId).map(member => member.userId)
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const res = await axios.post('/api/admin/school-signup/teams/save', {
        teamId: editingTeam?.teamId,
        ...values
      });
      if (res.data.code === 200) {
        message.success(res.data.data || '保存成功');
        setModalOpen(false);
        fetchTeams();
      } else {
        message.error(res.data.msg || '保存失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (teamId: number) => {
    const res = await axios.post('/api/admin/school-signup/teams/delete', { teamId });
    if (res.data.code === 200) {
      message.success('已删除');
      fetchTeams();
    } else {
      message.error(res.data.msg || '删除失败');
    }
  };

  const selectOptions = useMemo(() => studentOptions.map(student => ({
    value: student.userId,
    disabled: student.accountStatus === 'banned',
    label: `${student.realName || '-'} (${student.numberId || '无学号'})`
  })), [studentOptions]);

  const columns: ColumnsType<TeamItem> = [
    { title: '队伍名称', dataIndex: 'teamName', width: 180 },
    { title: '队长', dataIndex: 'leaderName', width: 120, render: text => <Tag color="gold">{text || '-'}</Tag> },
    { title: '队伍成员（含队长）', dataIndex: 'membersContent', render: text => text || '-' },
    { title: '队伍宣言', dataIndex: 'introduce', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除该队伍？" onConfirm={() => handleDelete(record.teamId)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <Card
      title="队伍管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchTeams}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建队伍</Button>
        </Space>
      }
    >
      <Table
        rowKey="teamId"
        columns={columns}
        dataSource={teams}
        loading={loading}
        scroll={{ x: 900 }}
      />

      <Modal
        title={editingTeam ? '编辑队伍' : '创建队伍'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="teamName" label="队伍名称" rules={[{ required: true, message: '请输入队伍名称' }]}>
            <Input maxLength={15} showCount />
          </Form.Item>
          <Form.Item name="leaderId" label="队长" rules={[{ required: true, message: '请选择队长' }]}>
            <Select
              showSearch
              filterOption={false}
              onSearch={searchStudents}
              suffixIcon={<SearchOutlined />}
              placeholder="输入姓名或学号搜索本校学生"
              options={selectOptions}
            />
          </Form.Item>
          <Form.Item name="memberIds" label="队伍成员">
            <Select
              mode="multiple"
              showSearch
              filterOption={false}
              onSearch={searchStudents}
              suffixIcon={<SearchOutlined />}
              placeholder="输入姓名或学号添加成员，队长会自动包含在内"
              options={selectOptions}
            />
          </Form.Item>
          <Form.Item name="introduce" label="队伍宣言">
            <Input.TextArea rows={3} maxLength={100} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default SchoolTeamManager;
