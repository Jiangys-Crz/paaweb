"use client";

import { Button, Card, Form, Input, Radio, Select, Steps, Typography, message } from 'antd';
import {
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyOutlined,
  UserOutlined,
  IdcardOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { md5 } from 'js-md5';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import SiteHeader from '../components/SiteHeader';

const { Title, Paragraph } = Typography;

interface University {
  universityId: number;
  universityName: string;
}

interface IdentityForm {
  universityId: number;
  numberId: string;
  initialPassword: string;
}

interface ActivationForm {
  nickName: string;
  userAccount: string;
  email: string;
  verifyCode: string;
  phone: string;
  gender: number;
  password: string;
  confirmPassword: string;
}

export default function ActivationPage() {
  const router = useRouter();
  const [identityForm] = Form.useForm<IdentityForm>();
  const [form] = Form.useForm<ActivationForm>();
  const [step, setStep] = useState(0);
  const [universities, setUniversities] = useState<University[]>([]);
  const [identityLoading, setIdentityLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [codeLoading, setCodeLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [verified, setVerified] = useState<{ userId: number; realName: string; numberId: string } | null>(null);
  const [identityValues, setIdentityValues] = useState<IdentityForm | null>(null);

  useEffect(() => {
    document.title = "激活账号";
    axios.get('/api/common/universities').then((res) => {
      if (res.data.code === 200) setUniversities(res.data.data || []);
    });
  }, []);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = window.setTimeout(() => setCountdown(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  // 身份验证：学校 + 学号 + 初始密码
  const verifyIdentity = async () => {
    try {
      const values = await identityForm.validateFields();
      setIdentityLoading(true);
      const res = await axios.post('/api/user/activate/verify-identity', values);
      if (res.data.code === 200) {
        const data = res.data.data;
        setVerified({ userId: data.userId, realName: data.realName || '', numberId: data.numberId || '' });
        setIdentityValues(values);
        form.setFieldsValue({
          nickName: data.realName || ''
        });
        setStep(1);
      } else {
        message.error(res.data.msg || '身份验证失败');
      }
    } catch {
      message.warning('请检查输入项是否完整');
    } finally {
      setIdentityLoading(false);
    }
  };

  const sendCode = async () => {
    const email = form.getFieldValue('email');
    if (!email) {
      message.error('请先填写邮箱');
      return;
    }
    try {
      setCodeLoading(true);
      const res = await axios.post<{ code: number; msg?: string }>(`/api/user/send-code?email=${email}&type=register`);
      if (res.data.code === 200) {
        message.success('验证码已发送');
        setCountdown(60);
      } else {
        message.error(res.data.msg || '发送失败');
      }
    } finally {
      setCodeLoading(false);
    }
  };

  const onFinish = async (values: ActivationForm) => {
    if (!verified || !identityValues) return;
    setLoading(true);
    try {
      const res = await axios.post('/api/user/activate-by-identity', {
        ...values,
        universityId: identityValues.universityId,
        numberId: identityValues.numberId,
        initialPassword: identityValues.initialPassword,
        password: md5(values.password),
        confirmPassword: md5(values.confirmPassword),
        userId: verified.userId
      });
      if (res.data.code === 200) {
        message.success('激活成功，欢迎回来');
        router.push('/');
      } else {
        message.error(res.data.msg || '激活失败');
      }
    } catch {
      message.error('激活失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <SiteHeader />
      <main className="auth-page-shell">
        <section className="auth-hero-grid activation-grid">
          <div className="auth-copy-panel">
            <span className="bits-pill">ACCOUNT ACTIVATION</span>
            <Title className="bits-gradient-title">激活账号</Title>
            <Paragraph>使用学校发放账号时提供的学校、学号与初始密码完成身份验证，再补充注册所需信息，即可正常使用全部功能。</Paragraph>
            <Steps
              direction="vertical"
              size="small"
              current={step}
              items={[
                { title: '身份验证', description: '输入学校、学号、初始密码' },
                { title: '完善信息', description: '补充登录账号、邮箱、手机号等注册信息' },
              ]}
              style={{ maxWidth: 320 }}
            />
          </div>
          <Card className="bits-card auth-card" variant="borderless">
            {step === 0 ? (
              <>
                <div className="auth-card-heading">
                  <Title level={3}><IdcardOutlined /> 身份验证</Title>
                  <Paragraph>请输入学校发放账号时登记的信息</Paragraph>
                </div>
                <Form form={identityForm} layout="vertical" size="large" onFinish={verifyIdentity}>
                  <Form.Item name="universityId" label="学校" rules={[{ required: true, message: '请选择学校' }]}>
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="请选择学校"
                      options={universities.map(item => ({ value: item.universityId, label: item.universityName }))}
                    />
                  </Form.Item>
                  <Form.Item name="numberId" label="学号" rules={[{ required: true, message: '请输入学号' }]}>
                    <Input className="bits-input" prefix={<UserOutlined />} placeholder="请输入学号" onPressEnter={verifyIdentity} />
                  </Form.Item>
                  <Form.Item name="initialPassword" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }]}>
                    <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="请输入学校发放的初始密码" onPressEnter={verifyIdentity} />
                  </Form.Item>
                  <Button className="bits-primary-button auth-submit" block loading={identityLoading} icon={<CheckCircleOutlined />} onClick={verifyIdentity}>
                    验证并继续
                  </Button>
                  <div className="auth-switch-line" style={{ textAlign: 'center' }}>
                    <Button type="link" onClick={() => router.push('/login')}>返回登录</Button>
                  </div>
                </Form>
              </>
            ) : (
              <>
                <div className="auth-card-heading">
                  <Title level={3}>完善信息</Title>
                  <Paragraph>身份验证通过（{verified?.realName} · {verified?.numberId}），请补充注册所需信息</Paragraph>
                </div>
                <Form form={form} layout="vertical" onFinish={onFinish} requiredMark="optional">
                  <Form.Item name="nickName" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
                    <Input className="bits-input" prefix={<UserOutlined />} placeholder="用于显示的名称" />
                  </Form.Item>
                  <Form.Item
                    name="userAccount"
                    label="登录账号"
                    rules={[
                      { required: true, message: '请输入登录账号' },
                      { pattern: /^(?=.*[A-Za-z])[A-Za-z0-9]{3,20}$/, message: '3-20个英文字母或数字，且至少包含1个英文字母' }
                    ]}
                  >
                    <Input className="bits-input" prefix={<UserOutlined />} placeholder="3-20位，至少包含1个英文字母" maxLength={20} />
                  </Form.Item>
                  <Form.Item name="email" label="电子邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
                    <Input className="bits-input" prefix={<MailOutlined />} placeholder="用于找回密码和接收通知" />
                  </Form.Item>
                  <Form.Item name="verifyCode" label="邮箱验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                    <div className="inline-code-row">
                      <Input className="bits-input" prefix={<SafetyOutlined />} placeholder="6 位验证码" />
                      <Button className="bits-ghost-button" onClick={sendCode} disabled={countdown > 0} loading={codeLoading}>
                        {countdown > 0 ? `${countdown}s` : '获取验证码'}
                      </Button>
                    </div>
                  </Form.Item>
                  <Form.Item name="phone" label="手机号码" rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}>
                    <Input className="bits-input" prefix={<PhoneOutlined />} placeholder="请输入手机号码" />
                  </Form.Item>
                  <Form.Item name="gender" label="性别" rules={[{ required: true, message: '请选择性别' }]}>
                    <Radio.Group>
                      <Radio value={1}>男</Radio>
                      <Radio value={2}>女</Radio>
                    </Radio.Group>
                  </Form.Item>
                  <Form.Item name="password" label="设置新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少 6 位' }]}>
                    <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="设置新密码" />
                  </Form.Item>
                  <Form.Item
                    name="confirmPassword"
                    label="确认密码"
                    dependencies={['password']}
                    rules={[
                      { required: true, message: '请确认密码' },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!value || getFieldValue('password') === value) return Promise.resolve();
                          return Promise.reject(new Error('两次输入的密码不一致'));
                        }
                      })
                    ]}
                  >
                    <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="再次输入密码" />
                  </Form.Item>
                  <Button className="bits-primary-button auth-submit" htmlType="submit" block loading={loading}>
                    激活并进入官网
                  </Button>
                </Form>
              </>
            )}
          </Card>
        </section>
      </main>
    </>
  );
}
