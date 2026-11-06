"use client";

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Image, Input, Popconfirm, Select, Space, Table, Tabs, Tag, Upload, message } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import axios from 'axios';

type University = { universityId: number; universityName: string; logoFileId?: number | null };
type School = { schoolId: number; universityId: number; schoolName: string; logoFileId?: number | null };

export default function OrganizationInfoManager() {
  const [universities, setUniversities] = useState<University[]>([]);
  const [schools, setSchools] = useState<School[]>([]);
  const [universitySearch, setUniversitySearch] = useState('');
  const [schoolSearch, setSchoolSearch] = useState('');
  const [selectedUniversityId, setSelectedUniversityId] = useState<number>();
  const [schoolPage, setSchoolPage] = useState(1);
  const [activeTab, setActiveTab] = useState('school');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState('');
  const fetchData = async () => {
    setLoading(true);
    try {
      const { data } = await axios.get('/api/admin/org/info');
      if (data.code !== 200) throw new Error(data.msg);
      const nextUniversities: University[] = data.data.universities;
      setUniversities(nextUniversities);
      setSchools(data.data.schools);
      setSelectedUniversityId(current => nextUniversities.some(university => university.universityId === current) ? current : nextUniversities[0]?.universityId);
    } catch { message.error('加载组织信息失败'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void fetchData(); }, []);
  const universityMap = useMemo(() => new Map(universities.map(u => [u.universityId, u])), [universities]);
  const saveLogo = async (kind: 'university' | 'school', id: number, logoFileId: number | null) => {
    const { data } = await axios.post(`/api/admin/org/info/${kind}/${id}/logo`, { logoFileId });
    if (data.code !== 200) throw new Error(data.msg || '保存失败');
    message.success('LOGO 已保存');
    await fetchData();
  };
  const upload = (kind: 'university' | 'school', id: number) => async (file: File) => {
    const key = `${kind}-${id}`;
    setBusy(key);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('uploadType', 'school_logo');
      const { data } = await axios.post('/api/file/upload', form);
      if (data.code !== 200 || !data.data) throw new Error(data.msg || '上传失败');
      await saveLogo(kind, id, Number(data.data));
    } catch (e) { message.error(e instanceof Error ? e.message : '上传失败'); }
    finally { setBusy(''); }
    return false;
  };
  const actions = (kind: 'university' | 'school', id: number, logo?: number | null) => (
    <Space wrap>
      <Upload accept="image/png,image/jpeg,.jpg,.jpeg,.png" showUploadList={false} beforeUpload={upload(kind, id)}>
        <Button icon={<UploadOutlined />} loading={busy === `${kind}-${id}`}>上传{logo ? '替换' : ''}</Button>
      </Upload>
      {logo && <Popconfirm title="确认移除此 LOGO？" onConfirm={async () => {
        setBusy(`${kind}-${id}`);
        try { await saveLogo(kind, id, null); } catch { message.error('移除失败'); } finally { setBusy(''); }
      }}><Button danger icon={<DeleteOutlined />}>移除</Button></Popconfirm>}
    </Space>
  );
  const logo = (id?: number | null) => id ? <Image src={`/api/file/image/${id}`} alt="组织 LOGO" width={56} height={56} style={{ objectFit: 'contain' }} /> : <span style={{ color: '#999' }}>未设置</span>;
  const universityKeyword = universitySearch.trim().toLowerCase();
  const schoolKeyword = schoolSearch.trim().toLowerCase();
  const selectedUniversity = selectedUniversityId === undefined ? undefined : universityMap.get(selectedUniversityId);
  const visibleUniversities = universities.filter(u => u.universityName.toLowerCase().includes(universityKeyword));
  const universitySchools = schools.filter(s => s.universityId === selectedUniversityId);
  const visibleSchools = universitySchools.filter(s => s.schoolName.toLowerCase().includes(schoolKeyword));
  const chooseUniversity = (id: number) => {
    setSelectedUniversityId(id);
    setSchoolSearch('');
    setSchoolPage(1);
  };
  return <Card title="组织信息管理">
    <Alert type="info" showIcon message="学校 LOGO 与学校报名管理员的学校信息共用。学院未设置 LOGO 时，外榜自动使用所属学校的 LOGO。" style={{ marginBottom: 16 }} />
    <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
      { key: 'school', label: '学院 LOGO', children: <>
        <Space wrap style={{ marginBottom: 16, width: '100%' }}>
          <Select aria-label="所属学校" placeholder="选择学校" showSearch optionFilterProp="label" loading={loading} value={selectedUniversityId} onChange={chooseUniversity} options={universities.map(university => ({ value: university.universityId, label: university.universityName }))} style={{ width: 320, maxWidth: '100%' }} />
          <Input.Search aria-label="学院名称" placeholder="在当前学校中搜索学院名称" disabled={!selectedUniversity} allowClear value={schoolSearch} onChange={e => { setSchoolSearch(e.target.value); setSchoolPage(1); }} style={{ width: 320, maxWidth: '100%' }} />
          <span>{visibleSchools.length} / {universitySchools.length} 个学院</span>
        </Space>
        {selectedUniversity && <Card size="small" style={{ marginBottom: 16 }}>
          <Space wrap size="middle">{logo(selectedUniversity.logoFileId)}<div><strong>{selectedUniversity.universityName}</strong><div style={{ color: '#888' }}>学校 LOGO · 学院未设置时使用</div></div>{actions('university', selectedUniversity.universityId, selectedUniversity.logoFileId)}</Space>
        </Card>}
        <Table rowKey="schoolId" loading={loading} dataSource={visibleSchools} pagination={{ current: schoolPage, pageSize: 10, onChange: page => setSchoolPage(page), showSizeChanger: false }} locale={{ emptyText: !selectedUniversity ? '请先选择学校' : schoolKeyword ? '未找到匹配的学院' : '该学校暂无学院' }} scroll={{ x: 700 }} columns={[
        { title: '学院', dataIndex: 'schoolName' },
        { title: '所属学校', render: (_: unknown, row: School) => universityMap.get(row.universityId)?.universityName || '—' },
        { title: '展示 LOGO', render: (_: unknown, row: School) => <Space>{logo(row.logoFileId || universityMap.get(row.universityId)?.logoFileId)}{!row.logoFileId && universityMap.get(row.universityId)?.logoFileId && <Tag>学校 LOGO</Tag>}</Space> },
        { title: '操作', render: (_: unknown, row: School) => actions('school', row.schoolId, row.logoFileId) },
      ]} />
      </> },
      { key: 'university', label: `学校 (${universities.length})`, children: <>
        <Input.Search aria-label="学校名称" placeholder="搜索学校名称" allowClear value={universitySearch} onChange={e => setUniversitySearch(e.target.value)} style={{ maxWidth: 360, marginBottom: 16 }} />
        <Table rowKey="universityId" loading={loading} dataSource={visibleUniversities} scroll={{ x: 600 }} columns={[
        { title: '学校', dataIndex: 'universityName' },
        { title: '学校 LOGO', render: (_: unknown, row: University) => logo(row.logoFileId) },
        { title: '操作', render: (_: unknown, row: University) => <Space wrap>{actions('university', row.universityId, row.logoFileId)}<Button onClick={() => { chooseUniversity(row.universityId); setActiveTab('school'); }}>管理学院</Button></Space> },
      ]} />
      </> },
    ]} />
  </Card>;
}
