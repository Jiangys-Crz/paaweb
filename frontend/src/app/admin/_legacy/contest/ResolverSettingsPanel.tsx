'use client';
import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Radio, Row, Space, Switch, Tabs, message } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import axios from 'axios';
import ResolverPhotoManager from './ResolverPhotoManager';
import AwardTeamSelector, { type AwardParticipants } from './AwardTeamSelector';

export const defaultResolverSettings = () => ({
  awards: { showMedals: true, showPodium: true, championLabel: '冠军 Champion', runnerUpLabel: '亚军 Runner-up', secondRunnerUpLabel: '季军 Second Runner-up', firstToSolve: false, solvedCount: false, bestFemaleTeam: false, bestFemaleLabel: '最佳女队 Best Female', goldLabel: 'Gold Medal', silverLabel: 'Silver Medal', bronzeLabel: 'Bronze Medal', customAwards: [] },
  display: { rowsPerScreen: 12, nameTemplate: '{org.name}: {team.name}', theme: 'dark', showOrganizationLogo: true, showTeamPhoto: true, showOrganization: true, showMembers: true },
});

export default function ResolverSettingsPanel({ boardId, passwordSet }: { boardId: number; passwordSet: boolean }) {
  const [participants, setParticipants] = useState<AwardParticipants>({ rankUnavailable: true, participants: [] });
  const [participantsLoading, setParticipantsLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setParticipantsLoading(true);
    axios.get(`/api/leaderboard/admin/${boardId}/resolver/participants`).then(({ data }) => {
      if (!active) return;
      if (data.code !== 200) throw new Error('failed');
      const response = data.data;
      if (Array.isArray(response)) setParticipants({ rankUnavailable: true, participants: response });
      else if (response && typeof response.rankUnavailable === 'boolean' && Array.isArray(response.participants)) setParticipants(response);
      else throw new Error('invalid participant response');
    }).catch(() => { if (active) message.error('无法加载滚榜获奖队伍列表'); }).finally(() => { if (active) setParticipantsLoading(false); });
    return () => { active = false; };
  }, [boardId]);
  const awardName = (key: string) => ['resolverSettings', 'awards', key];
  const displayName = (key: string) => ['resolverSettings', 'display', key];
  return <div>
    <Alert type="info" showIcon title={`数据来源：当前外榜（ID：${boardId}）`} description="滚榜使用此外榜的题目、提交记录和封榜时间；比赛时间和解封状态请在“外榜配置”中修改。" style={{ marginBottom: 16 }} />
    <Tabs items={[
      { key: 'basic', label: '基本设置与照片', forceRender: true, children: <>
        <Space align="start" size="large">
          <Form.Item name="resolverEnabled" label="启用滚榜" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="resolverSingleStep" label="逐步滚榜" valuePropName="checked" tooltip="在 Resolver 的官方暂停点等待下一步；关闭时使用连续滚榜流程。"><Switch /></Form.Item>
          <Form.Item name="resolverSpeed" label="操作间隔速度" extra="仅调整操作之间的等待间隔，动画速度保持不变。1 为标准间隔，范围 0.25–4 倍。" rules={[{ required: true }]}><InputNumber min={0.25} max={4} step={0.25} /></Form.Item>
        </Space>
        <Form.Item name="resolverPassword" label="滚榜密码" dependencies={['resolverEnabled', 'isUnfrozen']}
          extra={passwordSet ? '已设置密码，留空保持不变。' : '封榜时启用滚榜须设置至少12位密码（UTF-8不超过72字节）；已解封可留空。'}
          rules={[({ getFieldValue }) => ({ validator: (_, value?: string) => {
            if (!value) return getFieldValue('resolverEnabled') && !getFieldValue('isUnfrozen') && !passwordSet ? Promise.reject(new Error('封榜时启用滚榜必须设置密码')) : Promise.resolve();
            return value.length < 12 || new TextEncoder().encode(value).length > 72 ? Promise.reject(new Error('密码至少12位，UTF-8编码最多72字节')) : Promise.resolve();
          } })]}>
          <Input.Password autoComplete="new-password" placeholder={passwordSet ? '留空保持原密码' : '设置滚榜密码'} />
        </Form.Item>
        <ResolverPhotoManager boardId={boardId} />
      </> },
      { key: 'awards', label: '奖项设置', forceRender: true, children: <>
        <Alert type="info" showIcon title="奖牌数量同时用于外榜奖牌标记和滚榜颁奖；奖项名称及展示开关仅作用于滚榜。" style={{ marginBottom: 16 }} />
        <Card size="small" title="内置奖项" style={{ marginBottom: 16 }}>
        <Form.Item name={awardName('showPodium')} label="展示冠军、亚军、季军" valuePropName="checked" tooltip="按正式最终排名第 1、2、3 名自动颁发，个人赛与团队赛均支持；打星不参与，同名次均获奖。"><Switch /></Form.Item>
        <Row gutter={12}>{[['championLabel', '冠军'], ['runnerUpLabel', '亚军'], ['secondRunnerUpLabel', '季军']].map(([key, label]) => <Col span={8} key={key}>
          <Form.Item name={awardName(key)} label={`${label}奖项名称`} rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
        </Col>)}</Row>
        <Form.Item name={awardName('showMedals')} label="展示金银铜奖项" valuePropName="checked"><Switch /></Form.Item>
        <Row gutter={12}>{[['gold', '金牌'], ['silver', '银牌'], ['bronze', '铜牌']].map(([key, label]) => <Col span={8} key={key}>
          <Form.Item name={key} label={`${label}数量`}><InputNumber min={0} precision={0} /></Form.Item>
          <Form.Item name={awardName(`${key}Label`)} label={`${label}奖项名称`} rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
        </Col>)}</Row>
        <Row gutter={16} align="top">
          <Col span={8}><Form.Item name={awardName('bestFemaleTeam')} label="最佳女队奖" valuePropName="checked" tooltip="内置奖项，按最终排名自动选择正式女队中的最高名次，同名次均获奖。"><Switch /></Form.Item></Col>
          <Col span={16}><Form.Item name={awardName('bestFemaleLabel')} label="最佳女队奖项名称" extra="与金银铜奖一样可自定义显示名称，无需在自定义奖项中重复添加。" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} placeholder="最佳女队 Best Female" /></Form.Item></Col>
        </Row>
        <Space size="large"><Form.Item name={awardName('firstToSolve')} label="首解奖" valuePropName="checked"><Switch /></Form.Item><Form.Item name={awardName('solvedCount')} label="解题数量奖" valuePropName="checked"><Switch /></Form.Item></Space>
        </Card>
        <Form.List name={awardName('customAwards')}>{(fields, { add, remove }) => <>
          {fields.map(field => <Card size="small" key={field.key} title={`自定义奖项 ${field.name + 1}`} extra={<Button type="text" danger icon={<MinusCircleOutlined />} onClick={() => remove(field.name)}>移除</Button>} style={{ marginBottom: 12 }}>
            <Form.Item name={[field.name, 'citation']} label="奖项名称" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
            <Form.Item name={[field.name, 'participantIds']} label="获奖队伍 / 参赛者" rules={[{ required: true, type: 'array', min: 1, max: 500 }]}><AwardTeamSelector data={participants} loading={participantsLoading} /></Form.Item>
            <Form.Item name={[field.name, 'displayMode']} label="展示方式" rules={[{ required: true }]}><Radio.Group options={[{ label: '逐队展示', value: 'detail' }, { label: '名单展示', value: 'list' }, { label: '照片名单', value: 'photos' }]} /></Form.Item>
          </Card>)}
          <Button onClick={() => add({ citation: '', participantIds: [], displayMode: 'detail' })} icon={<PlusOutlined />} disabled={fields.length >= 20}>添加自定义奖项</Button>
        </>}</Form.List>
      </> },
      { key: 'display', label: '展示设置', forceRender: true, children: <>
        <Alert type="info" showIcon title="屏幕自适应" description="按 Resolver 原生比例自动适配显示区域。行高、字体、队标和题目格一起缩放，大屏按比例放大。" style={{ marginBottom: 16 }} />
        <Form.Item name={displayName('nameTemplate')} label="获奖名单显示名称模板" extra="仅用于获奖名单；主榜固定按标识、组织、队名和队员展示。支持 {org.name}、{org.formal_name}、{team.name}、{team.display_name}、{team.english_name}，其他文字原样显示。" rules={[{ required: true, whitespace: true, max: 200 }, { validator: (_, value?: string) => {
          const remaining = (value || '').replace(/\{(?:org\.name|org\.formal_name|team\.name|team\.display_name|team\.english_name)\}/g, '');
          return /[{}]/.test(remaining) ? Promise.reject(new Error('模板包含不支持或不完整的占位符')) : Promise.resolve();
        } }]}><Input maxLength={200} placeholder="{org.name}: {team.name}" /></Form.Item>
        <Form.Item name={displayName('theme')} hidden><Input /></Form.Item>
        <p>滚榜、获奖照片和获奖名单均使用暗色主题。</p>
        <Row gutter={24}>{[['showOrganizationLogo', '显示组织 LOGO'], ['showTeamPhoto', '展示获奖照片'], ['showOrganization', '显示组织名称'], ['showMembers', '显示队员信息']].map(([key, label]) => <Col span={12} key={key}><Form.Item name={displayName(key)} label={label} valuePropName="checked"><Switch /></Form.Item></Col>)}</Row>
      </> },
    ]} />
  </div>;
}
