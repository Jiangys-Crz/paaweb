"use client";

import React, { useEffect, useState } from 'react';
import { Table, Card, Button, Tag, Space, message, Popconfirm } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, UsergroupAddOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate } from '@/app/admin/_legacy/router';
import axios from 'axios';
import dayjs from 'dayjs';

interface TrainingItem {
    trainId: number;
    name: string;
    termName: string;
    teacherName: string;
    signupStart: string;
    signupEnd: string;
    trainStart: string;
    signupCount: number;
    maxCount: number;
    publisherId: number;
}

const TrainingManagement: React.FC = () => {
    const [list, setList] = useState<TrainingItem[]>([]);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const user = JSON.parse(localStorage.getItem('user_info') || '{}');
    const isSuperAdmin = ['supper_admin', 'super_admin', 'admin'].includes(user.role);

    const getTrainingStatus = (item: any) => {
        const now = dayjs();
        if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', status: 'pending', order: 2 };
        if (now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', status: 'registering', order: 1 };
        return { text: '报名已结束', color: 'red', status: 'ended', order: 3 };
    };

    useEffect(() => {
        const fetchList = async () => {
            setLoading(true);
            try {
                const res = await axios.get('/api/training/list');
                if (res.data.code === 200) {
                    const allData: TrainingItem[] = res.data.data;
                    const filteredData = isSuperAdmin 
                        ? allData 
                        : allData.filter((item) => item.publisherId === user.userId);
                    
                    filteredData.sort((a, b) => {
                        const statusA = getTrainingStatus(a);
                        const statusB = getTrainingStatus(b);
                        
                        if (statusA.order !== statusB.order) {
                            return statusA.order - statusB.order;
                        }
                        
                        if (statusA.status === 'registering') {
                            return new Date(a.signupEnd).getTime() - new Date(b.signupEnd).getTime();
                        } else if (statusA.status === 'pending') {
                            return new Date(a.signupStart).getTime() - new Date(b.signupStart).getTime();
                        } else {
                            return new Date(b.signupEnd).getTime() - new Date(a.signupEnd).getTime();
                        }
                    });
                    
                    setList(filteredData);
                }
            } finally {
                setLoading(false);
            }
        };

        fetchList();
    }, [isSuperAdmin, user.userId, user.role]); // Added dependencies

    const handleDelete = async (trainId: number) => {
        try {
            const res = await axios.post('/api/training/delete', { trainId, publisherId: user.userId });
            if (res.data.code === 200) {
                message.success('删除成功');
                // Refresh list
                const listRes = await axios.get('/api/training/list');
                 if (listRes.data.code === 200) {
                    const allData: TrainingItem[] = listRes.data.data;
                    const filteredData = isSuperAdmin 
                        ? allData 
                        : allData.filter((item) => item.publisherId === user.userId);
                    
                    filteredData.sort((a, b) => {
                        const statusA = getTrainingStatus(a);
                        const statusB = getTrainingStatus(b);
                        
                        if (statusA.order !== statusB.order) {
                            return statusA.order - statusB.order;
                        }
                        
                        if (statusA.status === 'registering') {
                            return new Date(a.signupEnd).getTime() - new Date(b.signupEnd).getTime();
                        } else if (statusA.status === 'pending') {
                            return new Date(a.signupStart).getTime() - new Date(b.signupStart).getTime();
                        } else {
                            return new Date(b.signupEnd).getTime() - new Date(a.signupEnd).getTime();
                        }
                    });
                    
                    setList(filteredData);
                }
            } else {
                message.error(res.data.msg);
            }
        } catch(e) { message.error('操作失败'); }
    };

    const columns: ColumnsType<TrainingItem> = [
        { title: 'ID', dataIndex: 'trainId', width: 60, fixed: 'left' },
        { title: '培训名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
        { title: '学期', dataIndex: 'termName', width: 120 },
        { title: '讲师', dataIndex: 'teacherName', width: 100 },
        { 
            title: '报名时间', 
            key: 'time',
            width: 160,
            render: (_, record) => (
                <div style={{ fontSize: 12, color: '#666' }}>
                    <div>起: {dayjs(record.signupStart).format('YYYY-MM-DD HH:mm')}</div>
                    <div>止: {dayjs(record.signupEnd).format('YYYY-MM-DD HH:mm')}</div>
                </div>
            )
        },
        { 
            title: '报名情况', 
            key: 'signup',
            width: 100,
            render: (_, record) => (
                <span>
                    <span style={{ color: record.signupCount >= record.maxCount ? 'red' : 'green', fontWeight: 'bold' }}>
                        {record.signupCount}
                    </span> / {record.maxCount}
                </span>
            )
        },
        { 
            title: '状态', 
            key: 'status',
            width: 120,
            render: (_, record) => {
                const status = getTrainingStatus(record);
                return <Tag color={status.color}>{status.text}</Tag>;
            }
        },
        {
            title: '操作',
            key: 'action',
            width: 220, 
            fixed: 'right',
            render: (_, record) => (
                <Space>
                    <Button type="primary" ghost size="small" icon={<UsergroupAddOutlined />} onClick={() => navigate(`/admin/training/signups/${record.trainId}`)}>
                        名单管理
                    </Button>
                    <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/admin/training/edit/${record.trainId}`)}>
                        编辑
                    </Button>
                    <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.trainId)}>
                        <Button danger size="small" icon={<DeleteOutlined />}>删除</Button>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <Card title="培训项目管理" extra={
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/training/publish')}>发布新培训</Button>
        }>
            <Table 
                columns={columns} 
                dataSource={list} 
                rowKey="trainId" 
                loading={loading} 
                pagination={{ pageSize: 10 }}
                scroll={{ x: 1300 }}
            />
        </Card>
    );
};

export default TrainingManagement;