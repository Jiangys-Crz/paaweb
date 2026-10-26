"use client";

import type { ReactNode } from 'react';
import { Button, Space, Tag, Typography } from 'antd';
import {
  ArrowLeftOutlined,
  HomeOutlined,
  LoginOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import SiteHeader from './SiteHeader';

const { Title, Paragraph, Text } = Typography;

export type ErrorStateCode =
  | '400'
  | '401'
  | '402'
  | '403'
  | '404'
  | '405'
  | '406'
  | '408'
  | '409'
  | '410'
  | '413'
  | '414'
  | '415'
  | '418'
  | '422'
  | '423'
  | '429'
  | '451'
  | '500'
  | '501'
  | '502'
  | '503'
  | '504'
  | '505'
  | 'unknown';

interface ErrorStateConfig {
  title: string;
  desc: string;
  hint: string;
  tone: 'warning' | 'danger' | 'info';
  icon: ReactNode;
  primaryText?: string;
  primaryAction?: 'home' | 'login' | 'reload';
}

const ERROR_CONFIG: Record<ErrorStateCode, ErrorStateConfig> = {
  '400': {
    title: '请求参数有误',
    desc: '当前请求没有被系统识别，请检查链接或表单内容后重试。',
    hint: 'Bad Request',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '401': {
    title: '需要登录',
    desc: '您的登录状态已失效，重新登录后即可继续访问。',
    hint: 'Unauthorized',
    tone: 'warning',
    icon: <LoginOutlined />,
    primaryText: '去登录',
    primaryAction: 'login',
  },
  '402': {
    title: '需要额外授权',
    desc: '当前资源需要额外授权或条件满足后才能访问。',
    hint: 'Payment Required',
    tone: 'warning',
    icon: <SafetyCertificateOutlined />,
  },
  '403': {
    title: '无权访问',
    desc: '当前账号没有访问此页面的权限。如需开通，请联系管理员确认权限配置。',
    hint: 'Forbidden',
    tone: 'danger',
    icon: <SafetyCertificateOutlined />,
  },
  '404': {
    title: '页面不存在',
    desc: '访问的页面可能已移动、删除，或链接地址输入有误。',
    hint: 'Not Found',
    tone: 'info',
    icon: <SearchOutlined />,
  },
  '405': {
    title: '请求方式不支持',
    desc: '当前资源不支持这种访问方式，请返回上一页重新操作。',
    hint: 'Method Not Allowed',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '406': {
    title: '无法返回可接受内容',
    desc: '服务器无法按当前请求头返回合适的内容格式。',
    hint: 'Not Acceptable',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '408': {
    title: '请求超时',
    desc: '网络响应时间过长，请稍后刷新页面再试。',
    hint: 'Request Timeout',
    tone: 'warning',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  '409': {
    title: '请求发生冲突',
    desc: '当前操作与已有数据状态冲突，请刷新后再试。',
    hint: 'Conflict',
    tone: 'warning',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  '410': {
    title: '资源已失效',
    desc: '访问的内容已经被移除或不再公开。',
    hint: 'Gone',
    tone: 'info',
    icon: <SearchOutlined />,
  },
  '413': {
    title: '提交内容过大',
    desc: '上传的数据超过系统限制，请压缩文件或减少提交内容后重试。',
    hint: 'Payload Too Large',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '414': {
    title: '链接过长',
    desc: '请求地址超过系统可处理范围，请减少查询条件后重试。',
    hint: 'URI Too Long',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '415': {
    title: '文件类型不支持',
    desc: '系统无法处理当前提交的内容格式，请更换文件或请求类型。',
    hint: 'Unsupported Media Type',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '418': {
    title: '请求被拒绝',
    desc: '系统没有接受这次请求，请返回上一页重新操作。',
    hint: "I'm a teapot",
    tone: 'info',
    icon: <WarningOutlined />,
  },
  '422': {
    title: '数据校验失败',
    desc: '提交的数据未通过校验，请检查必填项、格式和取值范围。',
    hint: 'Unprocessable Content',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '423': {
    title: '资源已锁定',
    desc: '当前资源暂时不可编辑或访问，请稍后再试。',
    hint: 'Locked',
    tone: 'warning',
    icon: <SafetyCertificateOutlined />,
  },
  '429': {
    title: '请求过于频繁',
    desc: '系统正在保护服务稳定性，请稍等片刻后再次尝试。',
    hint: 'Too Many Requests',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '451': {
    title: '资源不可用',
    desc: '当前内容因访问规则限制暂不可用。',
    hint: 'Unavailable For Legal Reasons',
    tone: 'danger',
    icon: <SafetyCertificateOutlined />,
  },
  '500': {
    title: '服务器开小差了',
    desc: '服务端处理请求时遇到异常。您可以刷新重试，或稍后再访问。',
    hint: 'Internal Server Error',
    tone: 'danger',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  '501': {
    title: '功能暂未实现',
    desc: '服务器暂不支持当前请求的功能。',
    hint: 'Not Implemented',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '502': {
    title: '网关异常',
    desc: '前端暂时无法连接到后端服务，请稍后刷新页面。',
    hint: 'Bad Gateway',
    tone: 'danger',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  '505': {
    title: '协议版本不支持',
    desc: '服务器不支持当前请求使用的 HTTP 协议版本。',
    hint: 'HTTP Version Not Supported',
    tone: 'warning',
    icon: <WarningOutlined />,
  },
  '503': {
    title: '服务暂不可用',
    desc: '系统可能正在维护或负载较高，请稍后再试。',
    hint: 'Service Unavailable',
    tone: 'warning',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  '504': {
    title: '网关响应超时',
    desc: '请求等待时间过长，请检查网络后刷新重试。',
    hint: 'Gateway Timeout',
    tone: 'warning',
    icon: <ReloadOutlined />,
    primaryText: '重新加载',
    primaryAction: 'reload',
  },
  unknown: {
    title: '访问遇到问题',
    desc: '系统没有识别到具体错误类型，请返回首页或刷新页面重试。',
    hint: 'Unexpected Error',
    tone: 'info',
    icon: <WarningOutlined />,
  },
};

interface ErrorStatePageProps {
  code?: string;
  reset?: () => void;
  message?: string;
}

export const getErrorStateConfig = (code?: string) => {
  const key = String(code || 'unknown') as ErrorStateCode;
  return ERROR_CONFIG[key] || ERROR_CONFIG.unknown;
};

export default function ErrorStatePage({ code = 'unknown', reset, message }: ErrorStatePageProps) {
  const router = useRouter();
  const config = getErrorStateConfig(code);
  const displayCode = code === 'unknown' ? 'Error' : code;

  const handlePrimary = () => {
    if (config.primaryAction === 'login') {
      router.push('/login');
      return;
    }
    if (config.primaryAction === 'reload') {
      if (reset) reset();
      else window.location.reload();
      return;
    }
    router.push('/');
  };

  return (
    <main className="error-page-shell">
      <SiteHeader />
      <section className="error-page-stage">
        <div className={`error-page-panel error-page-panel-${config.tone}`}>
          <div className="error-page-visual" aria-hidden="true">
            <div className="error-page-code">{displayCode}</div>
            <div className="error-page-icon">{config.icon}</div>
          </div>
          <div className="error-page-content">
            <Tag className="error-page-tag">{config.hint}</Tag>
            <Title level={1}>{config.title}</Title>
            <Paragraph>{config.desc}</Paragraph>
            {message && <Text className="error-page-message">{message}</Text>}
            <Space wrap size={12} className="error-page-actions">
              <Button className="bits-primary-button" icon={config.primaryAction === 'login' ? <LoginOutlined /> : config.primaryAction === 'reload' ? <ReloadOutlined /> : <HomeOutlined />} onClick={handlePrimary}>
                {config.primaryText || '返回首页'}
              </Button>
              <Button className="bits-ghost-button" icon={<ArrowLeftOutlined />} onClick={() => router.back()}>
                返回上一页
              </Button>
              <Button className="bits-ghost-button" icon={<HomeOutlined />} onClick={() => router.push('/')}>
                返回首页
              </Button>
            </Space>
          </div>
        </div>
      </section>
    </main>
  );
}
