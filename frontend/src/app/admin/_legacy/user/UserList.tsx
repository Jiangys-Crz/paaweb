"use client";

import React, { useState, useEffect, useRef } from 'react';
import {
  Alert, Card, Table, Button, Image, Input, Select, Space, Tag, Modal, Form, Upload,
  message, Breadcrumb, Row, Col, Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined, EditOutlined, ReloadOutlined, UploadOutlined, DownloadOutlined, IdcardOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import axios from 'axios';
import dayjs from 'dayjs';
import AccountBanModal from './AccountBanModal';
import ImportPreview, { ImportPreviewData } from '../components/ImportPreview';
import { readExcelRows } from '@/utils/excelImport';
import useAdminPermissions from '@/hooks/useAdminPermissions';

const USER_IMPORT_HEADERS = ['学校', '学院', '专业', '姓名', '学号', '初始密码'];

interface UserItem {
    userId: number;
    userName: string;
    realName: string;
    numberId: string;
    role: string;
    accountStatus: string;
    universityId: number;
    universityName: string;
    schoolId: number;
    schoolName: string;
    majorId: number;
    majorName: string;
    gradeYear: string;
    isEnrolledStu: number;
    registerTime: string;
    eduBg: string;
    className?: string;
    email?: string;
    phone?: string;
    gender?: number;
    idPhotoFileId?: number;
    idPhotoUrl?: string;
}

const { Option } = Select;
const ROLE_MAP: Record<string, { label: string; color: string }> = {
  student: { label: '学生', color: 'blue' },
  stu_admin: { label: '学生管理员', color: 'cyan' },
  school_admin: { label: '学校管理员', color: 'purple' },
  supper_admin: { label: '超级管理员', color: 'red' },
};

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  pending: { label: '待审核', color: 'gold' },
  normal: { label: '正常', color: 'success' },
  register: { label: '注册', color: 'warning' },
  restricted: { label: '拒绝', color: 'warning' },
  banned: { label: '封禁', color: 'error' },
  pending_activation: { label: '待激活', color: 'default' },
};

interface UserListProps {
  scope?: 'admin' | 'school';
}

