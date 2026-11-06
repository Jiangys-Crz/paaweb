"use client";

import React, { useEffect, useState } from 'react';
import { Modal, Form, Switch, DatePicker, Table, Input, message, Divider, Space, Alert, Radio } from 'antd';
import { getBoardConfig, updateBoardConfig } from '@/services/leaderboard.service';
import dayjs from 'dayjs';
import ResolverSettingsPanel, { defaultResolverSettings } from './ResolverSettingsPanel';
import type { BoardProblemConfig } from '@/types/leaderboard';
import axios from 'axios';

interface Props {
    open: boolean;
    boardId: number;
    eventId: number;
    eventType?: string;
    mode?: 'board' | 'resolver';
    onCancel: () => void;
}

const ExternalBoardConfigModal: React.FC<Props> = ({ open, boardId, eventId, eventType = 'XCPC', mode = 'board', onCancel }) => {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(true);
    const [problems, setProblems] = useState<BoardProblemConfig[]>([]);

    const [loadError, setLoadError] = useState(false);
    const [resolverPasswordSet, setResolverPasswordSet] = useState(false);

    useEffect(() => {
        if (!open) return;
        setLoading(true);
        setLoadError(false);
        form.resetFields();
        let active = true;
        getBoardConfig(boardId).then(res => {
            if (!active) return;
            if (res.data.code !== 200) throw new Error('配置加载失败');
            const data = res.data.data;
            form.setFieldsValue({
                name: data.name,
                isExternalOpen: !!data.isExternalOpen,
                isUnfrozen: !!data.isUnfrozen,
                organizationDisplay: data.organizationDisplay || 'school',
                resolverEnabled: !!data.resolverEnabled,
                resolverSingleStep: data.resolverSingleStep !== false,
                resolverSpeed: data.resolverSpeed || 1,
                resolverSettings: data.resolverSettings ? {
                    ...defaultResolverSettings(), ...data.resolverSettings,
                    awards: { ...defaultResolverSettings().awards, ...data.resolverSettings.awards },
                    display: { ...defaultResolverSettings().display, ...data.resolverSettings.display, theme: 'dark' },
                } : defaultResolverSettings(),
                resolverPassword: undefined,
                actualStartTime: data.actualStartTime ? dayjs(data.actualStartTime) : null,
                actualEndTime: data.actualEndTime ? dayjs(data.actualEndTime) : null,
                freezeTime: data.freezeTime ? dayjs(data.freezeTime) : null,
                gold: data.medalConfig?.gold ?? 0,
                silver: data.medalConfig?.silver ?? 0,
                bronze: data.medalConfig?.bronze ?? 0,
            });
            setProblems(data.problems || []);
            setResolverPasswordSet(!!data.resolverPasswordSet);
            setLoadError(false);
        }).catch(() => {
            if (active) { setLoadError(true); message.error('加载配置失败，请关闭后重试'); }
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [open, boardId, mode, form]);

    const handleOk = async () => {
        try {
            const values = await form.validateFields();
            const identity = { eventId, boardId, configSection: mode };
            const payload = mode === 'resolver' ? {
                ...identity,
                resolverEnabled: eventType === 'XCPC' && !!values.resolverEnabled,
                resolverSingleStep: values.resolverSingleStep !== false,
                resolverSpeed: values.resolverSpeed || 1,
                resolverSettings: values.resolverSettings,
                resolverPassword: values.resolverPassword || undefined,
                medalConfig: { gold: values.gold ?? 0, silver: values.silver ?? 0, bronze: values.bronze ?? 0 },
            } : {
                ...identity,
                name: values.name,
                isExternalOpen: values.isExternalOpen,
                isUnfrozen: eventType === 'XCPC' ? values.isUnfrozen : true,
                organizationDisplay: values.organizationDisplay,
                
                actualStartTime: values.actualStartTime?.format('YYYY-MM-DD HH:mm:ss') || null,
                actualEndTime: values.actualEndTime?.format('YYYY-MM-DD HH:mm:ss') || null,
                freezeTime: eventType === 'XCPC' ? values.freezeTime?.format('YYYY-MM-DD HH:mm:ss') || null : null,
                
            };
            
            setLoading(true);
            const res = await updateBoardConfig(payload);
            if (res.data.code === 200) {
                message.success(mode === 'resolver' ? '滚榜配置已保存' : '外榜配置已保存');
                onCancel();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            if (!(e && typeof e === 'object' && 'errorFields' in e)) message.error(axios.isAxiosError(e) ? e.response?.data?.msg || e.response?.data?.message || '保存配置失败，请重试' : '保存配置失败，请重试');
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
            title={`${mode === 'resolver' ? '滚榜配置' : '外榜配置'} [${eventType}赛制]`}
            open={open} 
            onCancel={onCancel} 
            onOk={handleOk} 
            width={900} 
            confirmLoading={loading}
            okButtonProps={{ disabled: loading || loadError }}
            maskClosable={false}
            destroyOnHidden
        >
            {mode === 'board' && <Alert type="info" showIcon title={`外榜 ID：${boardId}`} description={<div>推送题目：<code>/external/board/{boardId}/problems</code><br />推送提交：<code>/external/board/{boardId}/submissions</code></div>} style={{ marginBottom: 16 }} />}
            <Form form={form} layout="vertical">
                {mode === 'board' ? <>
                <Form.Item name="name" label="外榜场次" rules={[{ required: true, whitespace: true, message: '请输入外榜场次' }, { max: 200 }]}>
                    <Input maxLength={200} placeholder="例如：正式赛、热身赛或其他场次" />
                </Form.Item>
                <Form.Item name="organizationDisplay" label="榜单组织显示" extra="学院未设置 LOGO 时使用所属学校 LOGO。">
                    <Radio.Group options={[{ label: '学院', value: 'school' }, { label: '学校', value: 'university' }]} optionType="button" />
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

                <Divider titlePlacement="left">题目信息</Divider>
                <Table 
                    dataSource={problems} columns={columns} rowKey="displayId" 
                    pagination={false} size="small" scroll={{y: 300}}
                />
                </> : <>
                    <Form.Item name="isUnfrozen" hidden valuePropName="checked"><Switch /></Form.Item>
                    {eventType === 'XCPC' && <ResolverSettingsPanel boardId={boardId} passwordSet={resolverPasswordSet} />}
                </>}
            </Form>
        </Modal>
    );
};

export default ExternalBoardConfigModal;
