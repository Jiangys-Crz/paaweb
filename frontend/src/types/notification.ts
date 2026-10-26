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
