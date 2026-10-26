import ProfilePageClient from './ProfilePageClient';

interface ProfilePageProps {
  params: Promise<{ userId: string }>;
}

export default async function ProfilePage({ params }: ProfilePageProps) {
  const { userId } = await params;
  return <ProfilePageClient userId={userId} />;
}
