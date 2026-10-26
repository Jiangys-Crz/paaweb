"use client";

import React, { useCallback, useEffect, useState } from 'react';
import { Table, Card, Button, Space, message, Popconfirm, Tag, Input, Select, Row, Col, type TableProps } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from '@/app/admin/_legacy/router';
import axios from 'axios';
import dayjs from 'dayjs';

const { Option } = Select;

interface NoticeRecord {
    infoId: number;
    title: string;
    type: 'notice' | 'publicity' | 'news' | string;
    publisherName?: string;
    createTime?: string;
    viewCount?: number;
}

interface NoticeListResponse {
    records: NoticeRecord[];
    total: number;
}

interface ApiResponse<T> {
    code: number;
    data?: T;
    msg?: string;
}

const NoticeManagement: React.FC = () => {
    const [list, setList] = useState<NoticeRecord[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [keyword, setKeyword] = useState('');
    const [type, setType] = useState('all');
    const navigate = useNavigate();

    const fetchList = useCallback(async (current: number, size: number) => {
        setLoading(true);
        try {
            const res = await axios.get<ApiResponse<NoticeListResponse>>('/api/notice/list', {
                params: {
                    current,
                    size,
                    type,
                    keyword
                }
            });
            
            if (res.data.code === 200 && res.data.data) {
                const records = res.data.data.records;
                const total = res.data.data.total;
                records.sort((a, b) => new Date(b.createTime || '').getTime() - new Date(a.createTime || '').getTime());
                setList(records);
                setPagination({ current, pageSize: size, total });
            }
        } finally {
            setLoading(false);
        }
    }, [keyword, type]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void fetchList(1, 10);
        }, 0);
        return () => window.clearTimeout(timer);
    }, [fetchList]);

    const handleSearch = () => {
        setPagination(prev => ({ ...prev, current: 1 }));
        fetchList(1, pagination.pageSize);
    };

    const handleDelete = async (infoId: number) => {
        try {
            const res = await axios.delete<ApiResponse<string>>(`/api/notice/delete/${infoId}`);
            if (res.data.code === 200) {
                message.success('删除成功');
                fetchList(pagination.current, pagination.pageSize);
            } else {
                message.error(res.data.msg);
            }
        } catch { message.error('操作失败'); }
    };

    const columns: TableProps<NoticeRecord>['columns'] = [
        { title: 'ID', dataIndex: 'infoId', width: 60 },
        { title: '标题', dataIndex: 'title', ellipsis: true },
        { 
            title: '类型', 
            dataIndex: 'type', 
            width: 80,
            render: (type: string) => {
                const map: Record<string, string> = {notice:'通知', publicity:'公示', news:'新闻'};
                return <Tag>{map[type] || type}</Tag>
            }
        },
        { title: '发布人', dataIndex: 'publisherName', width: 100 },
        { 
            title: '发布时间', 
            dataIndex: 'createTime', 
            width: 160,
            render: (text: string) => dayjs(text).format('YYYY-MM-DD HH:mm')
        },
        { title: '浏览', dataIndex: 'viewCount', width: 80 },
        {
            title: '操作',
            key: 'action',
            width: 180,
            render: (_, record) => (
                <Space>
                    <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/admin/notice/edit/${record.infoId}`)}>编辑</Button>
                    <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.infoId)}>
                        <Button danger size="small" icon={<DeleteOutlined />}>删除</Button>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <Card title="公告内容管理" extra={
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/notice/publish')}>发布新公告</Button>
        }>
            <div style={{ marginBottom: 20 }}>
                <Row gutter={16}>
                    <Col>
                        <Select 
                            value={type} 
                            onChange={val => { setType(val); }} 
                            style={{ width: 150 }}
                        >
                            <Option value="all">所有类型</Option>
                            <Option value="notice">通知</Option>
                            <Option value="publicity">公示</Option>
                            <Option value="news">新闻</Option>
                        </Select>
                    </Col>
                    <Col>
                        <Input 
                            placeholder="输入标题搜索" 
                            value={keyword} 
                            onChange={e => setKeyword(e.target.value)} 
                            onPressEnter={handleSearch}
                            style={{ width: 250 }} 
                            allowClear
                        />
                    </Col>
                    <Col>
                        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                    </Col>
                </Row>
            </div>

            <Table 
                columns={columns} 
                dataSource={list} 
                rowKey="infoId" 
                loading={loading} 
                pagination={{
                    ...pagination,
                    onChange: (page, pageSize) => fetchList(page, pageSize)
                }}
            />
        </Card>
    );
};

export default NoticeManagement;
