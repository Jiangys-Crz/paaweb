import type { RankItem } from '@/types/leaderboard';

type NameRow = Pick<RankItem, 'name' | 'organization' | 'isStar' | 'isGirl'> & { englishName?: string };
export const DEFAULT_RESOLVER_NAME_TEMPLATE = '{org.name}: {team.name}';

export function decorateResolverTeamName(row: NameRow): string {
  return `${row.isStar ? '⭐ ' : ''}${row.isGirl ? '👧 ' : ''}${row.name}`;
}

/** Plain text tokens only: substitutions are not evaluated or parsed as markup. */
export function formatResolverName(row: NameRow, template = DEFAULT_RESOLVER_NAME_TEMPLATE): string {
  const values: Record<string, string> = {
    'org.name': row.organization || '',
    'org.formal_name': row.organization || '',
    'team.name': decorateResolverTeamName(row),
    'team.display_name': decorateResolverTeamName(row),
    'team.english_name': row.englishName || '',
  };
  const result = template.replace(/\{([^{}]+)\}/g, (token, key: string) => values[key] ?? token);
  return result.trim() || decorateResolverTeamName(row);
}

export function resolverProblemLabel(problemId: string, status?: { status: string; tryCount?: number; score?: number; time?: number }) {
  if (!status || status.status === 'NONE') return { attempted: false, label: problemId, attempts: 0, minutes: '' };
  const attempts = Math.max(0, status.tryCount || 0) + (status.status === 'PENDING' ? Math.max(0, status.score || 0) : 0);
  const minutes = `${Math.max(0, Math.floor(status.time || 0))}'`;
  return { attempted: true, label: `${attempts} ${minutes}`, attempts, minutes };
}
