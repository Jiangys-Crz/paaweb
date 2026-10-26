import { DetailSeoBlock, generateDetailMetadata } from '@/app/utils/detailSeo';

const getConfig = (id: string) => ({
  endpoint: `/event/detail/${id}`,
  fallbackTitle: '比赛详情',
  titleKeys: ['name', 'title'],
  contentKeys: ['description', 'content'],
  typeLabel: '比赛',
});

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return generateDetailMetadata(getConfig(id));
}

export default async function ContestSignupDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock config={getConfig(id)} />
      {children}
    </>
  );
}
