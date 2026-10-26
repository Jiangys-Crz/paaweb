"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Statistic, Table, Form, Select, Input, Button, Space, Breadcrumb, message, Tag } from 'antd';
import { SearchOutlined, ReloadOutlined, ExportOutlined, TrophyOutlined, TeamOutlined, AppstoreOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import axios from 'axios';

const { Option } = Select;

interface StatsData {
    totalContestTypes: number;
    totalUsers: number;
    totalFirstPrize: number;
    totalSecondPrize: number;
    totalThirdPrize: number;
    totalOtherPrize: number;
    totalAwards: number;
    list: CertificateDetail[];
}

interface CertificateDetail {
    no: number;
    schoolName: string;
    contestName: string;
    contestLevel: string;
    awardLevel: string;
    advisor: string;
    studentNames: string;
    studentIds: string;
    awardDate: string;
    teamOrIndividual: string;
    certificateNo?: string;
    remark: string;
}

interface UniversityOption {
    universityId: number;
    universityName: string;
}

interface SchoolOption {
    schoolId: number;
    schoolName: string;
}

interface MajorOption {
    majorId: number;
    majorName: string;
}

interface TermOption {
    termId: number;
    termName: string;
}

interface CategoryOption {
    categoryId: number;
    name: string;
}

interface LevelOption {
    levelType?: string;
}

const levelWeight = (levelType?: string) => {
    if (!levelType) return -1;
    if (levelType.includes('国际')) return 5;
    if (levelType.includes('国家')) return 4;
    if (levelType.includes('省')) return 3;
    if (levelType.includes('市') || levelType.includes('校')) return 2;
    if (levelType.includes('院')) return 1;
    return 0;
};

const CertificateStatistics: React.FC = () => {
    const [loading, setLoading] = useState(false);
    const [stats, setStats] = useState<StatsData>({
        totalContestTypes: 0,
        totalUsers: 0,
        totalFirstPrize: 0,
        totalSecondPrize: 0,
        totalThirdPrize: 0,
        totalOtherPrize: 0,
        totalAwards: 0,
        list: []
    });
    
    // Filters
    const [form] = Form.useForm();
    const [universities, setUniversities] = useState<UniversityOption[]>([]);
    const [schools, setSchools] = useState<SchoolOption[]>([]);
    const [majors, setMajors] = useState<MajorOption[]>([]);
    const [terms, setTerms] = useState<TermOption[]>([]);
    const [categories, setCategories] = useState<CategoryOption[]>([]);
    const [levelScopes, setLevelScopes] = useState<string[]>([]);

    const fetchOptions = useCallback(async () => {
        try {
            const [uniRes, termRes, catRes, levelRes] = await Promise.all([
                axios.get('/api/common/universities'),
                axios.get('/api/certificate/terms'),
                axios.get('/api/contest/category/list', { params: { size: 100 } }),
                axios.get('/api/certificate/levels')
            ]);
            if (uniRes.data.code === 200 && Array.isArray(uniRes.data.data)) setUniversities(uniRes.data.data);
            if (termRes.data.code === 200 && Array.isArray(termRes.data.data)) setTerms(termRes.data.data);
            if (catRes.data.code === 200) {
                // Handle paginated response or direct array
                const data = catRes.data.data as CategoryOption[] | { records?: CategoryOption[] };
                if (Array.isArray(data)) {
                    setCategories(data);
                } else if (data && Array.isArray(data.records)) {
                    setCategories(data.records);
                }
            }
            if (levelRes.data.code === 200 && Array.isArray(levelRes.data.data)) {
                const scopes = Array.from(new Set(
                    levelRes.data.data
                        .map((level: LevelOption) => level.levelType)
                        .filter((levelType: string | undefined): levelType is string => Boolean(levelType))
                )) as string[];
                setLevelScopes(scopes.sort((a, b) => levelWeight(b) - levelWeight(a) || a.localeCompare(b, 'zh-CN')));
            }
        } catch (e) {
            console.error(e);
        }
    }, []);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const values = form.getFieldsValue();
            const res = await axios.post('/api/admin/certificate/stats/list', values);
            if (res.data.code === 200) {
                setStats(res.data.data);
            } else {
                message.error(res.data.msg);
            }
        } catch {
            message.error('获取统计数据失败');
        } finally {
            setLoading(false);
        }
    }, [form]);

    useEffect(() => {
        const loadInitialData = async () => {
            await Promise.all([fetchOptions(), fetchData()]);
        };
        void loadInitialData();
    }, [fetchOptions, fetchData]);

    const handleExport = async () => {
        setLoading(true);
        try {
            const values = form.getFieldsValue();
            const res = await axios.post('/api/admin/certificate/stats/export', values, {
                responseType: 'blob'
            });
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', '获奖证书统计导出.zip');
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            message.success('导出成功');
        } catch {
            message.error('导出失败');
        } finally {
            setLoading(false);
        }
    };

    const handleUniChange = async (val: number) => {
        form.setFieldsValue({ schoolId: null, majorId: null });
        setSchools([]);
        setMajors([]);
        if (val) {
            const res = await axios.get(`/api/common/schools/${val}`);
            if (res.data.code === 200) setSchools(res.data.data);
        }
    };

    const handleSchoolChange = async (val: number) => {
        form.setFieldsValue({ majorId: null });
        setMajors([]);
        if (val) {
            const res = await axios.get(`/api/common/majors/${val}`);
            if (res.data.code === 200) setMajors(res.data.data);
        }
    };

    const columns: ColumnsType<CertificateDetail> = [
        { title: '序号', dataIndex: 'no', width: 60, align: 'center' },
        { title: '学院', dataIndex: 'schoolName', width: 150 },
        { title: '竞赛名称', dataIndex: 'contestName', ellipsis: true },
        { title: '竞赛级别', dataIndex: 'contestLevel', width: 100 },
        { title: '获奖等级', dataIndex: 'awardLevel', width: 100 },
        { title: '第一指导老师', dataIndex: 'advisor', width: 120 },
        { title: '获奖学生', dataIndex: 'studentNames', ellipsis: true },
        { title: '获奖学生学号', dataIndex: 'studentIds', ellipsis: true },
        { title: '获奖年月', dataIndex: 'awardDate', width: 100 },
        { title: '团队或个人', dataIndex: 'teamOrIndividual', width: 100, render: (text: string) => text === '团队' ? <Tag color="purple">团队</Tag> : <Tag color="green">个人</Tag> },
        { title: '证书编号', dataIndex: 'certificateNo', width: 190, render: (text?: string) => text || <Tag>旧证书无编号</Tag> },
        { 
            title: '备注', 
            dataIndex: 'remark', 
            ellipsis: true,
            render: (text: string) => {
                if (!text) return text;
                return text.replace(/\(?GrantFile_\d+\)?/g, '').trim();
            }
        }
    ];

    return (
        <div style={{ padding: 24 }}>
            <Breadcrumb style={{ marginBottom: 16 }}>
                <Breadcrumb.Item>管理后台</Breadcrumb.Item>
                <Breadcrumb.Item>证书管理</Breadcrumb.Item>
                <Breadcrumb.Item>证书统计</Breadcrumb.Item>
            </Breadcrumb>

            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col span={6}>
                    <Card bordered={false} hoverable>
                        <Statistic title="获得奖项总数" value={stats.totalAwards} prefix={<TrophyOutlined />} valueStyle={{ color: '#1890ff', fontWeight: 'bold' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card bordered={false} hoverable>
                        <Statistic title="参加比赛类型总数" value={stats.totalContestTypes} prefix={<AppstoreOutlined />} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card bordered={false} hoverable>
                        <Statistic title="用户总数" value={stats.totalUsers} prefix={<TeamOutlined />} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card bordered={false} hoverable>
                        <Statistic title="获得一等奖总数" value={stats.totalFirstPrize} valueStyle={{ color: '#cf1322' }} prefix={<TrophyOutlined />} />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card bordered={false} hoverable>
                        <Statistic title="获得二等奖总数" value={stats.totalSecondPrize} valueStyle={{ color: '#d48806' }} prefix={<TrophyOutlined />} />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card bordered={false} hoverable>
                        <Statistic title="获得三等奖总数" value={stats.totalThirdPrize} valueStyle={{ color: '#7cb305' }} prefix={<TrophyOutlined />} />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card bordered={false} hoverable>
                        <Statistic title="获得其他奖总数" value={stats.totalOtherPrize} prefix={<TrophyOutlined />} />
                    </Card>
                </Col>
            </Row>

            <Card title="筛选查询" style={{ marginBottom: 24 }}>
                <Form form={form} layout="inline" onFinish={fetchData} style={{ rowGap: 16 }}>
                    <Form.Item name="contestType" label="赛事类型">
                        <Select placeholder="选择类型" style={{ width: 150 }} allowClear>
                            {categories.map(c => <Option key={c.categoryId} value={c.categoryId}>{c.name}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="minLevel" label="最低等级">
                        <Select placeholder="选择最低等级" style={{ width: 120 }} allowClear>
                            {levelScopes.map(levelType => <Option key={levelType} value={levelType}>{levelType}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="termId" label="学期">
                        <Select placeholder="选择学期" style={{ width: 150 }} allowClear>
                            {terms.map(t => <Option key={t.termId} value={t.termId}>{t.termName}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="universityId" label="学校">
                        <Select placeholder="选择学校" style={{ width: 150 }} allowClear onChange={handleUniChange}>
                            {universities.map(u => <Option key={u.universityId} value={u.universityId}>{u.universityName}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="schoolId" label="学院">
                        <Select placeholder="选择学院" style={{ width: 150 }} allowClear disabled={!schools.length} onChange={handleSchoolChange}>
                            {schools.map(s => <Option key={s.schoolId} value={s.schoolId}>{s.schoolName}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="majorId" label="专业">
                        <Select placeholder="选择专业" style={{ width: 150 }} allowClear disabled={!majors.length}>
                            {majors.map(m => <Option key={m.majorId} value={m.majorId}>{m.majorName}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item name="grade" label="年级">
                        <Input placeholder="如 2021" style={{ width: 100 }} allowClear />
                    </Form.Item>
                    <Form.Item name="className" label="班级">
                        <Input placeholder="如 1班" style={{ width: 100 }} allowClear />
                    </Form.Item>
                    <Form.Item name="degreeType" label="学历">
                        <Select placeholder="学历" style={{ width: 100 }} allowClear>
                            <Option value="undergraduate">本科</Option>
                            <Option value="postgraduate">研究生</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item name="keyword" label="搜索">
                        <Input placeholder="学生姓名/学号" style={{ width: 150 }} allowClear />
                    </Form.Item>
                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>查询</Button>
                            <Button icon={<ReloadOutlined />} onClick={() => { form.resetFields(); setSchools([]); setMajors([]); fetchData(); }} loading={loading}>重置</Button>
                            <Button type="primary" icon={<ExportOutlined />} onClick={handleExport} style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }} loading={loading}>导出</Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Card>

            <Card title="获奖名单">
                <Table 
                    columns={columns} 
                    dataSource={stats.list} 
                    rowKey="no" 
                    loading={loading}
                    pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
                />
            </Card>
        </div>
    );
};

export default CertificateStatistics;
