import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';
import MarkdownIt from 'markdown-it';
import katex from 'katex';
import { XSSPlugin } from 'md-editor-rt';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
async function loadSource(relativePath) {
  const filename = path.join(root, relativePath);
  const output = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    fileName: filename,
    compilerOptions: { module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2020 },
  }).outputText.replace(/from\s+(['"])([^'"]+)\1/g, (_match, _quote, specifier) => `from '${pathToFileURL(require.resolve(specifier)).href}'`);
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
}

const { default: MarkdownContent } = await loadSource('src/app/components/content/MarkdownContent.tsx');
const render = content => renderToStaticMarkup(React.createElement(MarkdownContent, { content }));
const malicious = [
  '<svg><a><animate attributeName="href" values="javascript:window.__SECURITY_PROBE__=1"/><text>probe</text></a></svg>',
  '<svg><set attributeName="href" to="javascript:window.__SECURITY_PROBE__=1"/></svg>',
  '<img src="/safe.png" onerror="window.__SECURITY_PROBE__=1">',
  '<a href="javascript:window.__SECURITY_PROBE__=1" style="position:fixed;inset:0">probe</a>',
  '<iframe srcdoc="probe"></iframe><object data="/probe"></object><base href="https://audit.invalid">',
  '<script>window.__SECURITY_PROBE__=1</script><style>body{display:none}</style>',
];
for (const payload of malicious) {
  const html = render(payload);
  assert.doesNotMatch(html, /<(?:svg|animate|set|iframe|object|base|script|style)\b/i);
  assert.doesNotMatch(html, /\bonerror=|href="javascript:|style="position:fixed/i);
}
const safe = render('## 标题\n\n**加粗** [链接](https://example.com)\n\n| 项目 | 数量 |\n| --- | --- |\n| 算法 | 2 |\n\n![图片](https://example.com/safe.png)\n\n$E=mc^2$\n\n$$\\frac{1}{2}$$');
assert.match(safe, /<h2>标题<\/h2>/);
assert.match(safe, /<strong>加粗<\/strong>/);
assert.match(safe, /<table>/);
assert.match(safe, /href="https:\/\/example.com"/);
assert.match(safe, /src="https:\/\/example.com\/safe.png"/);
assert.match(safe, /class="katex"/);
assert.match(safe, /class="katex-display"/);
assert.match(safe, /<math /);
assert.doesNotMatch(render('$\\href{javascript:window.__SECURITY_PROBE__=1}{click}$'), /href="javascript:/i);

const { editorXssOptions, editorKatexConfig } = await loadSource('src/app/components/content/SecureMarkdownEditor.ts');
const editor = new MarkdownIt({ html: true }).use(XSSPlugin, editorXssOptions);
for (const payload of malicious) {
  const html = editor.render(payload);
  assert.doesNotMatch(html, /<(?:svg|animate|set|iframe|object|base|script|style)\b/i);
  assert.doesNotMatch(html, /\bonerror=|href="javascript:|style="position:fixed/i);
}
assert.match(katex.renderToString('E=mc^2', editorKatexConfig({ throwOnError: false })), /class="katex"/);
assert.match(katex.renderToString('\\frac{1}{2}', editorKatexConfig({ displayMode: true, throwOnError: false })), /class="katex-display"/);
assert.doesNotMatch(katex.renderToString('\\href{javascript:window.__SECURITY_PROBE__=1}{click}', editorKatexConfig({ trust: true, throwOnError: false })), /href="javascript:/i);

const { contentSecurityPolicy } = await loadSource('src/security/contentSecurityPolicy.ts');
const policy = contentSecurityPolicy('test-nonce', false);
const scripts = policy.split('; ').find(directive => directive.startsWith('script-src'));
assert.match(scripts, /'nonce-test-nonce'/);
assert.doesNotMatch(scripts, /unsafe-inline|unsafe-eval/);
for (const directive of ["object-src 'none'", "base-uri 'self'", "frame-ancestors 'none'", "frame-src 'none'"]) assert.ok(policy.includes(directive));
assert.ok(policy.includes("style-src 'self' 'unsafe-inline'"));

const { userCardHtml } = await loadSource('src/types/user.ts');
const card = userCardHtml({ userId: 123, userName: '<svg onload="probe">', realName: '测试姓名', numberId: 'PRIVATE_NUMBER', email: 'PRIVATE_EMAIL' });
assert.match(card, /&lt;svg/);
assert.doesNotMatch(card, /<svg|PRIVATE_NUMBER|PRIVATE_EMAIL|测试姓名/);
assert.equal(userCardHtml({ userId: 'javascript:probe', userName: 'probe' }), '');
console.log('Security regression passed: Markdown/editor SVG, event and URL filtering; safe tables/images/math; CSP; public user cards.');
