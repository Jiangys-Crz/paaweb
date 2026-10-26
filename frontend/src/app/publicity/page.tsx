import { Metadata } from 'next';
import NoticeListClient from '../notice/NoticeListClient';

export const metadata: Metadata = {
  title: { absolute: '公示' },
};

export default function PublicityPage() {
  return <NoticeListClient type="publicity" />;
}
