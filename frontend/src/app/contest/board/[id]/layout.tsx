import { DetailSeoBlock, generateDetailMetadata, type DetailSeoConfig } from '@/app/utils/detailSeo';
import { formatBoardTitle } from '@/app/utils/boardTitles';

const getConfig = (id: string): DetailSeoConfig => ({
  endpoint: `/leaderboard/info/${id}`,
  fallbackTitle: '比赛榜单',
  titleKeys: ['name', 'title'],
  titleFormatter: detail => typeof detail?.eventName === 'string' ? formatBoardTitle({
    title: detail.eventName,
    sessionName: typeof detail.name === 'string' ? detail.name : undefined,
  }) : '',
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
