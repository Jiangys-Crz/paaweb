'use client';

import { useLayoutEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';

export function useViewportSize() {
  const [size, setSize] = useState({ width: 1280, height: 900 });
  useLayoutEffect(() => {
    const update = () => setSize({ width: window.innerWidth, height: window.innerHeight });
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);
  return size;
}

export function desktopModalStyle(width: number, viewport: { width: number; height: number }): CSSProperties {
  const scale = Math.min(1, Math.max(1, viewport.width - 32) / width);
  return {
    '--board-modal-width': `${width}px`,
    '--board-modal-scale': scale,
    '--board-modal-body-height': `${Math.max(160, (viewport.height - 64) / scale - 112)}px`
  } as CSSProperties;
}

export function useBoardDesktopLayout(contentKey: string) {
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [fit, setFit] = useState<{ width: number; scale: number; height?: number }>({ width: 1280, scale: 1 });

  useLayoutEffect(() => {
    const viewport = viewportRef.current;
    const content = contentRef.current;
    if (!viewport || !content) return;
    let active = true;
    let frame = 0;
    const update = () => {
      if (!active) return;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const currentScale = content.getBoundingClientRect().width / content.offsetWidth || 1;
        let width = Math.max(1280, viewport.clientWidth);
        const title = content.querySelector('.rank-page-title h3');
        if (title) {
          const range = document.createRange();
          range.selectNodeContents(title);
          width = Math.max(width, range.getBoundingClientRect().width / currentScale + 96);
        }
        content.querySelectorAll<HTMLElement>('.rank-page-filters, .board-submission-filters, .rank-page-timeline-bottom').forEach(row => {
          width = Math.max(width, row.scrollWidth + 96);
        });
        width = Math.ceil(width);
        const scale = Math.min(1, Math.max(1, viewport.clientWidth) / width);
        const height = Math.ceil(content.offsetHeight * scale);
        setFit(previous => previous.width === width && Math.abs(previous.scale - scale) < 0.0001 && previous.height === height
          ? previous : { width, scale, height });
      });
    };
    const observer = new ResizeObserver(update);
    observer.observe(viewport);
    observer.observe(content);
    update();
    void document.fonts.ready.then(update);
    return () => {
      active = false;
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [contentKey]);
  return { viewportRef, contentRef, fit };
}
