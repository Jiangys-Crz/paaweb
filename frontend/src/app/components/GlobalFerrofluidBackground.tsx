"use client";

import { usePathname } from 'next/navigation';
import Ferrofluid from './Ferrofluid';

const GlobalFerrofluidBackground = () => {
  const pathname = usePathname();
  const className = pathname === '/'
    ? 'global-ferrofluid-background global-ferrofluid-background-home'
    : 'global-ferrofluid-background';

  return (
    <div className={className} aria-hidden="true">
      <Ferrofluid
        colors={['#7ddcff', '#a7f3e5', '#c9d5ff', '#ffffff']}
        speed={0.28}
        scale={1.85}
        turbulence={0.72}
        fluidity={0.18}
        rimWidth={0.16}
        sharpness={2.15}
        shimmer={0.95}
        glow={1.25}
        flowDirection="down"
        opacity={0.44}
        mouseInteraction
        mouseStrength={0.45}
        mouseRadius={0.4}
        mouseDampening={0.22}
        mixBlendMode="multiply"
      />
    </div>
  );
};

export default GlobalFerrofluidBackground;
