import { Metadata } from 'next';
import NoticeDetailClient from '../../notice/NoticeDetailClient';

interface PublicityDetailPageProps {
  params: Promise<{ id: string }>;
}

export async function generateMetadata({ params }: PublicityDetailPageProps): Promise<Metadata> {
  const { id } = await params;
  try {
    const baseUrl = process.env.API_URL || 'http://localhost:8080';
    const res = await fetch(`${baseUrl}/notice/detail/${id}?incrementView=false`, { cache: 'no-store' });
    const json = await res.json();
    if (json && json.data) {
      return { title: { absolute: json.data.title } };
    }
  } catch (e) {
    console.error(e);
  }
  return { title: { absolute: '公示详情' } };
}

export default async function PublicityDetailPage({ params }: PublicityDetailPageProps) {
  const { id } = await params;
  return <NoticeDetailClient id={id} />;
}
