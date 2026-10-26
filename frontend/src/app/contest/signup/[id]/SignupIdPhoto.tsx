'use client';

import { Alert, Button, Image, Modal, Space, Upload, message } from 'antd';
import { DownloadOutlined, IdcardOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import axios from 'axios';
import { useState } from 'react';

interface SignupIdPhotoProps {
  userId: string | number;
  photoUrl?: string;
  onUploaded: () => Promise<void>;
}

export default function SignupIdPhoto({ userId, photoUrl, onUploaded }: SignupIdPhotoProps) {
  const [uploading, setUploading] = useState(false);
  const [uploadedPhotoUrl, setUploadedPhotoUrl] = useState<string>();
  const currentPhotoUrl = uploadedPhotoUrl || photoUrl;

  const uploadPhoto = (file: File) => {
    Modal.confirm({
      title: '确认上传证件照？',
      content: '请确认照片清晰且为本人证件照。上传后重新下载参赛证明即可使用最新照片。',
      okText: '确认上传',
      cancelText: '再检查一下',
      onOk: async () => {
        setUploading(true);
        try {
          const formData = new FormData();
          formData.append('file', file);
          formData.append('uploaderId', String(userId));
          formData.append('uploadType', 'id_photo');
          const uploadRes = await axios.post<{ code: number; data?: string | number; msg?: string }>('/api/file/upload', formData);
          if (uploadRes.data.code !== 200 || !uploadRes.data.data) {
            message.error(uploadRes.data.msg || '证件照上传失败');
            return;
          }
          const bindRes = await axios.post<{ code: number; msg?: string }>('/api/user/id-photo', null, {
            params: { fileId: uploadRes.data.data },
          });
          if (bindRes.data.code !== 200) {
            message.error(bindRes.data.msg || '证件照保存失败');
            return;
          }
          setUploadedPhotoUrl(`/api/user/id-photo/${userId}?v=${Date.now()}`);
          message.success('证件照已更新，请重新下载参赛证明');
          await onUploaded().catch(() => message.warning('照片已保存，报名信息刷新失败，请刷新页面'));
        } catch (error: unknown) {
          message.error(axios.isAxiosError(error) ? error.response?.data?.msg || '证件照上传失败，请稍后重试' : '证件照上传失败，请稍后重试');
        } finally {
          setUploading(false);
        }
      },
    });
    return false;
  };

  const downloadPhoto = async () => {
    try {
      const response = await axios.get<Blob>(`/api/user/id-photo/${userId}`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `证件照.${response.data.type === 'image/png' ? 'png' : 'jpg'}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => window.URL.revokeObjectURL(url), 30_000);
    } catch {
      message.error('证件照下载失败，请稍后重试');
    }
  };

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%', marginTop: 16 }}>
      <Alert
        type={currentPhotoUrl ? 'info' : 'warning'}
        showIcon
        title={currentPhotoUrl ? '可随时更换本人证件照，更新后请重新下载参赛证明。' : '您尚未上传证件照，请补传 3:4 本人证件照后重新下载参赛证明。'}
        description="报名结束或参赛证明下载开放后，仍可上传、修改和下载本人证件照。"
      />
      <Space wrap align="center">
        {currentPhotoUrl && <Image src={currentPhotoUrl} alt="本人证件照" width={90} height={120} style={{ objectFit: 'cover' }} />}
        <ImgCrop rotationSlider aspect={3 / 4} quality={0.92} modalTitle="裁剪证件照">
          <Upload showUploadList={false} beforeUpload={uploadPhoto} disabled={uploading} accept="image/png,image/jpeg">
            <Button icon={<IdcardOutlined />} loading={uploading}>{currentPhotoUrl ? '更换证件照' : '上传证件照'}</Button>
          </Upload>
        </ImgCrop>
        {currentPhotoUrl && <Button icon={<DownloadOutlined />} onClick={downloadPhoto} disabled={uploading}>下载证件照</Button>}
      </Space>
    </Space>
  );
}
