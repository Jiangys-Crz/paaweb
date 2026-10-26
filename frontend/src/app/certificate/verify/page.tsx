import type { Metadata } from 'next';
import CertificateVerifyClient from './CertificateVerifyClient';

export const metadata: Metadata = {
  title: '电子证书真伪查询',
  description: '查询PAA 竞赛管理平台电子证书真伪与签发信息。',
};

export default function Page() {
  return <CertificateVerifyClient />;
}
