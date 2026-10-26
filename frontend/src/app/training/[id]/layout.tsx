import { DetailSeoBlock, generateDetailMetadata } from '@/app/utils/detailSeo';

const getConfig = (id: string) => ({
  endpoint: `/training/detail/${id}`,
  fallbackTitle: '培训详情',
  titleKeys: ['name', 'title'],
  contentKeys: ['description', 'content'],
  typeLabel: '培训',
});

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return generateDetailMetadata(getConfig(id));
}

export default async function TrainingDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock config={getConfig(id)} />
      {children}
    </>
  );
}
