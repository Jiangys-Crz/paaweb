import type { Metadata } from 'next';

type DetailRecord = Record<string, unknown>;

export type DetailSeoConfig = {
  endpoint: string;
  fallbackTitle: string;
  titleKeys: string[];
  titleFormatter?: (detail: DetailRecord | null) => string;
  contentKeys: string[];
  typeLabel: string;
};

const getApiBaseUrl = () => process.env.API_URL || 'http://localhost:8080';

const asText = (value: unknown) => (typeof value === 'string' ? value : '');

export const stripMarkdown = (value?: string) => (
  value || ''
).replace(/```[\s\S]*?```/g, ' ')
  .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
  .replace(/\[[^\]]*]\([^)]*\)/g, ' ')
  .replace(/[#*_`>\-[\]()|]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

export async function fetchDetailForSeo(config: DetailSeoConfig): Promise<DetailRecord | null> {
  try {
    const res = await fetch(`${getApiBaseUrl()}${config.endpoint}`, {
      cache: 'no-store',
    });
    const json = await res.json();
    return json && typeof json.data === 'object' && json.data !== null ? json.data : null;
  } catch (error) {
    console.error('SEO detail fetch failed:', config.endpoint, error);
    return null;
  }
}

export function pickDetailTitle(detail: DetailRecord | null, config: DetailSeoConfig) {
  const formatted = config.titleFormatter?.(detail);
  if (formatted?.trim()) return formatted.trim();
  for (const key of config.titleKeys) {
    const value = asText(detail?.[key]);
    if (value.trim()) return value.trim();
  }
  return config.fallbackTitle;
}

export function pickDetailContent(detail: DetailRecord | null, config: DetailSeoConfig) {
  for (const key of config.contentKeys) {
    const value = asText(detail?.[key]);
    if (value.trim()) return stripMarkdown(value).slice(0, 300);
  }
  return '';
}

export async function generateDetailMetadata(config: DetailSeoConfig): Promise<Metadata> {
  const detail = await fetchDetailForSeo(config);
  const title = pickDetailTitle(detail, config);
  const description = pickDetailContent(detail, config) || `${config.typeLabel}：${title}`;

  return {
    title: { absolute: title },
    description,
    openGraph: {
      title,
      description,
      type: 'article',
    },
  };
}

export async function DetailSeoBlock({ config }: { config: DetailSeoConfig }) {
  const detail = await fetchDetailForSeo(config);
  const title = pickDetailTitle(detail, config);
  const content = pickDetailContent(detail, config);

  return (
    <article className="seo-detail-content" aria-label={`${config.typeLabel}SEO内容`}>
      <h1>{title}</h1>
      {content && <p>{content}</p>}
    </article>
  );
}
