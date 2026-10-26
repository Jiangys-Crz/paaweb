import { Metadata } from 'next';
import HonorWallClient from './HonorWallClient';

export const metadata: Metadata = {
  title: '荣誉墙',
};

export default function HonorWallPage() {
  return <HonorWallClient />;
}
