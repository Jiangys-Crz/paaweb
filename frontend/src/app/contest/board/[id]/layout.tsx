import { DetailSeoBlock, generateDetailMetadata } from '@/app/utils/detailSeo';

const getConfig = (id: string) => ({
  endpoint: `/leaderboard/info/${id}`,
  fallbackTitle: '比赛榜单',
  titleKeys: ['name', 'title'],
  contentKeys: ['description', 'content'],
  typeLabel: '外榜',
});

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return generateDetailMetadata(getConfig(id));
}

export default async function ContestBoardDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock config={getConfig(id)} />
      {children}
    </>
  );
}
