import { DetailSeoBlock, generateDetailMetadata } from '@/app/utils/detailSeo';

const getConfig = (id: string) => ({
  endpoint: `/contest/category/detail/${id}`,
  fallbackTitle: '赛事介绍',
  titleKeys: ['name', 'title'],
  contentKeys: ['intro', 'description', 'content'],
  typeLabel: '比赛',
});

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return generateDetailMetadata(getConfig(id));
}

export default async function ContestIntroDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock config={getConfig(id)} />
      {children}
    </>
  );
}
