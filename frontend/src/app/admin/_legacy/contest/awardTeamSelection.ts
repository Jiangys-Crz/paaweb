/** Replace selections only for the currently visible result set, retaining hidden or unavailable IDs. */
export function mergeVisibleAwardSelection(previous: readonly number[], visible: readonly number[], selected: readonly number[]): number[] {
  const visibleSet = new Set(visible);
  const chosen = new Set(selected);
  return [...previous.filter(id => !visibleSet.has(id) || chosen.has(id)), ...visible.filter(id => chosen.has(id) && !previous.includes(id))];
}
