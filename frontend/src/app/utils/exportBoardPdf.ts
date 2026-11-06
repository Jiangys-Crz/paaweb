/** Export the full, unscaled board on one custom-size PDF page. */
export async function exportBoardPdf(element: HTMLElement, title: string): Promise<void> {
  const [{ toCanvas }, { jsPDF }] = await Promise.all([
    import('html-to-image'),
    import('jspdf')
  ]);
  // The centered viewport clips a scaled desktop layout. Capturing it also
  // copies its resolved auto margins, shifting the board outside the image.
  // Export the inner board at its natural size and override only the clone.
  const content = element.matches('.rank-container')
    ? element : element.querySelector<HTMLElement>('.rank-container') || element;
  await document.fonts.ready;
  await Promise.all(Array.from(content.querySelectorAll('img')).map(image => {
    // Export includes off-screen rows whose organization watermarks load lazily.
    image.loading = 'eager';
    return image.decode().catch(() => undefined);
  }));
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));

  const width = Math.ceil(Math.max(content.offsetWidth, content.scrollWidth));
  const height = Math.ceil(Math.max(content.offsetHeight, content.scrollHeight));
  if (!width || !height) throw new Error('The leaderboard is not ready. Please try again.');

  // Keep a long board within browser canvas limits without cutting off rows.
  const pixelRatio = Math.min(2, 16384 / Math.max(width, height), Math.sqrt(16_000_000 / (width * height)));
  const background = getComputedStyle(document.body);
  const canvas = await toCanvas(content, {
    width,
    height,
    pixelRatio,
    skipAutoScale: true,
    backgroundColor: background.backgroundColor,
    style: {
      transform: 'none', transformOrigin: 'top left',
      margin: '0', marginInline: '0', maxWidth: 'none', maxHeight: 'none',
      overflow: 'visible',
      backgroundImage: background.backgroundImage, backgroundColor: background.backgroundColor
    }
  });
  if (!canvas.width || !canvas.height) throw new Error('Unable to generate the leaderboard image');

  // PDF pages have a maximum dimension; scale both sides together to preserve
  // the entire image and its aspect ratio, rather than splitting or cropping it.
  let pageWidth = 595.28;
  let pageHeight = pageWidth * canvas.height / canvas.width;
  const pageScale = Math.min(1, 14400 / Math.max(pageWidth, pageHeight));
  pageWidth *= pageScale;
  pageHeight *= pageScale;
  const pdf = new jsPDF({
    orientation: pageHeight >= pageWidth ? 'portrait' : 'landscape',
    unit: 'pt',
    format: [pageWidth, pageHeight],
    compress: true
  });
  pdf.setProperties({ title: `${title} - Leaderboard` });
  pdf.addImage(canvas, 'PNG', 0, 0, pageWidth, pageHeight, undefined, 'FAST');
  const filename = title.replace(/[\\/:*?"<>|]/g, '_').trim() || 'Contest';
  await pdf.save(`Contest_Rank_${filename}.pdf`, { returnPromise: true });
  canvas.width = 0;
  canvas.height = 0;
}
