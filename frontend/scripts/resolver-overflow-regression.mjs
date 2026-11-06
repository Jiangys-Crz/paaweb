import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const source = readFileSync(new URL('../src/app/components/ResolverAwards.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/app/components/ResolverAwards.css', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
const originalRequire = createRequire(import.meta.url);
function load(react) {
  const api = {};
  new Function('require', 'exports', compiled)(name => name.endsWith('.css') ? {} : name === 'react' ? react : originalRequire(name), api);
  return api;
}
const { ResolverAwardView } = load(React);
const markup = renderToStaticMarkup(React.createElement(ResolverAwardView, { row: {
  itemId: 1, rank: 1, name: '特别长的队伍名字用于显示', organization: '特别长的组织名字用于显示', englishName: 'A very long team name', members: '张三、李四、王五', coach: '老师', firstSolveIds: ['A', 'B', 'C', 'D'], problemStatus: {}
}, awards: [{ citation: '特别长的自定义奖项名称用于验证完整横向展示', type: 'Other' }] }));
for (const text of ['特别长的队伍名字用于显示', '特别长的组织名字用于显示', 'A very long team name', '张三、李四、王五', '老师', '特别长的自定义奖项名称用于验证完整横向展示', 'A, B, C, D']) {
  assert.ok(markup.includes(`class="resolver-overflow-text">${text}</div>`), `missing scrollable value: ${text}`);
}
assert.ok(markup.includes('指导老师') && markup.includes('ORGANIZATION'));
assert.match(css, /prefers-reduced-motion:reduce[^}]*transform:scaleX\(var\(--overflow-fit\)\)/);
assert.match(css, /prefers-reduced-motion:reduce[^}]*animation:none/);

// Exercise the actual component effect and its ResizeObserver, without a browser dependency.
const viewport = { clientWidth: 100 }, text = { scrollWidth: 80 };
let slot = 0, state = { distance: 0, fit: 1 }, observer, fontListener, cleanup;
const fakeReact = { __esModule: true, default: React,
  useRef: () => ({ current: slot++ === 0 ? viewport : text }),
  useState: () => [state, value => { state = typeof value === 'function' ? value(state) : value; }],
  useEffect: callback => { cleanup = callback(); },
};
const originalObserver = globalThis.ResizeObserver, originalDocument = globalThis.document;
globalThis.ResizeObserver = class {
  constructor(callback) { this.callback = callback; this.targets = []; observer = this; }
  observe(node) { this.targets.push(node); }
  disconnect() { this.disconnected = true; }
};
globalThis.document = { fonts: { addEventListener: (_event, callback) => { fontListener = callback; }, removeEventListener: (_event, callback) => { if (fontListener === callback) fontListener = null; } } };
try {
  const { OverflowText } = load(fakeReact);
  OverflowText({ value: 'Resize-test' });
  assert.deepEqual(state, { distance: 0, fit: 1 }, 'No movement when text fits');
  assert.deepEqual(observer.targets, [viewport, text]);
  text.scrollWidth = 240; observer.callback();
  assert.equal(state.distance, 140, 'Overflow equals the distance to the text end');
  assert.equal(state.fit, 100 / 240, 'Reduced motion fits the entire text');
  viewport.clientWidth = 300; observer.callback();
  assert.deepEqual(state, { distance: 0, fit: 1 }, 'Resizing wider stops the movement');
  viewport.clientWidth = 150; text.scrollWidth = 275; fontListener();
  assert.equal(state.distance, 125, 'Font loading triggers remeasurement');
  assert.equal(state.fit, 150 / 275);
  cleanup();
  assert.equal(observer.disconnected, true);
  assert.equal(fontListener, null);
} finally { globalThis.ResizeObserver = originalObserver; globalThis.document = originalDocument; }
console.log('Resolver overflow values, resize measurement, font remeasurement and reduced-motion fallback passed.');
