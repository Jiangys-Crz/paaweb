/** Optional school OJ integration. This public value is embedded at build time. */
export function schoolOjProblemUrl(problemId: string): string {
  const base = process.env.NEXT_PUBLIC_SCHOOL_OJ_URL?.trim();
  if (!base) return '';
  try {
    const url = new URL(base);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return '';
    return `${url.href.replace(/\/$/, '')}/p/${encodeURIComponent(problemId)}`;
  } catch {
    return '';
  }
}
