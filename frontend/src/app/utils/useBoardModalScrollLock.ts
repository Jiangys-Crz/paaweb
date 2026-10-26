'use client';

import { useLayoutEffect } from 'react';

/** Keep the background at its current position until the last board dialog closes. */
export function useBoardModalScrollLock(open: boolean): void {
  useLayoutEffect(() => {
    if (!open) return;
    const scrollX = window.scrollX;
    const scrollY = window.scrollY;
    const html = document.documentElement;
    const body = document.body;
    const saved = [
      [html, ['overflow', 'overscroll-behavior']],
      [body, ['position', 'top', 'left', 'right']]
    ] as const;
    const previous = saved.flatMap(([element, properties]) => properties.map(property => ({
      element,
      property,
      value: element.style.getPropertyValue(property),
      priority: element.style.getPropertyPriority(property)
    })));

    // Leave body's overflow and width to Ant Design's own dialog lock.
    html.style.setProperty('overflow', 'hidden');
    html.style.setProperty('overscroll-behavior', 'none');
    body.style.setProperty('position', 'fixed');
    body.style.setProperty('top', `${-scrollY}px`);
    body.style.setProperty('left', `${-scrollX}px`);
    body.style.setProperty('right', '0');

    return () => {
      previous.forEach(({ element, property, value, priority }) => {
        if (value) element.style.setProperty(property, value, priority);
        else element.style.removeProperty(property);
      });
      window.scrollTo(scrollX, scrollY);
    };
  }, [open]);
}
