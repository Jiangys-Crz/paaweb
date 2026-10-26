export function codeLanguageId(language: string): string {
  const value = language.trim().toLowerCase();
  if (/c\+\+|g\+\+|clang\+\+|^cpp|^cc\./.test(value)) return 'cpp';
  if (/^csharp|^cs\b|c#/.test(value)) return 'csharp';
  if (/^c\b|^gcc|^clang/.test(value)) return 'c';
  if (/python|pypy|^py[\d.]/.test(value)) return 'python';
  if (/^java(?!script)/.test(value)) return 'java';
  if (/javascript|node|^js\b/.test(value)) return 'javascript';
  if (/typescript|^ts\b/.test(value)) return 'typescript';
  if (/^go/.test(value)) return 'go';
  if (/^rust|^rs\b/.test(value)) return 'rust';
  return value;
}

export async function highlightSubmissionCode(code: string, language: string): Promise<string> {
  const { default: hljs } = await import('highlight.js/lib/common');
  const id = codeLanguageId(language);
  return hljs.getLanguage(id)
    ? hljs.highlight(code, { language: id, ignoreIllegals: true }).value
    : hljs.highlightAuto(code, ['cpp', 'python', 'java', 'javascript', 'go', 'rust', 'csharp']).value;
}

export async function copySubmissionCode(code: string): Promise<void> {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(code);
      return;
    } catch {
      // HTTP deployments and restricted clipboard permissions use selection copy.
    }
  }
  const focused = document.activeElement;
  const input = document.createElement('textarea');
  input.value = code;
  input.readOnly = true;
  input.style.position = 'fixed';
  input.style.left = '-9999px';
  document.body.appendChild(input);
  try {
    input.select();
    if (!document.execCommand('copy')) throw new Error('Copy failed. Select the code and copy it manually.');
  } finally {
    input.remove();
    if (focused instanceof HTMLElement) focused.focus({ preventScroll: true });
  }
}
