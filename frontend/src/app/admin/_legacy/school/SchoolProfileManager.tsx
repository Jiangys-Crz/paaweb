"use client";

import React, { useEffect, useState } from 'react';
import { Alert, Card, Col, Form, Image, Input, Row, Space, Statistic, Upload, Button, message } from 'antd';
import { BankOutlined, CheckCircleOutlined, PictureOutlined, SaveOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';

interface SchoolProfile {
  universityId: number;
  universityName: string;
  logoFileId?: number;
  logoUrl?: string;
  collegeCount: number;
  userCount: number;
}

const SchoolProfileManager: React.FC = () => {
  const [form] = Form.useForm();
  const [profile, setProfile] = useState<SchoolProfile | null>(null);
  const [logoFileId, setLogoFileId] = useState<number>();
  const [logoUrl, setLogoUrl] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const fetchProfile = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/school-signup/school-profile');
      if (res.data.code === 200) {
        const data = res.data.data as SchoolProfile;
        setProfile(data);
        setLogoFileId(data.logoFileId);
        setLogoUrl(data.logoUrl);
        form.setFieldsValue({ universityName: data.universityName });
      } else {
        message.error(res.data.msg || '获取学校信息失败');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const uploadLogo = async (file: File) => {
    setUploading(true);
    try {
      const body = new FormData();
      body.append('file', file);
      body.append('uploadType', 'school_logo');
      const res = await axios.post('/api/file/upload', body);
      if (res.data.code !== 200 || !res.data.data) {
        message.error(res.data.msg || 'LOGO 上传失败');
        return false;
      }
      const fileId = Number(res.data.data);
      setLogoFileId(fileId);
      setLogoUrl(`/api/file/image/${fileId}`);
      message.success('LOGO 已上传，请保存学校信息后生效');
    } catch {
      message.error('LOGO 上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const save = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const res = await axios.post('/api/admin/school-signup/school-profile', {
        universityName: values.universityName,
        logoFileId: logoFileId ?? null,
      });
      if (res.data.code === 200) {
        message.success('学校信息已保存');
        await fetchProfile();
      } else {
        message.error(res.data.msg || '保存失败');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Card
        title="学校信息"
        loading={!profile && loading}
        extra={<Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={save}>保存信息</Button>}
      >
        <Alert
          type="info"
          showIcon
          icon={<CheckCircleOutlined />}
          message="这里只能维护您所属大学的信息，学院、专业和用户数据也会自动限定在本校范围内。"
          style={{ marginBottom: 24 }}
        />
        <Form form={form} layout="vertical" style={{ maxWidth: 760 }}>
          <Form.Item name="universityName" label="学校名称" rules={[{ required: true, message: '请输入学校名称' }]}>
            <Input prefix={<BankOutlined />} maxLength={100} showCount placeholder="请输入学校全称" />
          </Form.Item>
          <Form.Item label="学校 LOGO" extra="支持 PNG、JPG、JPEG，建议使用 1:1 图片，大小不超过 20MB。">
            <Space align="start" size={20}>
              {logoUrl ? (
                <Image src={logoUrl} alt="学校 LOGO" width={128} height={128} style={{ objectFit: 'contain', border: '1px solid #d9d9d9', borderRadius: 8, padding: 8 }} />
              ) : (
                <div style={{ width: 128, height: 128, border: '1px dashed #bfbfbf', borderRadius: 8, display: 'grid', placeItems: 'center', color: '#999' }}>
                  <PictureOutlined style={{ fontSize: 28 }} />
                </div>
              )}
              <Upload accept="image/png,image/jpeg" showUploadList={false} beforeUpload={uploadLogo}>
                <Button icon={<UploadOutlined />} loading={uploading}>上传 LOGO</Button>
              </Upload>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col xs={24} md={8}><Card><Statistic title="学院数量" value={profile?.collegeCount ?? 0} prefix={<BankOutlined />} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="本校用户" value={profile?.userCount ?? 0} prefix={<UserOutlined />} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="信息状态" value={profile?.universityName ? '已完善' : '待完善'} /></Card></Col>
      </Row>
    </div>
  );
};

export default SchoolProfileManager;
