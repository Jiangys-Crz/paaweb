/** Accept both current names and legacy JSON/Java list strings from signup data. */
export function advisorNames(value: unknown): string[] {
  if (Array.isArray(value)) return value.flatMap(advisorNames);
  if (value == null) return [];
  let text = String(value).trim();
  if (text.startsWith('[') && text.endsWith(']')) {
    try {
      const parsed: unknown = JSON.parse(text);
      if (Array.isArray(parsed)) return parsed.flatMap(advisorNames);
    } catch { /* Older servers stored List.toString(), which is not JSON. */ }
    text = text.slice(1, -1).trim();
  }
  return text.split(/[、,，;；\r\n]+/).map(name => {
    const trimmed = name.trim();
    return ((trimmed.startsWith('"') && trimmed.endsWith('"')) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) ? trimmed.slice(1, -1).trim() : trimmed;
  }).filter(Boolean);
}

export const formatAdvisor = (value: unknown, fallback = '') => advisorNames(value).join('、') || fallback;
