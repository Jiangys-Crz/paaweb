import { Metadata } from 'next';
import IntroPageClient from '../intro/IntroPageClient';

export const metadata: Metadata = {
  title: { absolute: '协会干部' },
};

export default function MemberPage() {
  return <IntroPageClient category="association_member" />;
}
