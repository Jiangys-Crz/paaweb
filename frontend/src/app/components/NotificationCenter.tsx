"use client";

/* eslint-disable react-hooks/set-state-in-effect, @typescript-eslint/no-explicit-any */

import React, { useState, useEffect, useRef } from 'react';
import { Badge, Popover, List, Typography, Tag, Button, Empty, Spin, Segmented } from 'antd';
import { BellOutlined, CheckCircleOutlined, DownOutlined, UpOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';

const { Text } = Typography;

export interface NotificationVO {
  msgId: number;
  receiverId: number;
  type: number; // 0:系统, 1:邀请, 2:培训, 3:赛事, 4:证书, 5:CP Tracker
  title: string;
  content: string;
  isRead: number;
  createTime: string;
}

export const NotificationTypeMap: Record<number, { label: string; color: string }> = {
  0: { label: '系统通知', color: 'blue' },
  1: { label: '队伍邀请', color: 'cyan' },
  2: { label: '课程培训', color: 'purple' },
  3: { label: '赛事通知', color: 'orange' },
  4: { label: '证书颁发', color: 'gold' },
  5: { label: 'CP Tracker', color: 'green' },
};

const NotificationCenter: React.FC = () => {
  const [visible, setVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<NotificationVO[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [filterType, setFilterType] = useState<'all' | 'unread' | 'read'>('all');
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const filteredList = React.useMemo(() => {
    return list.filter(item => {
      if (filterType === 'all') return true;
      if (filterType === 'unread') return item.isRead === 0;
      if (filterType === 'read') return item.isRead === 1;
      return true;
    });
  }, [list, filterType]);
  const [userId, setUserId] = useState<number | null>(null);

  useEffect(() => {
    // Read user from localStorage in client-side
    if (typeof window !== 'undefined') {
      try {
        const userInfoStr = localStorage.getItem('user_info');
        if (userInfoStr) {
          const u = JSON.parse(userInfoStr);
          if (u && u.userId) setUserId(Number(u.userId));
        }
      } catch (e) {
        console.error("Failed to parse user info", e);
      }
    }
  }, []);

  const fetchUnreadCount = async () => {
    try {
      const res = await axios.get<{ code: number; data: number }>(
        `/api/notification/unread-count?_t=${Date.now()}`,
        { withCredentials: true }
      );
      if (res.data.code === 200) {
        const count = typeof res.data.data === 'number' ? res.data.data : parseInt(res.data.data as unknown as string, 10) || 0;
        setUnreadCount(count);
      }
    } catch (e: any) {
      console.error("获取未读数失败", e);
      console.error("未读数接口报错:", e.response?.status, e.response?.data);
    }
  };

  useEffect(() => {
    if (!userId) return;
    fetchUnreadCount();

    // Listen for auth-state-change to update unreadCount/user
    const handleAuthChange = () => {
      try {
        const userInfoStr = localStorage.getItem('user_info');
        if (userInfoStr) {
          const u = JSON.parse(userInfoStr);
          if (u && u.userId) setUserId(Number(u.userId));
        } else {
          setUserId(null);
        }
      } catch {}
    };
    window.addEventListener('auth-state-change', handleAuthChange);

    return () => {
      window.removeEventListener('auth-state-change', handleAuthChange);
    };
  }, [userId]);

  // ClientRuntime owns the single SSE connection and fans notification events
  // out to this UI component.
  useEffect(() => {
    if (!userId) return;

    const handleRealtimeNotification = (event: Event) => {
      const newMsg = (event as CustomEvent<NotificationVO>).detail;
      if (!newMsg?.msgId) return;
      setUnreadCount(prev => prev + 1);
      setList(prev => {
        if (prev.some(msg => msg.msgId === newMsg.msgId)) return prev;
        return [newMsg, ...prev];
      });
    };

    window.addEventListener('realtime-notification', handleRealtimeNotification);
    return () => window.removeEventListener('realtime-notification', handleRealtimeNotification);
  }, [userId]);

  // Prevent parent scroll chaining when scrolling inside notification popover
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const handleWheel = (e: WheelEvent) => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      const isScrollingUp = e.deltaY < 0;
      const isScrollingDown = e.deltaY > 0;

      e.stopPropagation();

      if (scrollTop <= 0 && isScrollingUp) {
        e.preventDefault();
      }

      if (scrollTop + clientHeight >= scrollHeight - 1 && isScrollingDown) {
        e.preventDefault();
      }
    };

    container.addEventListener('wheel', handleWheel, { passive: false });

    return () => {
      if (container) {
        container.removeEventListener('wheel', handleWheel);
      }
    };
  }, [visible, list]);

  const fetchList = async (currentPage: number, isRefresh = false) => {
    if (loading) return;
    setLoading(true);
    try {
      const res = await axios.get<{ code: number; data: { records: NotificationVO[]; total: number } }>(
        '/api/notification/list',
        {
          params: { page: currentPage, size: 5 },
          withCredentials: true
        }
      );
      if (res.data.code === 200) {
        const newRecords = res.data.data.records || [];
        if (isRefresh) {
          setList(newRecords);
        } else {
          setList(prev => {
            const ids = new Set(prev.map(item => item.msgId));
            const filtered = newRecords.filter(item => !ids.has(item.msgId));
            return [...prev, ...filtered];
          });
        }
        setTotal(res.data.data.total || 0);
      }
    } catch (e: any) {
      console.error("获取通知列表失败", e);
      console.error("列表接口报错:", e.response?.status, e.response?.data);
    } finally {
      setLoading(false);
    }
  };

  const handleVisibleChange = (open: boolean) => {
    setVisible(open);
    if (open) {
      setPage(1);
      fetchList(1, true);
      fetchUnreadCount();
    }
  };

  const handleItemClick = async (item: NotificationVO) => {
    const newExpandedIds = new Set(expandedIds);
    if (newExpandedIds.has(item.msgId)) {
      newExpandedIds.delete(item.msgId);
    } else {
      newExpandedIds.add(item.msgId);
    }
    setExpandedIds(newExpandedIds);

    if (item.isRead === 0) {
      try {
        await axios.post(`/api/notification/read/${item.msgId}`, null, { withCredentials: true });
        setUnreadCount(prev => Math.max(0, prev - 1));
        setList(prev => prev.map(msg =>
          msg.msgId === item.msgId ? { ...msg, isRead: 1 } : msg
        ));
      } catch (e: any) {
        console.error("标记已读失败", e);
        console.error("已读上报报错:", e.response?.status, e.response?.data);
      }
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await axios.post('/api/notification/read-all', null, { withCredentials: true });
      setUnreadCount(0);
      setList(prev => prev.map(msg => ({ ...msg, isRead: 1 })));
    } catch (e: any) {
      console.error("全部标记已读失败", e);
      console.error("一键已读报错:", e.response?.status, e.response?.data);
    }
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    fetchList(nextPage);
  };

  const popoverContent = (
    <div className="notify-popover-content" style={{ width: 360 }}>
      <div className="notify-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, paddingBottom: 8, borderBottom: '1px dashed rgba(120, 180, 220, 0.2)' }}>
        <Text strong style={{ fontSize: 15, color: '#155e9b' }}>站内消息</Text>
        <Button
          type="link"
          size="small"
          icon={<CheckCircleOutlined />}
          onClick={handleMarkAllRead}
          disabled={unreadCount === 0}
          style={{ paddingInline: 0 }}
        >
          全部已读
        </Button>
      </div>

      <div className="notify-filter-row" style={{ marginBottom: 12 }}>
        <Segmented
          block
          size="small"
          value={filterType}
          onChange={value => setFilterType(value as any)}
          options={[
            { label: '全部', value: 'all' },
            { label: `未读 (${unreadCount})`, value: 'unread' },
            { label: '已读', value: 'read' }
          ]}
          style={{ background: 'rgba(120, 180, 220, 0.08)', borderRadius: 8 }}
        />
      </div>

      <div
        ref={scrollContainerRef}
        className="notify-list-container"
        style={{
          maxHeight: 360,
          overflowY: 'auto',
          overscrollBehavior: 'contain',
          position: 'relative',
        }}
      >
        <List
          dataSource={filteredList}
          loading={loading && list.length === 0}
          size="small"
          style={{ margin: 0, padding: 0 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无消息" style={{ margin: '32px 0' }} /> }}
          renderItem={(item) => {
            const typeConfig = NotificationTypeMap[item.type] || { label: '通知', color: 'default' };
            const isExpanded = expandedIds.has(item.msgId);

            return (
              <List.Item
                onClick={() => handleItemClick(item)}
                className={`notify-item ${item.isRead === 0 ? 'unread' : 'read'}`}
                style={{
                  cursor: 'pointer',
                  padding: '12px 14px',
                  borderBottom: '1px solid rgba(120, 180, 220, 0.12)',
                  display: 'block',
                  transition: 'all 0.24s ease'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, alignItems: 'center' }}>
                  <div>
                    <Tag color={typeConfig.color} style={{ marginRight: 6, borderRadius: 6, border: 0 }}>
                      {typeConfig.label}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {dayjs(item.createTime).format('MM-DD HH:mm')}
                    </Text>
                  </div>
                  {item.isRead === 0 && <Badge status="processing" />}
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                  <Text strong={item.isRead === 0} style={{ fontSize: 13, flex: 1, marginRight: 8, color: item.isRead === 0 ? '#1f1f1f' : '#595959' }}>
                    {item.title}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 11, marginTop: 2 }}>
                    {isExpanded ? <UpOutlined style={{ fontSize: 10 }} /> : <DownOutlined style={{ fontSize: 10 }} />}
                  </Text>
                </div>

                {isExpanded && (
                  <div className="notify-detail-box" style={{ marginTop: 8, padding: '8px 12px', background: 'rgba(22, 119, 255, 0.03)', borderRadius: 8, fontSize: 12, color: '#434343', lineHeight: 1.6, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                    {item.content}
                  </div>
                )}
              </List.Item>
            );
          }}
        />
        {loading && list.length > 0 && (
          <div style={{ textAlign: 'center', padding: '12px 0' }}>
            <Spin size="small" />
          </div>
        )}
      </div>

      {list.length < total && !loading && (
        <div style={{ textAlign: 'center', marginTop: 12, paddingBottom: 2 }}>
          <Button size="small" type="text" onClick={loadMore} style={{ fontSize: 12, color: '#1677ff' }}>
            加载更多
          </Button>
        </div>
      )}
    </div>
  );

  return (
    <Popover
      content={popoverContent}
      title={null}
      trigger="click"
      open={visible}
      onOpenChange={handleVisibleChange}
      placement="bottomRight"
      overlayClassName="notification-popover"
      overlayStyle={{ paddingTop: 6 }}
      getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
    >
      <Badge count={unreadCount} overflowCount={99} size="small" offset={[-2, 2]}>
        <button className="notification-trigger-btn" type="button">
          <BellOutlined style={{ fontSize: 17 }} />
        </button>
      </Badge>
    </Popover>
  );
};

export default NotificationCenter;
