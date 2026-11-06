'use client';

import { useEffect, useRef } from 'react';

/** Resolver's first pause, with its bundled artwork and original attribution. */
export default function ResolverSplash({ title, subtitle, pending, cover }: { title: string; subtitle?: string; pending: number; cover?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    let active = true;
    const identity = new Image(), tools = new Image();
    const draw = () => {
      if (!active) return;
      const width = canvas.clientWidth, height = canvas.clientHeight;
      if (!width || !height) return;
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
      const context = canvas.getContext('2d');
      if (!context) return;
      context.scale(ratio, ratio);
      context.fillStyle = '#000'; context.fillRect(0, 0, width, height);
      context.fillStyle = '#fff';
      const margin = Math.floor(height / 20), unit = height * 72 / 96 / 10;
      const font = (size: number, bold: boolean) => { context.font = `${bold ? 700 : 400} ${size}px ResolverReference,ResolverFont,sans-serif`; };
      const centered = (text: string, y: number) => context.fillText(text, (width - context.measureText(text).width) / 2, y);
      // Measure each title from its preferred size, so resizing never compounds a previous scale.
      const fitFont = (text: string, preferred: number) => {
        font(preferred, true);
        const size = preferred * Math.min(1, (width - margin * 2) / Math.max(1, context.measureText(text).width));
        font(size, true);
        return size;
      };
      const titleSize = fitFont(title, unit * 1.25);
      let y = margin;
      centered(title, y + titleSize * .93);
      y += titleSize * 1.16;
      if (subtitle?.trim()) {
        y += height * .012;
        const subtitleSize = fitFont(subtitle.trim(), unit * .48);
        centered(subtitle.trim(), y + subtitleSize * .93);
        y += subtitleSize * 1.16;
      }
      y += height * .012;
      font(unit * .225, true);
      const pendingText = `${pending} pending submissions`;
      const pendingBaseline = y + unit * .225 * 1.16;
      centered(pendingText, pendingBaseline);
      const top = pendingBaseline + (context.measureText(pendingText).actualBoundingBoxDescent ?? unit * .225 * .25);
      const attrHeight = unit * .3 * 1.16;
      const pad = Math.floor(attrHeight / 3);
      const bottom = height - margin - attrHeight * 5.5 - pad * 2;
      const imageGap = Math.max(height / 50, unit * .15);
      const imageTop = top + imageGap;
      const imageBottom = bottom - unit * .3 * .93 - pad - imageGap;
      const availableHeight = Math.max(0, imageBottom - imageTop);
      if (identity.complete && identity.naturalWidth) {
        // Reserve the title/pending and Resolver/attribution areas before sizing the image.
        const scale = Math.min(width * .8 / identity.naturalWidth, height * .5 / identity.naturalHeight, availableHeight / identity.naturalHeight);
        const imageWidth = identity.naturalWidth * scale, imageHeight = identity.naturalHeight * scale;
        if (scale > 0) {
          context.save();
          context.beginPath(); context.rect(0, imageTop, width, availableHeight); context.clip();
          context.drawImage(identity, (width - imageWidth) / 2, imageTop + (availableHeight - imageHeight) / 2, imageWidth, imageHeight);
          context.restore();
        }
      }
      const label = (text: string, x: number, baseline: number, color: string, align: 'left' | 'right' | 'center', size: number, bold: boolean) => {
        font(size, bold);
        const textWidth = context.measureText(text).width;
        const left = align === 'right' ? x - textWidth : align === 'center' ? x - textWidth / 2 : x;
        context.fillStyle = color; context.beginPath(); context.roundRect(left - pad, baseline - size * .93 - pad, textWidth + pad * 2, size * 1.16 + pad * 2, 5); context.fill();
        context.fillStyle = '#fff'; context.fillText(text, left, baseline);
      };
      label('ICPC Resolver', width / 2, bottom, '#244087', 'center', unit * .3, false);
      y = bottom + attrHeight * 2 + pad;
      let logoWidth = 0;
      if (tools.complete && tools.naturalWidth) {
        const scale = Math.min(width * .11 / tools.naturalWidth, height * .11 / tools.naturalHeight);
        logoWidth = tools.naturalWidth * scale;
        context.drawImage(tools, (width - logoWidth) / 2, height - margin - tools.naturalHeight * scale, logoWidth, tools.naturalHeight * scale);
      }
      const left = width / 2 - logoWidth / 2 - margin, right = width / 2 + logoWidth / 2 + margin;
      label('Concept by', left, y, '#007e00', 'right', unit * .225, true);
      label('Implementation by', right, y, '#007e00', 'left', unit * .225, true);
      y += attrHeight * 1.5 + pad;
      font(unit * .3, false); context.fillStyle = '#fff';
      context.fillText('Fredrik Niemelä', left - context.measureText('Fredrik Niemelä').width, y); context.fillText('Tim deBoer', right, y);
      y += attrHeight; context.fillText('Mattias de Zalenski', left - context.measureText('Mattias de Zalenski').width, y);
      font(unit * .225, true); context.fillText('Red Hat', right, y);
      y += attrHeight; context.fillText('KTH Royal Institute of Technology', left - context.measureText('KTH Royal Institute of Technology').width, y);
      context.fillStyle = '#404040'; const version = '2.7.1354'; context.fillText(version, width - margin - context.measureText(version).width, height - margin);
    };
    identity.onload = draw; tools.onload = draw;
    // Resolver reads contest/logo for this image slot, then falls back to images/id.png.
    let fallback = !cover;
    identity.onerror = () => {
      if (!fallback) { fallback = true; identity.src = '/resolver/id.png'; }
      else draw();
    };
    identity.src = cover || '/resolver/id.png'; tools.src = '/resolver/logo.png';
    const observer = new ResizeObserver(draw); observer.observe(canvas); draw();
    void document.fonts.ready.then(draw);
    return () => { active = false; observer.disconnect(); identity.onload = null; identity.onerror = null; tools.onload = null; };
  }, [title, subtitle, pending, cover]);
  return <canvas ref={ref} className="resolver-splash" aria-label={`${title}${subtitle ? ` - ${subtitle}` : ''}, ${pending} pending submissions, ICPC Resolver`} />;
}
