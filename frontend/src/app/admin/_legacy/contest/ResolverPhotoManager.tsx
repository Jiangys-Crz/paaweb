'use client';
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Space, Upload, Progress, Typography, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import axios from 'axios';

export default function ResolverPhotoManager({ boardId }: { boardId: number }) {
  const [status, setStatus] = useState<{ photoCount: number; hasCover: boolean }>({ photoCount: 0, hasCover: false });
  const [busy, setBusy] = useState(false);
  const [percent, setPercent] = useState(0);
  const [unmatched, setUnmatched] = useState<string[]>([]);
  const refresh = useCallback(async () => {
    try { const res = await axios.get(`/api/leaderboard/admin/${boardId}/resolver/photos`); if (res.data.code === 200) setStatus(res.data.data); }
    catch { message.error('无法加载滚榜照片状态'); }
  }, [boardId]);
  useEffect(() => { void refresh(); }, [refresh]);
  const upload = async (file: File, cover: boolean) => {
    if (busy) return false;
    if (file.size > (cover ? 32 : 512) * 1024 * 1024) { message.error(cover ? '滚榜封面最多32MB' : 'ZIP最多512MB'); return false; }
    setBusy(true); setPercent(0); setUnmatched([]);
    try {
      const body = new FormData(); body.append('file', file);
      const res = await axios.post(`/api/leaderboard/admin/${boardId}/resolver/photos`, body, {
        params: { cover }, timeout: 600000,
        onUploadProgress: progress => setPercent(Math.min(99, Math.round((progress.loaded / (progress.total || file.size)) * 100))),
      });
      if (res.data.code !== 200) throw new Error(res.data.msg || '上传失败');
      setPercent(100); setUnmatched(res.data.data.unmatched || []);
      message.success(cover ? '滚榜封面已保存' : `已导入 ${res.data.data.imported} 张照片`);
      await refresh();
    } catch (error) {
      message.error(axios.isAxiosError(error) ? error.response?.data?.message || error.response?.data?.msg || '照片上传失败，请重试' : error instanceof Error ? error.message : '照片上传失败');
    } finally { setBusy(false); }
    return false;
  };
  const removeCover = async () => {
    setBusy(true);
    try {
      const res = await axios.delete(`/api/leaderboard/admin/${boardId}/resolver/cover`);
      if (res.data.code !== 200) throw new Error('移除失败');
      message.success('滚榜封面已移除');
      await refresh();
    } catch { message.error('无法移除滚榜封面'); }
    finally { setBusy(false); }
  };
  return <div style={{ marginBottom: 20 }}>
    <Alert type="info" showIcon title={`队伍照片 ${status.photoCount} 张 · ${status.hasCover ? '已设置滚榜封面' : '尚未设置滚榜封面'}`}
      description="ZIP内图片以本赛事导入的比赛账号命名，支持JPG、JPEG、PNG（不区分扩展名大小写），可放在子文件夹中，透明 PNG 会保留透明区域。重复上传会更新对应账号的照片；未匹配的文件会列出。封面图仅替换 Resolver 开场页中央赛事图片，标题、待揭晓数量、底部署名和版本号保留。队伍没有照片时展示奖牌。" />
    <Space style={{ marginTop: 12 }}>
      <Upload accept=".zip" showUploadList={false} beforeUpload={file => upload(file, false)} disabled={busy}>
        <Button icon={<UploadOutlined />} loading={busy}>上传照片 ZIP（最多512MB）</Button>
      </Upload>
      <Upload accept=".jpg,.jpeg,.png,.JPG,.JPEG,.PNG" showUploadList={false} beforeUpload={file => upload(file, true)} disabled={busy}>
        <Button icon={<UploadOutlined />}>上传滚榜封面（最多32MB）</Button>
      </Upload>
      {status.hasCover && <Button danger disabled={busy} onClick={() => { void removeCover(); }}>移除封面</Button>}
    </Space>
    {busy && <Progress percent={percent} status="active" format={value => value === 99 ? '处理中' : `${value}%`} />}
    {!!unmatched.length && <Typography.Paragraph type="warning" style={{ marginTop: 8 }}>未匹配比赛账号：{unmatched.join('、')}</Typography.Paragraph>}
  </div>;
}
