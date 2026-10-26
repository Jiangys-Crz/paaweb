"use client";

import React, { useEffect, useState } from 'react';
import { Card, Button, Image, Input, message, Tag, Row, Col, Typography, Empty, Spin, Tabs, Table, Modal, Descriptions, List, Badge, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckCircleOutlined, CloseCircleOutlined, ReloadOutlined, SearchOutlined, EyeOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';

const { TextArea } = Input;
const { Title, Text } = Typography;

interface PendingCertificate {
    certificateVerifyId: number;
    userId: number;
    userRealName: string;
    userNumberId: string;
    userMajorName: string;
    userClassName: string;
    eventName: string;
    categoryName: string;
    levelName: string;
    levelType: string;
    termName: string;
    time: string;
    imageUrl: string;
    createTime: string;
    auditUserRealName?: string;
    status?: string;
    comments?: string;
    replayTime?: string;
    subProjectName?: string;
    isTeam?: number;
    advisor?: string;
    members?: { name: string; studentId: string; isCaptain: number }[];
}

const CertificateAudit: React.FC = () => {
    const [activeTab, setActiveTab] = useState('pending');
    const [loading, setLoading] = useState(false);
    const [user, setUser] = useState<{ realName?: string; userId?: number } | null>(null);
    const [pendingQueue, setPendingQueue] = useState<PendingCertificate[]>([]); 
    const [currentIndex, setCurrentIndex] = useState(0); 
    const [sidebarSearch, setSidebarSearch] = useState(''); 
    const [currentCert, setCurrentCert] = useState<PendingCertificate | null>(null);
    const [comment, setComment] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [listData, setListData] = useState<PendingCertificate[]>([]);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [keyword, setKeyword] = useState(''); 
    const [viewModalOpen, setViewModalOpen] = useState(false);
    const [revokeModalOpen, setRevokeModalOpen] = useState(false);
    const [revokeReason, setRevokeReason] = useState('');
    const [revokingCertId, setRevokeCertId] = useState<number | null>(null);
    const [imageLoading, setImageLoading] = useState(false);
    const [viewImageLoading, setViewImageLoading] = useState(false);

    const handleOpenRevoke = (recordId: number) => {
        setRevokeCertId(recordId);
        setRevokeReason('');
        setRevokeModalOpen(true);
    };

    const submitRevoke = async () => {
        if (!revokeReason.trim()) {
            message.warning('必须填写驳回原因');
            return;
        }
        if (!revokingCertId) return;
        setSubmitting(true);
        try {
            const res = await axios.post('/api/certificate/admin/audit', {
                id: revokingCertId,
                pass: false,
                comment: revokeReason.trim(),
                operatorId: user?.userId
            });
            if (res.data.code === 200) {
                message.success('已驳回该记录');
                setRevokeModalOpen(false);
                fetchHistoryList(pagination.current); 
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            message.error('操作失败');
        } finally {
            setSubmitting(false);
        }
    };

    const [viewRecord, setViewRecord] = useState<PendingCertificate | null>(null);

    useEffect(() => {
        const userInfo = localStorage.getItem('user_info');
        if (userInfo) {
            setUser(JSON.parse(userInfo));
        }
        setPagination(p => ({ ...p, current: 1 }));
        setKeyword('');
        if (activeTab !== 'pending') {
            setCurrentCert(null);
        }
    }, [activeTab]);

    useEffect(() => {
        fetchData();
    }, [activeTab, pagination.current]);

    const fetchData = () => {
        if (activeTab === 'pending') {
            fetchPendingQueue();
        } else {
            fetchHistoryList(pagination.current);
        }
    };

    const fetchPendingQueue = async () => {
        setLoading(true);
        setComment('');
        // Clear the previous applicant immediately. A completed review must not
        // leave its image visible while the next private certificate image loads.
        setCurrentCert(null);
        setImageLoading(false);
        try {
            const res = await axios.get('/api/certificate/admin/list', {
                params: { current: 1, size: 100, status: 'pending' }
            });
            if (res.data.code === 200) {
                const records: PendingCertificate[] = res.data.data.records || [];
                records.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());
                setPendingQueue(records);
                if (records.length > 0) {
                    setCurrentCert(records[0]);
                    setCurrentIndex(0);
                    setImageLoading(Boolean(records[0].imageUrl));
                } else {
                    setCurrentCert(null);
                    setImageLoading(false);
                }
            }
        } catch (e) {
            message.error('获取待审核任务失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSwitchCert = (index: number) => {
        if (index >= 0 && index < pendingQueue.length) {
            const nextCert = pendingQueue[index];
            setCurrentIndex(index);
            setCurrentCert(nextCert);
            setImageLoading(Boolean(nextCert.imageUrl));
            setComment('');
        }
    };

    const fetchHistoryList = async (page: number) => {
        setLoading(true);
        try {
            const res = await axios.get('/api/certificate/admin/list', {
                params: { current: page, size: pagination.pageSize, status: activeTab, keyword }
            });
            if (res.data.code === 200) {
                const records: PendingCertificate[] = res.data.data.records || [];
                records.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());
                setListData(records);
                setPagination({
                    current: res.data.data.current,
                    pageSize: res.data.data.size,
                    total: res.data.data.total
                });
            }
        } catch (e) {
            message.error('加载列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSearchHistory = () => {
        setPagination({ ...pagination, current: 1 });
        fetchHistoryList(1);
    };

    const handleAudit = async (pass: boolean) => {
        if (!pass && !comment.trim()) {
            message.warning('驳回时必须填写审核意见');
            return;
        }
        const finalComment = comment.trim() || (pass ? "审核通过，信息无误。" : "");
        if (!currentCert) return;
        setSubmitting(true);
        try {
            const res = await axios.post('/api/certificate/admin/audit', {
                id: currentCert.certificateVerifyId,
                pass: pass,
                comment: finalComment,
                operatorId: user?.userId
            });
            if (res.data.code === 200) {
                message.success(pass ? '已通过' : '已驳回');
                await fetchPendingQueue(); 
                setSubmitting(false);
            } else {
                message.error(res.data.msg);
                setSubmitting(false);
            }
        } catch (e) {
            message.error('操作失败');
            setSubmitting(false);
        }
    };

    const handleOpenView = (record: PendingCertificate) => {
        setViewRecord(record);
        setViewImageLoading(Boolean(record.imageUrl));
        setViewModalOpen(true);
    };

    const renderWorkbench = () => {
        if (loading && !currentCert) return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" tip="加载任务中..." /></div>;
        if (!currentCert) {
            return (
                <Empty description="暂无待审核证书" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 100 }}>
                    <Button type="primary" icon={<ReloadOutlined />} onClick={fetchPendingQueue}>刷新任务列表</Button>
                </Empty>
            );
        }
        const filteredQueue = pendingQueue.filter(item => (item.userRealName || '').includes(sidebarSearch) || (item.eventName || '').includes(sidebarSearch));

        return (
            <Row gutter={24} style={{ height: 'calc(100vh - 240px)' }}>
                <Col span={6} style={{ borderRight: '1px solid #f0f0f0', height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <div style={{ marginBottom: 12 }}>
                        <Input prefix={<SearchOutlined />} placeholder="搜姓名或赛事..." onChange={e => setSidebarSearch(e.target.value)} allowClear />
                    </div>
                    <div style={{ flex: 1, overflowY: 'auto' }}>
                        <List
                            size="small"
                            itemLayout="horizontal"
                            dataSource={filteredQueue}
                            renderItem={(item) => {
                                const queueIndex = pendingQueue.findIndex(p => p.certificateVerifyId === item.certificateVerifyId);
                                const isSelected = currentCert.certificateVerifyId === item.certificateVerifyId;
                                return (
                                    <List.Item onClick={() => handleSwitchCert(queueIndex)} style={{ cursor: 'pointer', padding: '12px', backgroundColor: isSelected ? '#e6f7ff' : 'transparent', borderLeft: isSelected ? '3px solid #1890ff' : '3px solid transparent', transition: 'all 0.2s', borderBottom: '1px solid #f0f0f0' }}>
                                        <List.Item.Meta title={<div style={{ display: 'flex', justifyContent: 'space-between' }}><Text strong>{item.userRealName || '未知用户'}</Text><Text type="secondary" style={{ fontSize: 12 }}>{item.createTime ? dayjs(item.createTime).format('MM-DD') : '-'}</Text></div>} description={<div style={{ fontSize: 12, color: '#666', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.eventName || '未知赛事'}</div>} />
                                    </List.Item>
                                );
                            }}
                        />
                    </div>
                </Col>
                <Col span={18} style={{ height: '100%', overflowY: 'auto' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, padding: '12px 20px', background: '#fafafa', borderRadius: 8 }}>
                        <Space size="large">
                            <Badge count={pendingQueue.length} overflowCount={999} color="#1890ff"><Title level={5} style={{ margin: 0 }}>待审核证书</Title></Badge>
                            <Text type="secondary">当前进度：{currentIndex + 1} / {pendingQueue.length}</Text>
                        </Space>
                        <Space>
                            <Button icon={<LeftOutlined />} onClick={() => handleSwitchCert(currentIndex - 1)} disabled={currentIndex === 0}>上一条</Button>
                            <Button icon={<RightOutlined />} onClick={() => handleSwitchCert(currentIndex + 1)} disabled={currentIndex === pendingQueue.length - 1}>下一条</Button>
                        </Space>
                    </div>
                    <Row gutter={24}>
                        <Col span={12} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                            <div style={{ width: '100%', height: 500, background: '#333', display: 'flex', justifyContent: 'center', alignItems: 'center', borderRadius: 8, overflow: 'hidden', padding: 10, position: 'relative' }}>
                                {currentCert.imageUrl ? (
                                    <>
                                        {imageLoading && <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1, color: 'white' }}><Spin tip="正在加载证书图片..." /></div>}
                                        <Image
                                            key={currentCert.certificateVerifyId}
                                            src={currentCert.imageUrl}
                                            onLoad={() => setImageLoading(false)}
                                            onError={() => setImageLoading(false)}
                                            style={{ maxHeight: 480, maxWidth: '100%', objectFit: 'contain', opacity: imageLoading ? 0 : 1, transition: 'opacity 0.2s' }}
                                        />
                                    </>
                                ) : (
                                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<span style={{ color: 'white' }}>暂无证书图片</span>} />
                                )}
                            </div>
                        </Col>
                        <Col span={12}>
                            <Card size="small" title="申请人信息" style={{ marginBottom: 16 }}>
                                <Descriptions column={1} size="small">
                                    <Descriptions.Item label="姓名"><Text strong>{currentCert.userRealName || '-'}</Text></Descriptions.Item>
                                    <Descriptions.Item label="学号">{currentCert.userNumberId || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="专业班级">{currentCert.userMajorName} {currentCert.userClassName}</Descriptions.Item>
                                </Descriptions>
                            </Card>
                            <Card size="small" title="证书申报信息" style={{ marginBottom: 16 }}>
                                <Descriptions column={1} size="small">
                                    <Descriptions.Item label="赛事名称"><Text strong style={{ color: '#1890ff' }}>{currentCert.eventName}</Text></Descriptions.Item>
                                    <Descriptions.Item label="赛事类型">{currentCert.categoryName}</Descriptions.Item>
                                    <Descriptions.Item label="奖项等级"><Tag color="gold">{currentCert.levelType}</Tag><Text strong>{currentCert.levelName}</Text></Descriptions.Item>
                                    <Descriptions.Item label="子项目">{currentCert.subProjectName || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="获奖日期">{currentCert.time ? dayjs(currentCert.time).format('YYYY-MM-DD') : '-'}</Descriptions.Item>
                                    <Descriptions.Item label="所属学期">{currentCert.termName || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="指导老师">{currentCert.advisor || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="团队赛">{currentCert.isTeam === 1 ? <Tag color="purple">是</Tag> : <span>否</span>}</Descriptions.Item>
                                </Descriptions>
                                {currentCert.isTeam === 1 && currentCert.members && currentCert.members.length > 0 && (
                                    <div style={{ marginTop: 10, borderTop: '1px solid #f0f0f0', paddingTop: 10 }}>
                                        <div style={{ marginBottom: 5, color: '#666' }}>团队成员:</div>
                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                                            {currentCert.members.map((m, idx) => (
                                                <Tag key={idx} color={m.isCaptain ? 'geekblue' : 'default'}>
                                                    {m.name} ({m.studentId}) {m.isCaptain === 1 && <span style={{fontSize: 10}}>(队长)</span>}
                                                </Tag>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </Card>
                            <div style={{ background: '#f6ffed', padding: 16, borderRadius: 8, border: '1px solid #b7eb8f' }}>
                                <div style={{ marginBottom: 8, fontWeight: 'bold', color: '#389e0d' }}>审核操作</div>
                                <TextArea rows={3} value={comment} onChange={e => setComment(e.target.value)} placeholder="请输入审核意见（驳回时必填）..." style={{ marginBottom: 12 }} />
                                <Row gutter={16}>
                                    <Col span={12}><Button danger block icon={<CloseCircleOutlined />} onClick={() => handleAudit(false)} loading={submitting}>驳回</Button></Col>
                                    <Col span={12}><Button type="primary" block icon={<CheckCircleOutlined />} onClick={() => handleAudit(true)} loading={submitting}>通过</Button></Col>
                                </Row>
                            </div>
                        </Col>
                    </Row>
                </Col>
            </Row>
        );
    };

    const renderList = () => {
        const columns: ColumnsType<PendingCertificate> = [
            { title: 'ID', dataIndex: 'certificateVerifyId', width: 80 },
            { title: '姓名', dataIndex: 'userRealName', width: 100 },
            { title: '赛事名称', dataIndex: 'eventName', ellipsis: true },
            { title: '奖项等级', key: 'level', width: 180, render: (_, r) => <span><Tag>{r.levelType}</Tag>{r.levelName}</span> },
            { title: '状态', dataIndex: 'status', width: 100, render: (status: string) => { if (status === 'approved') return <Tag color="success">已通过</Tag>; if (status === 'rejected') return <Tag color="error">已驳回</Tag>; return <Tag color="default">{status}</Tag>; } },
            { title: '提交时间', dataIndex: 'createTime', width: 160, render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-' },
            { title: '操作', key: 'action', width: 160, render: (_, record) => (
                <Space>
                    <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleOpenView(record)}>详情</Button>
                    {record.status === 'approved' && (
                        <Button type="link" danger size="small" icon={<CloseCircleOutlined />} onClick={() => handleOpenRevoke(record.certificateVerifyId)}>驳回</Button>
                    )}
                </Space>
            ) }
        ];
        return (
            <div>
                <div style={{ marginBottom: 16, display: 'flex', gap: 10 }}>
                    <Input placeholder="搜索姓名或赛事名称" value={keyword} onChange={e => setKeyword(e.target.value)} onPressEnter={handleSearchHistory} style={{ width: 300 }} allowClear />
                    <Button type="primary" icon={<SearchOutlined />} onClick={handleSearchHistory}>搜索</Button>
                    <Button icon={<ReloadOutlined />} onClick={() => fetchHistoryList(1)}>刷新</Button>
                </div>
                <Table columns={columns} dataSource={listData} rowKey="certificateVerifyId" loading={loading} pagination={{ ...pagination, showTotal: (total) => `共 ${total} 条记录`, onChange: (page) => setPagination(p => ({ ...p, current: page })) }} />
            </div>
        );
    };

    return (
        <Card title="证书审核管理" extra={<Tag color="blue">当前管理员: {user?.realName || 'Admin'}</Tag>}>
            <Tabs activeKey={activeTab} onChange={(key) => setActiveTab(key)} type="card" items={[ { key: 'pending', label: '待审核 (工作台)' }, { key: 'approved', label: '已通过记录' }, { key: 'rejected', label: '已驳回记录' }, { key: 'all', label: '全部记录' }, ]} style={{ marginBottom: 16 }} />
            {activeTab === 'pending' ? renderWorkbench() : renderList()}
            <Modal title="证书认证详情" open={viewModalOpen} footer={null} onCancel={() => setViewModalOpen(false)} width={800}>
                {viewRecord && (
                    <Row gutter={24}>
                        <Col span={12}>
                            {viewRecord.imageUrl ? (
                                <div style={{ position: 'relative', minHeight: 220, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                    {viewImageLoading && <Spin tip="正在加载图片..." />}
                                    <Image
                                        key={viewRecord.certificateVerifyId}
                                        src={viewRecord.imageUrl}
                                        onLoad={() => setViewImageLoading(false)}
                                        onError={() => setViewImageLoading(false)}
                                        style={{ maxWidth: '100%', borderRadius: 8, border: '1px solid #f0f0f0', opacity: viewImageLoading ? 0 : 1 }}
                                    />
                                </div>
                            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无证书图片" />}
                        </Col>
                        <Col span={12}>
                            <Descriptions title="基本信息" column={1} size="small" bordered>
                                <Descriptions.Item label="申请人">{viewRecord.userRealName} ({viewRecord.userNumberId})</Descriptions.Item>
                                <Descriptions.Item label="赛事名称">{viewRecord.eventName}</Descriptions.Item>
                                <Descriptions.Item label="赛事类型">{viewRecord.categoryName || '-'}</Descriptions.Item>
                                <Descriptions.Item label="奖项">{viewRecord.levelType} - {viewRecord.levelName}</Descriptions.Item>
                                <Descriptions.Item label="子项目">{viewRecord.subProjectName || '-'}</Descriptions.Item>
                                <Descriptions.Item label="获奖日期">{viewRecord.time ? dayjs(viewRecord.time).format('YYYY-MM-DD') : '-'}</Descriptions.Item>
                                <Descriptions.Item label="所属学期">{viewRecord.termName || '-'}</Descriptions.Item>
                                <Descriptions.Item label="指导老师">{viewRecord.advisor || '-'}</Descriptions.Item>
                                <Descriptions.Item label="团队赛">{viewRecord.isTeam === 1 ? <Tag color="purple">是</Tag> : <span>否</span>}</Descriptions.Item>
                            </Descriptions>
                            {viewRecord.isTeam === 1 && viewRecord.members && viewRecord.members.length > 0 && (
                                <div style={{ marginTop: 20 }}>
                                    <div style={{ fontWeight: 'bold', marginBottom: 8 }}>团队成员:</div>
                                    <List
                                        size="small"
                                        bordered
                                        dataSource={viewRecord.members}
                                        renderItem={(item) => (
                                            <List.Item>
                                                {item.name} ({item.studentId}) {item.isCaptain === 1 && <Tag color="blue">队长</Tag>}
                                            </List.Item>
                                        )}
                                    />
                                </div>
                            )}
                            <div style={{ marginTop: 20 }}>
                                <Descriptions title="审核结果" column={1} size="small" bordered>
                                    <Descriptions.Item label="结果">{viewRecord.status === 'approved' ? <Tag color="green">通过</Tag> : <Tag color="red">驳回</Tag>}</Descriptions.Item>
                                    <Descriptions.Item label="审核意见">{viewRecord.comments}</Descriptions.Item>
                                    <Descriptions.Item label="审核人">{viewRecord.auditUserRealName || '-'}</Descriptions.Item>
                                    <Descriptions.Item label="审核时间">{viewRecord.replayTime ? dayjs(viewRecord.replayTime).format('YYYY-MM-DD HH:mm') : '-'}</Descriptions.Item>
                                </Descriptions>
                            </div>
                        </Col>
                    </Row>
                )}
            </Modal>
            <Modal title="驳回已审核记录" open={revokeModalOpen} onCancel={() => setRevokeModalOpen(false)} onOk={submitRevoke} confirmLoading={submitting}>
                <div style={{ marginBottom: 8 }}>请输入驳回原因（将通知申请人）：</div>
                <TextArea rows={4} value={revokeReason} onChange={e => setRevokeReason(e.target.value)} placeholder="例如：证书信息有误，请重新上传" />
            </Modal>
        </Card>
    );
};

export default CertificateAudit;
