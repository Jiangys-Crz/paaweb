"use client";

import React, { useEffect, useState } from 'react';
import { Modal, Form, Switch, DatePicker, InputNumber, Table, Input, message, Divider, Space, Alert } from 'antd';
import { getBoardConfig, updateBoardConfig } from '@/services/leaderboard.service';
import dayjs from 'dayjs';
import type { BoardProblemConfig } from '@/types/leaderboard';

interface Props {
    open: boolean;
    boardId: number;
    eventId: number;
    eventType?: string;
    onCancel: () => void;
}

const ExternalBoardConfigModal: React.FC<Props> = ({ open, boardId, eventId, eventType = 'XCPC', onCancel }) => {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(true);
    const [problems, setProblems] = useState<BoardProblemConfig[]>([]);

    const [loadError, setLoadError] = useState(false);

    useEffect(() => {
        if (!open) return;
        let active = true;
        getBoardConfig(boardId).then(res => {
            if (!active) return;
            if (res.data.code !== 200) throw new Error('配置加载失败');
            const data = res.data.data;
            form.setFieldsValue({
                name: data.name,
                isExternalOpen: !!data.isExternalOpen,
                isUnfrozen: !!data.isUnfrozen,
                actualStartTime: data.actualStartTime ? dayjs(data.actualStartTime) : null,
                actualEndTime: data.actualEndTime ? dayjs(data.actualEndTime) : null,
                freezeTime: data.freezeTime ? dayjs(data.freezeTime) : null,
                gold: data.medalConfig?.gold,
                silver: data.medalConfig?.silver,
                bronze: data.medalConfig?.bronze,
            });
            setProblems(data.problems || []);
            setLoadError(false);
        }).catch(() => {
            if (active) { setLoadError(true); message.error('加载配置失败，请关闭后重试'); }
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [open, boardId, form]);

    const handleOk = async () => {
        try {
            const values = await form.validateFields();
            const payload = {
                eventId,
                boardId,
                name: values.name,
                isExternalOpen: values.isExternalOpen,
                isUnfrozen: eventType === 'XCPC' ? values.isUnfrozen : true,
                
                actualStartTime: values.actualStartTime?.format('YYYY-MM-DD HH:mm:ss') || null,
                actualEndTime: values.actualEndTime?.format('YYYY-MM-DD HH:mm:ss') || null,
                freezeTime: eventType === 'XCPC' ? values.freezeTime?.format('YYYY-MM-DD HH:mm:ss') || null : null,
                
                medalConfig: {
                    gold: values.gold,
                    silver: values.silver,
                    bronze: values.bronze
                },
                problems: problems // Use problems directly as they are read-only
            };
            
            setLoading(true);
            const res = await updateBoardConfig(payload);
            if (res.data.code === 200) {
                message.success('配置已保存');
                onCancel();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            if (!(e && typeof e === 'object' && 'errorFields' in e)) message.error('保存配置失败，请重试');
        } finally {
            setLoading(false);
        }
    };

    const columns = [
        { title: '编号', dataIndex: 'displayId', width: 70 },
        { title: '题目名称', dataIndex: 'name' },
        { title: '分值', dataIndex: 'score', width: 80 },
        { title: '时间(ms)', dataIndex: 'timeLimit', width: 90 },
        { title: '空间(MB)', dataIndex: 'memoryLimit', width: 90 }
    ];

    return (
        <Modal 
            title={`外榜配置 [${eventType}赛制]`} 
            open={open} 
            onCancel={onCancel} 
            onOk={handleOk} 
            width={900} 
            confirmLoading={loading}
            okButtonProps={{ disabled: loading || loadError }}
            maskClosable={false}
            destroyOnHidden
        >
            <Alert type="info" showIcon title={`外榜 ID：${boardId}`} description={<div>推送题目：<code>/external/board/{boardId}/problems</code><br />推送提交：<code>/external/board/{boardId}/submissions</code></div>} style={{ marginBottom: 16 }} />
            <Form form={form} layout="vertical">
                <Form.Item name="name" label="外榜名称" rules={[{ required: true, whitespace: true, message: '请输入外榜名称' }, { max: 200 }]}>
                    <Input maxLength={200} placeholder="例如：正式榜、热身赛榜" />
                </Form.Item>
                <Space size="large" style={{marginBottom: 16}}>
                    <Form.Item name="isExternalOpen" label="开放外榜" valuePropName="checked" style={{marginBottom:0}}>
                        <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                    
                    {/* 修改点 2：仅 XCPC 赛制显示“解除封榜”开关 */}
                    {eventType === 'XCPC' && (
                        <Form.Item name="isUnfrozen" label="解除封榜" valuePropName="checked" style={{marginBottom:0}}>
                            <Switch checkedChildren="已解封" unCheckedChildren="封榜中" />
                        </Form.Item>
                    )}
                </Space>

                <Divider titlePlacement="left">时间配置</Divider>
                <div style={{ display: 'flex', gap: 16 }}>
                    <Form.Item name="actualStartTime" label="实际开始时间">
                        <DatePicker showTime />
                    </Form.Item>
                    <Form.Item name="actualEndTime" label="实际结束时间">
                        <DatePicker showTime />
                    </Form.Item>
                    
                    {/* 仅 XCPC 赛制显示封榜时间设置 */}
                    {eventType === 'XCPC' && (
                        <Form.Item name="freezeTime" label="封榜时间" tooltip="设置封榜开始时间，留空则默认结束前1小时">
                            <DatePicker showTime />
                        </Form.Item>
                    )}
                </div>

                {eventType === 'XCPC' && (
                    <>
                        <Divider titlePlacement="left">奖牌配置</Divider>
                        <Space>
                            <Form.Item name="gold" label="金牌"><InputNumber min={0} /></Form.Item>
                            <Form.Item name="silver" label="银牌"><InputNumber min={0} /></Form.Item>
                            <Form.Item name="bronze" label="铜牌"><InputNumber min={0} /></Form.Item>
                        </Space>
                    </>
                )}

                <Divider titlePlacement="left">题目信息</Divider>
                <Table 
                    dataSource={problems} columns={columns} rowKey="displayId" 
                    pagination={false} size="small" scroll={{y: 300}}
                />
            </Form>
        </Modal>
    );
};

export default ExternalBoardConfigModal;
