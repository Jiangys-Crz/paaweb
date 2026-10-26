import { Metadata } from 'next';
import IntroPageClient from './IntroPageClient';

export const metadata: Metadata = {
  title: '协会简介',
};

export default function IntroMainPage() {
  return <IntroPageClient category="association" />;
}
