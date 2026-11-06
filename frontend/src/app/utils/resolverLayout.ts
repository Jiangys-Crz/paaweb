/** Static scoreboard geometry from ICPC Tools 2.7.1354 (12 teams per screen). */
export function getResolverLayout(viewportHeight: number, headerHeight: number, viewportWidth = 0, problemCount = 0) {
  const visibleRows = 12;
  const rowHeight = Math.max(24, (viewportHeight - headerHeight) / visibleRows);
  // ResolverUI returns no title and disables the clock; viewportHeight is the whole screen.
  const presentationHeight = viewportHeight;
  const unit = presentationHeight / visibleRows * 36 * .95 / 96;
  const rowFontSize = unit * 1.25;
  let statusFontSize = unit * .7;
  let problemFontSize = unit * .5;
  const rankWidth = 8 + rowFontSize * 1.67; // "199" plus 8px; use actual font metrics when available.
  const logoWidth = rowHeight;
  let cubeWidth = Math.floor((rowHeight / 1.8 - 5) * 10);
  // This reproduces the native loop, including its 5px gutter and lower bound.
  while (problemCount > 0 && cubeWidth > 30 && (cubeWidth + 5) * problemCount > viewportWidth - rowHeight * 3 - 70) cubeWidth -= 2;
  // Two-part counts/minutes need to remain readable in a narrow app pane.
  // Preserve native sizing on projectors, and fit the longest usual 2+3 digit label.
  if (viewportWidth > 0 && problemCount > 0) {
    statusFontSize = Math.min(statusFontSize, Math.max(7, Math.max(30, cubeWidth) * .23));
    problemFontSize = Math.min(problemFontSize, statusFontSize);
  }
  return { visibleRows, rowHeight, rowFontSize, statusFontSize, problemFontSize, rankWidth, logoWidth,
    cubeWidth: Math.max(30, cubeWidth), cubeHeight: Math.max(0, Math.floor(rowHeight / 2.5) - 5), cubeTop: Math.floor(rowHeight * .6) + 1 };
}
