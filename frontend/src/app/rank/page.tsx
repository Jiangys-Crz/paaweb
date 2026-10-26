import { Metadata } from 'next';
import RankPageClient from './RankPageClient';

export const metadata: Metadata = {
  title: '积分排行榜',
};

export default function RankPage() {
  return <RankPageClient />;
}
