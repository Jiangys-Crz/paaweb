"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import {
  Avatar,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Upload,
  message
} from 'antd';
import ImgCrop from 'antd-img-crop';
import {
  CameraOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  InfoCircleOutlined,
  LogoutOutlined,
  PlusOutlined,
  SearchOutlined,
  TeamOutlined,
  UsergroupAddOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { readStoredUser } from '@/utils/auth';
import { userDisplayName, userSearchLabel, type UserSearchResult } from '@/types/user';

type TeamMember = UserSearchResult;

interface Team {
  teamId: number;
  teamName: string;
  introduce?: string;
  teamAvatarUrl?: string;
  teamImageId?: number | null;
  leaderId: number;
  leaderName?: string;
  myStatus: string;
  members?: TeamMember[];
}

type UserProfile = UserSearchResult;

interface TeamManagerProps {
  userId: number;
}

const TeamManager = ({ userId }: TeamManagerProps) => {
  const [teams, setTeams] = useState<Team[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [editingTeam, setEditingTeam] = useState<Team | null>(null);
  const [currentTeamId, setCurrentTeamId] = useState<number | null>(null);
  const [teamAvatarUrl, setTeamAvatarUrl] = useState('');
  const [teamAvatarFileId, setTeamAvatarFileId] = useState<number | null>(null);
  const [fetchingUser, setFetchingUser] = useState(false);
  const [userOptions, setUserOptions] = useState<UserProfile[]>([]);
  const [selectedInviteUser, setSelectedInviteUser] = useState<number | null>(null);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const currentUser = readStoredUser();
  const isOwnProfile = Number(currentUser?.userId) === Number(userId);

  const fetchTeams = useCallback(async () => {
    if (!isOwnProfile) {
      setTeams([]);
      return;
    }
    const res = await axios.get<{ code: number; data?: Team[] }>('/api/team/my-teams');
    if (res.data.code === 200) setTeams(res.data.data || []);
  }, [isOwnProfile]);

  useEffect(() => {
    fetchTeams().catch(() => setTeams([]));
  }, [fetchTeams]);

  const joinedTeams = useMemo(() => teams.filter(team => team.myStatus === '1'), [teams]);
  const pendingTeams = useMemo(() => teams.filter(team => team.myStatus === '0'), [teams]);

  const ensureNormalAccount = () => {
    if (currentUser && currentUser.accountStatus && currentUser.accountStatus !== 'normal') {
      message.error('账号状态异常，无法进行该操作');
      return false;
    }
    return true;
  };

  const handleCreateTeam = async (values: { teamName: string; introduce?: string }) => {
    if (!ensureNormalAccount()) return;
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/create', { ...values, leaderId: userId });
    if (res.data.code === 200) {
      message.success('创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      fetchTeams();
    } else {
      message.error(res.data.msg || '创建失败');
    }
  };

  const openEditModal = (team: Team) => {
    setEditingTeam(team);
    editForm.setFieldsValue(team);
    setTeamAvatarUrl(team.teamAvatarUrl || '');
    setTeamAvatarFileId(team.teamImageId || null);
    setEditOpen(true);
  };

  const handleAvatarUpload = async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('uploaderId', String(userId));
    const res = await axios.post<{ code: number; data?: number; msg?: string }>('/api/file/upload', formData);
    if (res.data.code === 200 && res.data.data) {
      setTeamAvatarFileId(res.data.data);
      const reader = new FileReader();
      reader.onload = event => setTeamAvatarUrl(String(event.target?.result || ''));
      reader.readAsDataURL(file);
      message.success('头像上传成功');
    } else {
      message.error(res.data.msg || '上传失败');
    }
  };

  const handleUpdateTeam = async (values: { teamName: string; introduce?: string }) => {
    if (!editingTeam) return;
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/update', {
      teamId: editingTeam.teamId,
      leaderId: userId,
      teamName: values.teamName,
      introduce: values.introduce,
      teamImageId: teamAvatarFileId
    });
    if (res.data.code === 200) {
      message.success('更新成功');
      setEditOpen(false);
      fetchTeams();
    } else {
      message.error(res.data.msg || '更新失败');
    }
  };

  const handleUserSearch = async (keyword: string) => {
    if (!keyword) return;
    setFetchingUser(true);
    try {
      const res = await axios.get<{ code: number; data?: UserProfile[] }>('/api/user/search', { params: { keyword } });
      if (res.data.code === 200) setUserOptions(res.data.data || []);
    } finally {
      setFetchingUser(false);
    }
  };

  const openInviteModal = (teamId: number) => {
    setCurrentTeamId(teamId);
    setSelectedInviteUser(null);
    setUserOptions([]);
    setInviteOpen(true);
  };

  const handleSendInvite = async () => {
    if (!selectedInviteUser || !currentTeamId) {
      message.warning('请先选择要邀请的用户');
      return;
    }
    if (!ensureNormalAccount()) return;
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/invite', {
      teamId: currentTeamId,
      inviterId: userId,
      targetUserId: selectedInviteUser
    });
    if (res.data.code === 200) {
      message.success('邀请已发送');
      setInviteOpen(false);
    } else {
      message.error(res.data.msg || '发送失败');
    }
  };

  const handleInviteResponse = async (teamId: number, accept: boolean) => {
    if (accept && !ensureNormalAccount()) return;
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/handle-invite', { teamId, userId, accept });
    if (res.data.code === 200) {
      message.success(accept ? '已加入队伍' : '已拒绝');
      fetchTeams();
    } else {
      message.error(res.data.msg || '操作失败');
    }
  };

  const handleRemoveMember = async (teamId: number, memberId: number) => {
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/remove', { teamId, memberId, operatorId: userId });
    if (res.data.code === 200) {
      message.success('操作成功');
      fetchTeams();
    } else {
      message.error(res.data.msg || '操作失败');
    }
  };

  const handleDisbandTeam = async (teamId: number) => {
    const res = await axios.post<{ code: number; msg?: string }>('/api/team/disband', { teamId, operatorId: userId });
    if (res.data.code === 200) {
      message.success('队伍已解散');
      fetchTeams();
    } else {
      message.error(res.data.msg || '操作失败');
    }
  };

  return (
    <div className="team-manager">
      {isOwnProfile && pendingTeams.length > 0 && (
        <div className="team-section">
          <div className="panel-title-row">
            <strong>待处理邀请</strong>
            <Tag color="warning">{pendingTeams.length}</Tag>
          </div>
          <div className="team-grid">
            {pendingTeams.map(team => (
              <Card className="soft-panel team-card" variant="borderless" key={team.teamId}>
                <strong>{team.teamName}</strong>
                <p>队长：{team.leaderName || '未知'}</p>
                <Space>
                  <Popconfirm title="确定加入？" onConfirm={() => handleInviteResponse(team.teamId, true)}>
                    <Button className="bits-primary-button" size="small" icon={<CheckCircleOutlined />}>同意</Button>
                  </Popconfirm>
                  <Popconfirm title="确定拒绝？" onConfirm={() => handleInviteResponse(team.teamId, false)}>
                    <Button danger size="small" icon={<CloseCircleOutlined />}>拒绝</Button>
                  </Popconfirm>
                </Space>
              </Card>
            ))}
          </div>
        </div>
      )}

      <div className="panel-title-row">
        <strong>队伍列表</strong>
        {isOwnProfile && (
          <Button className="bits-primary-button" icon={<PlusOutlined />} onClick={() => ensureNormalAccount() && setCreateOpen(true)}>
            创建新队伍
          </Button>
        )}
      </div>

      {joinedTeams.length ? (
        <List
          grid={{ gutter: 16, xs: 1, sm: 1, md: 2 }}
          dataSource={joinedTeams}
          renderItem={team => {
            const isLeader = team.leaderId === userId;
            return (
              <List.Item>
                <Card
                  className="soft-panel team-card"
                  variant="borderless"
                  title={<Space><Avatar shape="square" src={team.teamAvatarUrl} icon={<TeamOutlined />} /> <span>{team.teamName}</span></Space>}
                  extra={isLeader ? <Tag color="gold">队长</Tag> : <Tag color="processing">队员</Tag>}
                >
                  <p>{team.introduce || '暂无介绍'}</p>
                  <div className="team-members">
                    {(team.members || []).map(member => (
                      <Tooltip title={userSearchLabel(member)} key={member.userId}>
                        <span className="team-member-avatar">
                          <Avatar src={member.avatarUrl}>{userDisplayName(member)[0]}</Avatar>
                          {isLeader && isOwnProfile && member.userId !== userId && (
                            <Popconfirm title={`踢出 ${userDisplayName(member)}？`} onConfirm={() => handleRemoveMember(team.teamId, member.userId)}>
                              <CloseCircleOutlined />
                            </Popconfirm>
                          )}
                        </span>
                      </Tooltip>
                    ))}
                  </div>
                  {isOwnProfile && (
                    <Space className="team-actions" wrap>
                      {isLeader ? (
                        <>
                          <Button onClick={() => openEditModal(team)}>设置</Button>
                          <Button onClick={() => openInviteModal(team.teamId)}>邀请</Button>
                          <Popconfirm title="确定解散队伍？" onConfirm={() => handleDisbandTeam(team.teamId)}>
                            <Button danger>解散</Button>
                          </Popconfirm>
                        </>
                      ) : (
                        <Popconfirm title="确定退出队伍？" onConfirm={() => handleRemoveMember(team.teamId, userId)}>
                          <Button danger icon={<LogoutOutlined />}>退队</Button>
                        </Popconfirm>
                      )}
                    </Space>
                  )}
                </Card>
              </List.Item>
            );
          }}
        />
      ) : <Empty description="暂无队伍" />}

      <Modal title="创建新队伍" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => createForm.submit()} destroyOnHidden>
        <Form form={createForm} onFinish={handleCreateTeam} layout="vertical">
          <Form.Item name="teamName" label="队伍名称" rules={[{ required: true, message: '请输入队伍名称' }]}><Input className="bits-input" /></Form.Item>
          <Form.Item name="introduce" label="队伍宣言"><Input.TextArea className="bits-input" rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="编辑队伍信息" open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => editForm.submit()} destroyOnHidden>
        <Form form={editForm} onFinish={handleUpdateTeam} layout="vertical">
          <div className="avatar-edit-zone">
            <ImgCrop rotationSlider aspect={1} quality={0.8} modalTitle="裁剪队伍头像">
              <Upload showUploadList={false} beforeUpload={file => { handleAvatarUpload(file); return false; }}>
                <button className="avatar-edit-button" type="button">
                  <Avatar size={96} src={teamAvatarUrl} icon={<TeamOutlined />} />
                  <span><CameraOutlined /></span>
                </button>
              </Upload>
            </ImgCrop>
          </div>
          <Form.Item name="teamName" label="队伍名称" rules={[{ required: true }]}><Input className="bits-input" /></Form.Item>
          <Form.Item name="introduce" label="队伍宣言"><Input.TextArea className="bits-input" rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal title="邀请新成员" open={inviteOpen} onCancel={() => setInviteOpen(false)} onOk={handleSendInvite} okText="发送邀请" destroyOnHidden>
        <Select
          showSearch
          placeholder="输入昵称搜索"
          filterOption={false}
          onSearch={handleUserSearch}
          onChange={value => setSelectedInviteUser(Number(value))}
          notFoundContent={fetchingUser ? <Spin size="small" /> : <Empty description="暂无用户" />}
          style={{ width: '100%' }}
          suffixIcon={<SearchOutlined />}
          value={selectedInviteUser || undefined}
          options={userOptions.map(user => ({
            value: user.userId,
            label: (
              <Space>
                <Avatar size="small" src={user.avatarUrl} icon={<UsergroupAddOutlined />} />
                <span>{userSearchLabel(user)}</span>
              </Space>
            )
          }))}
        />
        <p className="team-modal-tip"><InfoCircleOutlined /> 仅能搜索到已注册且未加入该队伍的用户。</p>
      </Modal>
    </div>
  );
};

export default TeamManager;
