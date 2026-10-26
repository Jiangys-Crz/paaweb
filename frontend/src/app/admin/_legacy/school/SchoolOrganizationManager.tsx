"use client";

import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Empty, Form, Input, InputNumber, Modal, Popconfirm, Row, Space, Table, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import axios from 'axios';

interface MajorItem { majorId: number; schoolId: number; majorName: string; schoolingYears?: number; }
interface CollegeItem { schoolId: number; schoolName: string; majors: MajorItem[]; }

const SchoolOrganizationManager: React.FC = () => {
  const [colleges, setColleges] = useState<CollegeItem[]>([]);
  const [selectedCollege, setSelectedCollege] = useState<CollegeItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [collegeModal, setCollegeModal] = useState(false);
  const [majorModal, setMajorModal] = useState(false);
  const [editingCollege, setEditingCollege] = useState<CollegeItem | null>(null);
  const [editingMajor, setEditingMajor] = useState<MajorItem | null>(null);
  const [collegeForm] = Form.useForm();
  const [majorForm] = Form.useForm();

  const fetchOrganization = async (keepSelected = true) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/school-signup/organization');
      if (res.data.code === 200) {
        const next = res.data.data?.colleges || [];
        setColleges(next);
        if (keepSelected && selectedCollege) {
          setSelectedCollege(next.find((item: CollegeItem) => item.schoolId === selectedCollege.schoolId) || next[0] || null);
        } else if (!selectedCollege && next.length) {
          setSelectedCollege(next[0]);
        }
      } else {
        message.error(res.data.msg || '获取组织架构失败');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchOrganization(false); }, []);

  const selectedMajors = useMemo(() => selectedCollege?.majors || [], [selectedCollege]);

  const saveCollege = async () => {
    const values = await collegeForm.validateFields();
    const res = await axios.post('/api/admin/school-signup/organization/college/save', {
      schoolId: editingCollege?.schoolId,
      schoolName: values.schoolName,
    });
    if (res.data.code !== 200) return message.error(res.data.msg || '保存学院失败');
    message.success('学院已保存');
    setCollegeModal(false);
    await fetchOrganization();
  };

  const saveMajor = async () => {
    if (!selectedCollege) return;
    const values = await majorForm.validateFields();
    const res = await axios.post('/api/admin/school-signup/organization/major/save', {
      majorId: editingMajor?.majorId,
      schoolId: selectedCollege.schoolId,
      majorName: values.majorName,
      schoolingYears: values.schoolingYears,
    });
    if (res.data.code !== 200) return message.error(res.data.msg || '保存专业失败');
    message.success('专业已保存');
    setMajorModal(false);
    await fetchOrganization();
  };

  const deleteCollege = async (id: number) => {
    const res = await axios.delete(`/api/admin/school-signup/organization/college/${id}`);
    if (res.data.code !== 200) return message.error(res.data.msg || '删除学院失败');
    message.success('学院已删除');
    setSelectedCollege(null);
    await fetchOrganization(false);
  };

  const deleteMajor = async (id: number) => {
    const res = await axios.delete(`/api/admin/school-signup/organization/major/${id}`);
    if (res.data.code !== 200) return message.error(res.data.msg || '删除专业失败');
    message.success('专业已删除');
    await fetchOrganization();
  };

  const openCollege = (item?: CollegeItem) => {
    setEditingCollege(item || null);
    collegeForm.setFieldsValue({ schoolName: item?.schoolName || '' });
    setCollegeModal(true);
  };

  const openMajor = (item?: MajorItem) => {
    setEditingMajor(item || null);
    majorForm.setFieldsValue({ majorName: item?.majorName || '', schoolingYears: item?.schoolingYears ?? 4 });
    setMajorModal(true);
  };

  return (
    <div>
      <Row gutter={16}>
        <Col xs={24} md={10}>
          <Card title="学院组织" extra={<Space><Button icon={<ReloadOutlined />} onClick={() => fetchOrganization()}>刷新</Button><Button type="primary" icon={<PlusOutlined />} onClick={() => openCollege()}>新增学院</Button></Space>}>
            {colleges.length ? <Table
              rowKey="schoolId"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={colleges}
              rowClassName={record => record.schoolId === selectedCollege?.schoolId ? 'custom-row-selected' : ''}
              onRow={record => ({ onClick: () => setSelectedCollege(record), style: { cursor: 'pointer' } })}
              columns={[
                { title: '学院名称', dataIndex: 'schoolName', ellipsis: true },
                { title: '专业数', width: 76, render: (_: unknown, record: CollegeItem) => record.majors?.length || 0 },
                { title: '操作', width: 92, render: (_: unknown, record: CollegeItem) => <Space onClick={event => event.stopPropagation()}><Button type="text" icon={<EditOutlined />} onClick={() => openCollege(record)} /><Popconfirm title="确定删除该学院？" onConfirm={() => deleteCollege(record.schoolId)}><Button type="text" danger icon={<DeleteOutlined />} /></Popconfirm></Space> },
              ]}
            /> : <Empty description="暂无学院，请先新增" />}
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <Card title={selectedCollege ? `${selectedCollege.schoolName} · 专业` : '专业管理'} extra={<Button type="primary" icon={<PlusOutlined />} disabled={!selectedCollege} onClick={() => openMajor()}>新增专业</Button>}>
            {selectedCollege ? <Table
              rowKey="majorId"
              size="small"
              pagination={false}
              dataSource={selectedMajors}
              columns={[
                { title: '专业名称', dataIndex: 'majorName', ellipsis: true },
                { title: '学制', dataIndex: 'schoolingYears', width: 100, render: (value: number) => value ? `${value} 年` : '-' },
                { title: '操作', width: 110, render: (_: unknown, record: MajorItem) => <Space><Button type="link" icon={<EditOutlined />} onClick={() => openMajor(record)}>编辑</Button><Popconfirm title="确定删除该专业？" onConfirm={() => deleteMajor(record.majorId)}><Button type="link" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm></Space> },
              ]}
            /> : <Empty description="请先选择学院" />}
          </Card>
        </Col>
      </Row>

      <Modal title={editingCollege ? '编辑学院' : '新增学院'} open={collegeModal} onCancel={() => setCollegeModal(false)} onOk={saveCollege} destroyOnHidden>
        <Form form={collegeForm} layout="vertical"><Form.Item name="schoolName" label="学院名称" rules={[{ required: true, message: '请输入学院名称' }]}><Input maxLength={100} showCount /></Form.Item></Form>
      </Modal>
      <Modal title={editingMajor ? '编辑专业' : '新增专业'} open={majorModal} onCancel={() => setMajorModal(false)} onOk={saveMajor} destroyOnHidden>
        <Form form={majorForm} layout="vertical"><Form.Item name="majorName" label="专业名称" rules={[{ required: true, message: '请输入专业名称' }]}><Input maxLength={100} showCount /></Form.Item><Form.Item name="schoolingYears" label="学制（年）" rules={[{ required: true, message: '请输入学制' }]}><InputNumber min={0} max={10} style={{ width: '100%' }} /></Form.Item></Form>
      </Modal>
      <style>{`.custom-row-selected td { background-color: #e6f7ff !important; font-weight: 600; color: #1890ff; }`}</style>
    </div>
  );
};

export default SchoolOrganizationManager;
