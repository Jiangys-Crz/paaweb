import { Metadata } from 'next';
import NoticeListClient from './NoticeListClient';

export const metadata: Metadata = {
  title: { absolute: '通知' },
};

export default function NoticePage() {
  return <NoticeListClient type="notice" />;
}
