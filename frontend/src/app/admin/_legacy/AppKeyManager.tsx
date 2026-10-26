'use client';

import { useState, useEffect } from 'react';
import { Alert, Table, Button, Switch, Modal, Form, Input, Checkbox, message, Popconfirm, Typography, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, CopyOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons';
import {
    getAppKeyList, generateAppKey, toggleAppKeyStatus, updateAppKeyPermissions, deleteAppKey,
    type ExternalApp,
} from '@/services/appKey.service';

const { Text } = Typography;
type Permission = 'external_login' | 'external_board';
interface KeyFormValues { appName: string; permissions: Permission[] }
const permissionOptions = [
    { label: '外部登录', value: 'external_login' },
    { label: '外榜', value: 'external_board' },
];

export default function AppKeyManager() {
    const [data, setData] = useState<ExternalApp[]>([]);
    const [loading, setLoading] = useState(true);
    const [open, setOpen] = useState(false);
    const [editingApp, setEditingApp] = useState<ExternalApp | null>(null);
    const [saving, setSaving] = useState(false);
    const [pendingId, setPendingId] = useState<number | null>(null);
    const [form] = Form.useForm<KeyFormValues>();

    const fetchList = async () => {
        setLoading(true);
        try {
            const { data: res } = await getAppKeyList();
            if (res.code === 200) setData(res.data);
            else message.error(res.msg || '获取列表失败');
        } catch { message.error('获取密钥列表失败，请重试'); }
        finally { setLoading(false); }
    };
    useEffect(() => {
        let cancelled = false;
        getAppKeyList().then(({ data: res }) => {
            if (cancelled) return;
            if (res.code === 200) setData(res.data);
            else message.error(res.msg || '获取列表失败');
        }).catch(() => {
            if (!cancelled) message.error('获取密钥列表失败，请重试');
        }).finally(() => {
            if (!cancelled) setLoading(false);
        });
        return () => { cancelled = true; };
    }, []);

    const openForm = (app: ExternalApp | null) => {
        setEditingApp(app);
        form.resetFields();
        const permissions: Permission[] = [];
        if (app?.externalLoginEnabled) permissions.push('external_login');
        if (app?.externalBoardEnabled) permissions.push('external_board');
        form.setFieldsValue({ appName: app?.appName || '', permissions });
        setOpen(true);
    };

    const handleSave = async (values: KeyFormValues) => {
        if (saving) return;
        setSaving(true);
        const permissions = {
            externalLoginEnabled: (values.permissions || []).includes('external_login'),
            externalBoardEnabled: (values.permissions || []).includes('external_board'),
        };
        try {
            const { data: res } = editingApp
                ? await updateAppKeyPermissions(editingApp.appId, permissions)
                : await generateAppKey(values.appName.trim(), permissions);
            if (res.code !== 200) { message.error(res.msg || '保存失败'); return; }
            message.success(editingApp ? '权限已更新' : '密钥生成成功');
            setOpen(false);
            form.resetFields();
            await fetchList();
        } catch { message.error('保存失败，请刷新列表确认结果'); }
        finally { setSaving(false); }
    };

    const handleToggleStatus = async (app: ExternalApp) => {
        setPendingId(app.appId);
        try {
            const { data: res } = await toggleAppKeyStatus(app.appId, app.status === 1 ? 0 : 1);
            if (res.code !== 200) { message.error(res.msg || '状态更新失败'); return; }
            message.success('密钥状态已更新');
            await fetchList();
        } catch { message.error('状态更新失败，请刷新列表确认结果'); }
        finally { setPendingId(null); }
    };

    const handleDelete = async (appId: number) => {
        setPendingId(appId);
        try {
            const { data: res } = await deleteAppKey(appId);
            if (res.code !== 200) { message.error(res.msg || '删除失败'); return; }
            message.success('密钥已删除');
            await fetchList();
        } catch { message.error('删除失败，请刷新列表确认结果'); }
        finally { setPendingId(null); }
    };

    const busy = pendingId !== null || saving;
    const columns: ColumnsType<ExternalApp> = [
        { title: 'ID', dataIndex: 'appId', width: 70 },
        { title: '使用网站（备注）', dataIndex: 'appName' },
        {
            title: 'API Key', dataIndex: 'appKey',
            render: (text: string) => <Text copyable={{ icon: [<CopyOutlined key="copy" />, <CopyOutlined key="copied" />] }}>{text}</Text>,
        },
        {
            title: '权限', key: 'permissions', width: 180,
            render: (_, app) => <Space wrap>
                {app.externalLoginEnabled && <Tag color="blue">外部登录</Tag>}
                {app.externalBoardEnabled && <Tag color="green">外榜</Tag>}
                {!app.externalLoginEnabled && !app.externalBoardEnabled && <Tag>未授权</Tag>}
            </Space>,
        },
        {
            title: '创建时间', dataIndex: 'createTime', width: 180,
            render: (text: string) => text ? text.replace('T', ' ').substring(0, 19) : '-',
        },
        {
            title: '状态', dataIndex: 'status', width: 110,
            render: (status: number, app) => <Popconfirm
                title={`确定${status === 1 ? '禁用' : '启用'}该密钥？`}
                onConfirm={() => handleToggleStatus(app)} disabled={busy} okText="确定" cancelText="取消">
                <Switch checked={status === 1} disabled={busy} loading={pendingId === app.appId}
                    checkedChildren="已启用" unCheckedChildren="已禁用" />
            </Popconfirm>,
        },
        {
            title: '操作', key: 'actions', width: 190,
            render: (_, app) => <Space>
                <Button type="link" icon={<SettingOutlined />} disabled={busy} onClick={() => openForm(app)}>设置权限</Button>
                <Popconfirm title="确定删除此 API Key？" description="删除后该密钥立即失效，无法恢复。"
                    onConfirm={() => handleDelete(app.appId)} disabled={busy} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
                    <Button type="link" danger icon={<DeleteOutlined />} disabled={busy}>删除</Button>
                </Popconfirm>
            </Space>,
        },
    ];

    return <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2>API Key 授权管理</h2>
            <Button type="primary" icon={<PlusOutlined />} disabled={busy} onClick={() => openForm(null)}>分配新密钥</Button>
        </div>
        <Alert style={{ marginBottom: 16 }} type="info" showIcon
            message="密钥启用后，仅可调用已勾选权限的接口。外部登录权限包含登录验证码；外榜权限包含题目及提交记录上传。" />
        <Table columns={columns} dataSource={data} rowKey="appId" loading={loading} pagination={false} scroll={{ x: 1200 }} />
        <Modal title={editingApp ? `设置权限：${editingApp.appName}` : '分配新的 API Key'} open={open}
            onOk={() => form.submit()} okText={editingApp ? '保存权限' : '生成密钥'} confirmLoading={saving}
            onCancel={() => { setOpen(false); form.resetFields(); }} closable={!saving} maskClosable={false}
            cancelButtonProps={{ disabled: saving }}>
            <Form form={form} onFinish={handleSave} layout="vertical" disabled={saving} initialValues={{ permissions: [] }}>
                {!editingApp && <Form.Item name="appName" label="使用网站名称/备注" rules={[
                    { required: true, whitespace: true, message: '请输入接入的第三方网站名称' },
                    { max: 50, message: '网站名称不能超过50字' },
                ]}>
                    <Input maxLength={50} placeholder="例如：教务处综合服务网" />
                </Form.Item>}
                <Form.Item name="permissions" label="授予权限" extra="未勾选的权限无法调用；不勾选任何权限时，密钥无法调用上述接口。">
                    <Checkbox.Group options={permissionOptions} />
                </Form.Item>
            </Form>
        </Modal>
    </div>;
}