const UserList: React.FC<UserListProps> = ({ scope = 'admin' }) => {
  const isSchoolScope = scope === 'school';
  const hasPermission = useAdminPermissions();
  const canChangeEmail = !isSchoolScope && hasPermission('manage_user') && hasPermission('manage_user_email');
  const canChangeRole = !isSchoolScope && hasPermission('manage_user') && hasPermission('manage_user_role');
  const canChangeStatus = !isSchoolScope && hasPermission('manage_user') && hasPermission('manage_user_status');
  const apiBase = isSchoolScope ? '/api/admin/school-signup/students' : '/api/admin/user';
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<UserItem[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [keywordInput, setKeywordInput] = useState('');
  const [filters, setFilters] = useState<{
    keyword: string;
    universityId: number | null;
    schoolId: number | null;
    majorId: number | null;
    gender: number | null;
    gradeYear: string | null;
    isEnrolledStu: number | null;
  }>({
    keyword: '',
    universityId: null,
    schoolId: null,
    majorId: null,
    gender: null,
    gradeYear: null,
    isEnrolledStu: null
  });
  const [universities, setUniversities] = useState<{ universityId: number; universityName: string }[]>([]);
  const [schools, setSchools] = useState<{ schoolId: number; schoolName: string }[]>([]);
  const [majors, setMajors] = useState<{ majorId: number; majorName: string }[]>([]);
  const [schoolScopeUniversity, setSchoolScopeUniversity] = useState<{ universityId: number; universityName: string } | null>(null);

  // 编辑相关状态
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserItem | null>(null);
  const [editingEduBg, setEditingEduBg] = useState<string>('');
  const [form] = Form.useForm();
  const [editSchools, setEditSchools] = useState<{ schoolId: number; schoolName: string }[]>([]);
  const [editMajors, setEditMajors] = useState<{ majorId: number; majorName: string }[]>([]);
  const [editIdPhotoFileId, setEditIdPhotoFileId] = useState<number | undefined>();
  const [editIdPhotoUrl, setEditIdPhotoUrl] = useState<string | undefined>();
  const [idPhotoUploading, setIdPhotoUploading] = useState(false);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [importPreview, setImportPreview] = useState<ImportPreviewData | null>(null);
  const [importUsers, setImportUsers] = useState<Record<string, string>[]>([]);

  useEffect(() => {
    if (isSchoolScope) fetchSchoolOrganization();
    else fetchUniversities();
    fetchData();
  }, []);

  const fetchSchoolOrganization = async () => {
    try {
      const [organizationRes, profileRes] = await Promise.all([
        axios.get('/api/admin/school-signup/organization'),
        axios.get('/api/admin/school-signup/school-profile'),
      ]);
      if (organizationRes.data.code !== 200) return;
      const university = {
        universityId: Number(organizationRes.data.data?.universityId),
        universityName: profileRes.data.code === 200 ? profileRes.data.data?.universityName : '本校',
      };
      const nextSchools = (organizationRes.data.data?.colleges || []).map((item: any) => ({ schoolId: item.schoolId, schoolName: item.schoolName }));
      setSchoolScopeUniversity(university);
      setUniversities([university]);
      setSchools(nextSchools);
    } catch {
      message.error('获取本校组织架构失败');
    }
  };

  const handleImport = async () => {
    if (!importFile) {
      message.warning('请先选择导入文件');
      return;
    }
    setImportLoading(true);
    try {
      const confirming = Boolean(importPreview);
      const userList = importUsers.length ? importUsers : (await readExcelRows(importFile, USER_IMPORT_HEADERS))
        .map((r) => ({
          universityName: r[0], schoolName: r[1], majorName: r[2],
          realName: r[3], numberId: r[4], password: r[5],
        }));

      if (userList.length === 0) {
        message.error('Excel 中没有待导入数据');
        return;
      }

      if (!importUsers.length) setImportUsers(userList);
      const res = await axios.post(`${apiBase}/import${confirming ? '' : '/preview'}`, userList);
      if (res.data.code === 200) {
        if (!confirming) {
          setImportPreview(res.data.data);
          return;
        }
        message.success(res.data.data);
        setIsImportModalOpen(false);
        setImportFile(null);
        setImportUsers([]);
        setImportPreview(null);
        fetchData();
      } else {
        message.error(res.data.msg);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文件解析失败，请使用模板文件');
    } finally {
      setImportLoading(false);
    }
  };

  const closeImport = () => {
    setIsImportModalOpen(false);
    setImportFile(null);
    setImportUsers([]);
    setImportPreview(null);
  };

  useEffect(() => {
    fetchData(1);
  }, [filters]);

  useEffect(() => {
    if (filters.universityId) {
      fetchSchools(filters.universityId, 'filter');
    } else if (!isSchoolScope) {
      setSchools([]);
    }
  }, [filters.universityId]);

  useEffect(() => {
    if (filters.schoolId) {
      fetchMajors(filters.schoolId, 'filter');
    } else {
      setMajors([]);
    }
  }, [filters.schoolId]);

  const fetchData = async (page = 1, pageSize = pagination.pageSize) => {
    setLoading(true);
    try {
      const params = {
        current: page,
        size: pageSize,
        ...filters,
        ...(isSchoolScope ? { collegeId: filters.schoolId } : {})
      };
      const res = await axios.get(`${apiBase}/list`, { params });
      if (res.data.code === 200) {
        setData(res.data.data.records);
        setPagination({
          current: res.data.data.current,
          pageSize: res.data.data.size,
          total: res.data.data.total
        });
      }
    } catch (error) {
      message.error('获取用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchUniversities = async () => {
    const res = await axios.get('/api/common/universities');
    if (res.data.code === 200) setUniversities(res.data.data);
  };

  const fetchSchools = async (uniId: number, type: 'filter' | 'edit') => {
    const res = await axios.get(`/api/common/schools/${uniId}`);
    if (res.data.code === 200) {
      if (type === 'filter') setSchools(res.data.data);
      else setEditSchools(res.data.data);
    }
  };

  const fetchMajors = async (schoolId: number, type: 'filter' | 'edit') => {
    const res = await axios.get(`/api/common/majors/${schoolId}`);
    if (res.data.code === 200) {
      if (type === 'filter') setMajors(res.data.data);
      else setEditMajors(res.data.data);
    }
  };

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    fetchData(pag.current || 1, pag.pageSize || 10);
  };

  const handleSelectChange = (key: string, value: string | number | null) => {
    setFilters(prev => {
      const newFilters = { ...prev, [key]: value };
      if (key === 'universityId') {
        newFilters.schoolId = null;
        newFilters.majorId = null;
      }
      if (key === 'schoolId') {
        newFilters.majorId = null;
      }
      return newFilters;
    });
  };

  const handleKeywordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setKeywordInput(val);

    if (debounceTimer.current) clearTimeout(debounceTimer.current);

    debounceTimer.current = setTimeout(() => {
      setFilters(prev => ({ ...prev, keyword: val }));
    }, 500);
  };

  const handleManualSearch = () => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    setFilters(prev => ({ ...prev, keyword: keywordInput }));
  };

  const handleReset = () => {
    setKeywordInput('');
    setFilters({
      keyword: '',
      universityId: null,
      schoolId: null,
      majorId: null,
      gender: null,
      gradeYear: null,
      isEnrolledStu: null
    });
    setSchools([]);
    setMajors([]);
  };

  const handleEdit = async (record: UserItem) => {
    setEditingUser(record);
    setIsModalOpen(true);
    setEditingEduBg(record.eduBg);
    setEditIdPhotoFileId(record.idPhotoFileId);
    setEditIdPhotoUrl(record.idPhotoUrl);

    if (record.universityId) {
      await fetchSchools(record.universityId, 'edit');
    }
    if (record.schoolId) {
      await fetchMajors(record.schoolId, 'edit');
    }

    form.setFieldsValue({
      ...record,
      universityId: record.universityId || null,
      schoolId: record.schoolId || null,
      majorId: record.majorId || null
    });
  };

  const handleEditUniChange = async (val: number) => {
    form.setFieldsValue({ schoolId: null, majorId: null });
    setEditMajors([]);
    await fetchSchools(val, 'edit');
  };

  const handleEditSchoolChange = async (val: number) => {
    form.setFieldsValue({ majorId: null });
    await fetchMajors(val, 'edit');
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload: Record<string, unknown> = isSchoolScope
        ? { ...editingUser, ...values, idPhotoFileId: editIdPhotoFileId, role: 'student', accountStatus: editingUser?.accountStatus }
        : { ...editingUser, ...values, idPhotoFileId: editIdPhotoFileId };
      if (!canChangeEmail) delete payload.email;
      if (!isSchoolScope) {
        if (!canChangeRole) delete payload.role;
        if (!canChangeStatus) delete payload.accountStatus;
      }

      const res = await axios.post(`${apiBase}/update`, payload);
      if (res.data.code === 200) {
        message.success('更新成功');
        setIsModalOpen(false);
        fetchData(pagination.current);
      } else {
        message.error(res.data.msg);
      }
    } catch (e) {
    }
  };

  const handleIdPhotoUpload = async (file: File) => {
    if (!editingUser) return false;
    setIdPhotoUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('uploaderId', String(editingUser.userId));
      formData.append('uploadType', 'id_photo');
      const res = await axios.post<{ code: number; data?: number; msg?: string }>('/api/file/upload', formData);
      if (res.data.code !== 200 || !res.data.data) {
        message.error(res.data.msg || '证件照上传失败');
        return false;
      }
      setEditIdPhotoFileId(res.data.data);
      const reader = new FileReader();
      reader.onload = event => setEditIdPhotoUrl(String(event.target?.result || ''));
      reader.readAsDataURL(file);
      message.success('证件照已上传，保存用户信息后生效');
    } catch {
      message.error('证件照上传失败');
    } finally {
      setIdPhotoUploading(false);
    }
    return false;
  };

  const isCollegeStudent = ['undergraduate', 'postgraduate'].includes(editingEduBg);

  const columns: ColumnsType<UserItem> = [
    { title: '序号', key: 'index', width: 60, align: 'center' as const, render: (_: unknown, __: UserItem, index: number) => (pagination.current - 1) * pagination.pageSize + index + 1 },
    { title: '昵称', dataIndex: 'userName', ellipsis: true },
    { title: '真实姓名', dataIndex: 'realName', width: 100 },
    { title: '学号', dataIndex: 'numberId', width: 120 },
    {
      title: '角色',
      dataIndex: 'role',
      width: 120,
      render: (role: string) => {
        const conf = ROLE_MAP[role] || { label: role, color: 'default' };
        return <Tag color={conf.color}>{conf.label}</Tag>;
      }
    },
    {
      title: '账号状态',
      dataIndex: 'accountStatus',
      width: 100,
      render: (status: string) => {
        const conf = STATUS_MAP[status] || { label: status, color: 'default' };
        return <Tag color={conf.color}>{conf.label}</Tag>;
      }
    },
    { title: '学校', dataIndex: 'universityName', ellipsis: true },
    { title: '学院', dataIndex: 'schoolName', ellipsis: true },
    { title: '专业', dataIndex: 'majorName', ellipsis: true },
    { title: '年级', dataIndex: 'gradeYear', width: 80 },
    { title: '在校', dataIndex: 'isEnrolledStu', width: 70, render: (val: number) => val === 1 ? '是' : '否' },
    { title: '注册时间', dataIndex: 'registerTime', width: 160, render: (val: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-' },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right' as const,
      render: (_: unknown, record: UserItem) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Breadcrumb style={{ marginBottom: '16px' }}>
        <Breadcrumb.Item>管理后台</Breadcrumb.Item>
        <Breadcrumb.Item>{isSchoolScope ? '学校报名管理' : '用户管理'}</Breadcrumb.Item>
        <Breadcrumb.Item>{isSchoolScope ? '学生管理' : '用户列表'}</Breadcrumb.Item>
      </Breadcrumb>

      <Card>
        <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={8}>
              <Input
                placeholder="搜索学号、账号、姓名、邮箱、手机号"
                prefix={<SearchOutlined style={{ color: '#ccc' }} />}
                value={keywordInput}
                onChange={handleKeywordChange}
                onPressEnter={handleManualSearch}
                allowClear
              />
            </Col>
            <Col span={16} style={{ textAlign: 'right' }}>
              <Space>
                {canChangeStatus && <AccountBanModal onBanned={() => { fetchData(); }} />}
                <Button type="primary" onClick={() => setIsImportModalOpen(true)}>{isSchoolScope ? '批量导入学生' : '导入用户'}</Button>
                <Button type="primary" onClick={handleManualSearch} icon={<SearchOutlined />}>查询</Button>
                <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
              </Space>
            </Col>
          </Row>

          <Row gutter={12} style={{ marginTop: 12 }}>
            {!isSchoolScope && <Col span={4}>
              <Select
                placeholder="选择学校"
                style={{ width: '100%' }}
                allowClear
                value={filters.universityId}
                onChange={val => handleSelectChange('universityId', val)}
              >
                {universities.map(u => <Option key={u.universityId} value={u.universityId}>{u.universityName}</Option>)}
              </Select>
            </Col>}
            <Col span={4}>
              <Select
                placeholder="选择学院"
                style={{ width: '100%' }}
                allowClear
                disabled={isSchoolScope ? !schoolScopeUniversity : !filters.universityId}
                value={filters.schoolId}
                onChange={val => handleSelectChange('schoolId', val)}
              >
                {schools.map(s => <Option key={s.schoolId} value={s.schoolId}>{s.schoolName}</Option>)}
              </Select>
            </Col>
            <Col span={4}>
              <Select
                placeholder="选择专业"
                style={{ width: '100%' }}
                allowClear
                disabled={!filters.schoolId}
                value={filters.majorId}
                onChange={val => handleSelectChange('majorId', val)}
              >
                {majors.map(m => <Option key={m.majorId} value={m.majorId}>{m.majorName}</Option>)}
              </Select>
            </Col>
            <Col span={3}>
              <Select
                placeholder="性别"
                allowClear
                style={{ width: '100%' }}
                value={filters.gender}
                onChange={val => handleSelectChange('gender', val)}
              >
                <Option value={1}>男</Option>
                <Option value={2}>女</Option>
              </Select>
            </Col>
            <Col span={3}>
              <Input
                placeholder="年级 (如2023)"
                allowClear
                value={filters.gradeYear || ''}
                onChange={e => handleSelectChange('gradeYear', e.target.value)}
              />
            </Col>
            <Col span={3}>
              <Select
                placeholder="是否在校"
                allowClear
                style={{ width: '100%' }}
                value={filters.isEnrolledStu}
                onChange={val => handleSelectChange('isEnrolledStu', val)}
              >
                <Option value={1}>是</Option>
                <Option value={0}>否</Option>
              </Select>
            </Col>
          </Row>
        </Space>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="userId"
          pagination={{
            ...pagination,
            showTotal: (total) => `共 ${total} 条`,
            showSizeChanger: true
          }}
          loading={loading}
          onChange={handleTableChange}
          scroll={{ x: 1500 }}
          size="middle"
        />
      </Card>

      <Modal
        title="导入用户"
        open={isImportModalOpen}
        onOk={handleImport}
        onCancel={closeImport}
        okText={importPreview ? '确认导入可用数据' : '上传并校验'}
        width={800}
        confirmLoading={importLoading}
        destroyOnClose
      >
        <p>请下载模板填写后上传导入，格式：学校 学院 专业 姓名 学号 初始密码</p>
        <p>
          <a href="/user-import-template.xlsx" download>
            <DownloadOutlined /> 下载导入模板
          </a>
        </p>
        <Upload
          accept=".xlsx,.xls"
          maxCount={1}
          beforeUpload={(file) => {
            setImportFile(file);
            setImportUsers([]);
            setImportPreview(null);
            return false; // 阻止自动上传，由 handleImport 解析后提交
          }}
          onRemove={() => { setImportFile(null); setImportUsers([]); setImportPreview(null); }}
          fileList={importFile ? [{ uid: '-1', name: importFile.name, status: 'done' as const }] : []}
        >
          <Button icon={<UploadOutlined />}>选择 Excel 文件</Button>
        </Upload>
        <p style={{ color: '#999', fontSize: 12, marginTop: 8 }}>
          仅支持 .xlsx / .xls 文件；学号列请保持文本格式（避免前导零丢失）；初始密码由管理员在导入时设置（密码留空则由系统随机生成并在导入结果中返回）；导入的账号初始为待激活状态，学生凭"学校+学号+初始密码"激活。
          {isSchoolScope ? '学校/学院以您管理的学校为准。' : ''}
        </p>
        {importPreview && <ImportPreview data={importPreview} />}
      </Modal>

      <Modal
        title="编辑用户信息"
        open={isModalOpen}
        onOk={handleSave}
        onCancel={() => setIsModalOpen(false)}
        width={800}
        destroyOnClose
        forceRender
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="userName" label="用户昵称" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="realName" label="真实姓名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="role" label="用户角色" rules={[{ required: true }]}>
                <Select disabled={!canChangeRole}>
                  {Object.entries(ROLE_MAP).map(([key, val]) => (
                    <Option key={key} value={key}>{val.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="accountStatus" label="账号状态" rules={[{ required: true }]}>
                <Select disabled={!canChangeStatus}>
                  {Object.entries(STATUS_MAP).map(([key, val]) => (
                    <Option key={key} value={key}>{val.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="email" label="邮箱" rules={[{ required: canChangeEmail, type: 'email' }]}>
                <Input disabled={!canChangeEmail} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="手机号" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="numberId" label="学号/工号" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="gender" label="性别" rules={[{ required: true }]}>
                <Select>
                  <Option value={1}>男</Option>
                  <Option value={2}>女</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Divider>证件照</Divider>
          <Row gutter={20} align="middle" style={{ marginBottom: 20 }}>
            <Col flex="120px">
              {editIdPhotoUrl ? (
                <Image
                  src={editIdPhotoUrl}
                  alt="用户证件照"
                  width={96}
                  height={128}
                  style={{ objectFit: 'cover', border: '1px solid #d9d9d9' }}
                />
              ) : (
                <div style={{ width: 96, height: 128, border: '1px dashed #bfbfbf', display: 'grid', placeItems: 'center', color: '#8c8c8c' }}>
                  未上传
                </div>
              )}
            </Col>
            <Col flex="auto">
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Alert type="info" showIcon message="管理员可替用户更新证件照，照片保存后将用于比赛报名和参赛证明。" />
                <ImgCrop rotationSlider aspect={3 / 4} quality={0.92} modalTitle="裁剪证件照">
                  <Upload showUploadList={false} beforeUpload={handleIdPhotoUpload} accept="image/png,image/jpeg">
                    <Button icon={<IdcardOutlined />} loading={idPhotoUploading}>上传并裁剪证件照</Button>
                  </Upload>
                </ImgCrop>
              </Space>
            </Col>
          </Row>

          <Card size="small" title="身份与学籍信息" style={{ marginBottom: 16, background: '#f9f9f9' }}>
            {/* 身份类型选择 */}
            <Row gutter={16}>
              <Col span={24}>
                <Form.Item name="eduBg" label="身份类型" rules={[{ required: true }]}>
                  <Select placeholder="请选择身份类型" onChange={setEditingEduBg}>
                    <Option value="undergraduate">本科生</Option>
                    <Option value="postgraduate">研究生</Option>
                    {!isSchoolScope && <Option value="department">部门</Option>}
                    {!isSchoolScope && <Option value="off-campus">校外</Option>}
                  </Select>
                </Form.Item>
              </Col>
            </Row>

            {/* 根据身份类型动态显示 */}
            {isCollegeStudent ? (
              <>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item name="universityId" label="学校" rules={[{ required: true, message: '请选择学校' }]}>
                      <Select placeholder="选择学校" onChange={handleEditUniChange} showSearch optionFilterProp="children">
                        {universities.map(u => <Option key={u.universityId} value={u.universityId}>{u.universityName}</Option>)}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="schoolId" label="学院" rules={[{ required: true, message: '请选择学院' }]}>
                      <Select placeholder="选择学院" disabled={!editSchools.length} onChange={handleEditSchoolChange} showSearch optionFilterProp="children">
                        {editSchools.map(s => <Option key={s.schoolId} value={s.schoolId}>{s.schoolName}</Option>)}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="majorId" label="专业" rules={[{ required: true, message: '请选择专业' }]}>
                      <Select placeholder="选择专业" disabled={!editMajors.length} showSearch optionFilterProp="children">
                        {editMajors.map(m => <Option key={m.majorId} value={m.majorId}>{m.majorName}</Option>)}
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item name="gradeYear" label="年级" rules={[{ required: true }]}>
                      <Input placeholder="如 2024" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="className" label="行政班级">
                      <Input placeholder="如 1班" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="isEnrolledStu" label="是否在校">
                      <Select>
                        <Option value={1}>是 (在读)</Option>
                        <Option value={0}>否 (毕业/其他)</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>
              </>
            ) : (
              <>
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="universityId" label="所属学校/单位" rules={[{ required: true, message: '请选择学校/单位' }]}>
                      <Select placeholder="选择学校/单位" showSearch optionFilterProp="children">
                        {universities.map(u => <Option key={u.universityId} value={u.universityId}>{u.universityName}</Option>)}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="gradeYear" label="年级 (年份)">
                      <Input placeholder="如 2024" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row>
                  <Col span={12}>
                    <Form.Item name="isEnrolledStu" label="是否在校">
                      <Select>
                        <Option value={1}>是</Option>
                        <Option value={0}>否</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>
              </>
            )}
          </Card>
        </Form>
      </Modal>
    </div>
  );
};

export default UserList;
