import { DetailSeoBlock } from '@/app/utils/detailSeo';

export default async function NewsDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock
        config={{
          endpoint: `/notice/detail/${id}?incrementView=false`,
          fallbackTitle: '新闻详情',
          titleKeys: ['title'],
          contentKeys: ['content'],
          typeLabel: '新闻',
        }}
      />
      {children}
    </>
  );
}
