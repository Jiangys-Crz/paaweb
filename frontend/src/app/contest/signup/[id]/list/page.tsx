"use client";

import { Alert, Button, Empty, Input, Select, Skeleton, Statistic, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ArrowLeftOutlined, CheckCircleOutlined, ReloadOutlined, SearchOutlined, TeamOutlined, TrophyOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { rankingText, signupGroupText } from '@/utils/contestDisplay';
import ActivityLayout from '../../../ContestNav';
import styles from './page.module.css';

const EVENT_GROUPS: Record<string, string[]> = {
  GPLT: ['星芒初绽', '长风破浪', '凌霄问鼎'],
  LANQIAO: ['C/C++程序设计', 'Python程序设计', 'Java软件开发'],
};

const GROUP_COLORS: Record<string, string> = {
  official: 'blue',
  girl: 'magenta',
  star: 'gold',
  星芒初绽: 'cyan',
  长风破浪: 'blue',
  凌霄问鼎: 'purple',
  'C/C++程序设计': 'blue',
  Python程序设计: 'green',
  Java软件开发: 'orange',
};

const groupColor = (value?: string | null) => {
  if (!value) return 'blue';
  if (GROUP_COLORS[value]) return GROUP_COLORS[value];
  const palette = ['blue', 'cyan', 'purple', 'green', 'orange', 'magenta'];
  const hash = Array.from(value).reduce((result, char) => (result * 31 + char.charCodeAt(0)) >>> 0, 0);
  return palette[hash % palette.length];
};

interface PublicSignup {
  key: string;
  id?: number | string;
  schoolName?: string;
  teamName?: string;
  teamType?: string;
  membersContent?: string;
  realName?: string;
  gradeYear?: number | string;
  advisor?: string;
  isRanking?: number | string;
}

interface EventInfo {
  name?: string;
  type?: string;
  isTeam?: number;
}

export default function SignupPublicListPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [eventInfo, setEventInfo] = useState<EventInfo | null>(null);
  const [list, setList] = useState<PublicSignup[]>([]);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [teamType, setTeamType] = useState('all');
  const [loading, setLoading] = useState(true);
  const [eventError, setEventError] = useState('');
  const [listError, setListError] = useState('');
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    axios.get(`/api/event/detail/${id}`, { signal: controller.signal }).then(res => {
      if (res.data.code !== 200 || !res.data.data) throw new Error('赛事信息加载失败');
      setEventInfo(res.data.data);
      setEventError('');
    }).catch(() => {
      if (!controller.signal.aborted) setEventError('赛事信息加载失败，请刷新重试。');
    });
    return () => controller.abort();
  }, [id, reloadKey]);

  useEffect(() => {
    const controller = new AbortController();
    // Loading and stale-response handling stay with the request that owns them.
    const fetchSignups = async () => {
      setLoading(true);
      setListError('');
      try {
        const res = await axios.get('/api/event/public/signups', {
          signal: controller.signal,
          params: { eventId: id, current, size: pageSize, keyword: searchKeyword, teamType: teamType === 'all' ? undefined : teamType },
        });
        if (res.data.code !== 200) throw new Error('名单加载失败');
        setList((res.data.data?.records || []).map((record: PublicSignup, index: number) => ({
          ...record,
          key: String(record.id ?? `${current}-${pageSize}-${index}`),
        })));
        setTotal(res.data.data?.total || 0);
      } catch {
        if (!controller.signal.aborted) {
          setList([]);
          setTotal(0);
          setListError('报名名单加载失败，请刷新重试。');
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void fetchSignups();
    return () => controller.abort();
  }, [id, teamType, searchKeyword, current, pageSize, reloadKey]);

  const isTeam = Number(eventInfo?.isTeam) === 1;
  const unit = isTeam ? '队' : '人';
  const filtered = teamType !== 'all' || Boolean(searchKeyword);
  const groups = EVENT_GROUPS[eventInfo?.type || ''] || ['official', 'girl', 'star'];
  const renderGroup = (value?: string) => (
    <Tag color={groupColor(value)} className={styles.groupTag}>
      {signupGroupText(value, isTeam, isTeam ? '正式队伍' : '正式参赛')}
    </Tag>
  );
  const displayText = (value: string | number | undefined) => value || '—';
  const columns: TableColumnsType<PublicSignup> = [
    { title: '序号', key: 'index', width: 72, align: 'center', render: (_, __, index) => <span className={styles.rowIndex}>{(current - 1) * pageSize + index + 1}</span> },
    { title: '学校 / 学院', dataIndex: 'schoolName', width: 200, render: displayText },
    ...(isTeam ? [
      { title: '队伍名称', dataIndex: 'teamName', width: 200, render: (value: string) => <span className={styles.primaryText}>{displayText(value)}</span> },
      { title: '队伍类型', dataIndex: 'teamType', width: 150, render: renderGroup },
      { title: '队伍成员', dataIndex: 'membersContent', width: 220, render: (value: string) => <span className={styles.members}>{displayText(value)}</span> },
    ] : [
      { title: '姓名', dataIndex: 'realName', width: 120, render: (value: string) => <span className={styles.primaryText}>{displayText(value)}</span> },
      { title: '年级', dataIndex: 'gradeYear', width: 100, render: displayText },
      { title: '参赛组别', dataIndex: 'teamType', width: 180, render: renderGroup },
    ]),
    { title: '指导老师', dataIndex: 'advisor', width: 160, render: displayText },
    { title: '参与排名', dataIndex: 'isRanking', width: 110, render: (value: number | string) => <Tag className={styles.rankingTag} color={Number(value) === 1 ? 'green' : 'default'}>{rankingText(value)}</Tag> },
  ];

  const search = () => {
    setCurrent(1);
    setSearchKeyword(keyword.trim());
    setReloadKey(value => value + 1);
  };

  return (
    <ActivityLayout>
      <section className={`activity-content ${styles.page}`}>
        <Button className={styles.backButton} type="text" icon={<ArrowLeftOutlined />} onClick={() => router.push(`/contest/signup/${id}`)}>返回比赛详情</Button>
        <div className={styles.panel}>
          <header className={styles.hero}>
            <div className={styles.heading}>
              <div className={styles.eyebrow}><TrophyOutlined /> 赛事报名公示</div>
              <h1>报名名单公示</h1>
              {eventInfo ? <p className={styles.eventName}>{eventInfo.name}</p> : <Skeleton.Input active size="small" />}
              <div className={styles.approvedNote}><CheckCircleOutlined /> 仅公示已审核通过的报名信息</div>
            </div>
            <div className={styles.summary}>
              <span className={styles.summaryIcon}>{isTeam ? <TeamOutlined /> : <UserOutlined />}</span>
              <Statistic title={filtered ? '筛选结果' : '审核通过'} value={total} suffix={unit} loading={loading} />
            </div>
          </header>

          <div className={styles.body}>
            {(eventError || listError) && <Alert className={styles.error} type="error" showIcon title={eventError || listError} />}
            <div className={styles.listHeading}>
              <div><h2><TeamOutlined /> 参赛名单</h2><p>可按组别筛选，或搜索{isTeam ? '队伍名称' : '姓名'}</p></div>
              {eventInfo && <Tag className={styles.formatTag} color={isTeam ? 'purple' : 'blue'}>{isTeam ? '团队赛' : '个人赛'}</Tag>}
            </div>
            <div className={styles.filters}>
              <Select
                aria-label={isTeam ? '筛选队伍类型' : '筛选参赛组别'}
                className={styles.groupSelect}
                value={teamType}
                disabled={!eventInfo}
                onChange={value => { setTeamType(value); setCurrent(1); }}
                options={[{ value: 'all', label: '全部组别' }, ...groups.map(value => ({ value, label: renderGroup(value) }))]}
              />
              <Input
                aria-label={isTeam ? '搜索队伍名称' : '搜索姓名'}
                className={styles.searchInput}
                prefix={<SearchOutlined />}
                placeholder={isTeam ? '输入队伍名称搜索' : '输入姓名搜索'}
                value={keyword}
                onChange={event => setKeyword(event.target.value)}
                onPressEnter={search}
                onClear={() => { setSearchKeyword(''); setCurrent(1); }}
                allowClear
              />
              <Button type="primary" icon={<SearchOutlined />} onClick={search}>搜索</Button>
              <Button icon={<ReloadOutlined spin={loading} />} onClick={() => setReloadKey(value => value + 1)}>刷新</Button>
            </div>
            <Table<PublicSignup>
              className={styles.table}
              columns={columns}
              dataSource={list}
              loading={loading}
              scroll={{ x: isTeam ? 1220 : 1000 }}
              rowKey="key"
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={listError ? '名单暂时无法加载' : filtered ? '未找到符合条件的报名信息' : '暂无审核通过的报名信息'} /> }}
              pagination={{
                current, pageSize, total, locale: zhCN.Pagination,
                showSizeChanger: true,
                pageSizeOptions: [10, 20, 50, 100],
                showTotal: count => `共 ${count} ${unit}${filtered ? '符合筛选条件' : '审核通过'}`,
                onChange: (page, size) => { setCurrent(size === pageSize ? page : 1); setPageSize(size); },
              }}
            />
            <div className={styles.footerNote}><CheckCircleOutlined /> 名单以审核通过的报名信息为准{filtered && '，当前显示筛选后的结果'}。</div>
          </div>
        </div>
      </section>
    </ActivityLayout>
  );
}
