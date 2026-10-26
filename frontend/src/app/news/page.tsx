import { Metadata } from 'next';
import NoticeListClient from '../notice/NoticeListClient';

export const metadata: Metadata = {
  title: { absolute: '新闻' },
};

export default function NewsPage() {
  return <NoticeListClient type="news" />;
}
