import { Metadata } from 'next';
import IntroPageClient from '../../intro/IntroPageClient';

export const metadata: Metadata = {
  title: '章程与制度',
};

export default function ContestRulePage() {
  return <IntroPageClient category="rules" />;
}
