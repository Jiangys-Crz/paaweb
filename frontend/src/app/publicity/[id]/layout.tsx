import { DetailSeoBlock } from '@/app/utils/detailSeo';

export default async function PublicityDetailLayout({ children, params }: { children: React.ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <DetailSeoBlock
        config={{
          endpoint: `/notice/detail/${id}?incrementView=false`,
          fallbackTitle: '公示详情',
          titleKeys: ['title'],
          contentKeys: ['content'],
          typeLabel: '公示',
        }}
      />
      {children}
    </>
  );
}
