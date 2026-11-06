import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

const source = readFileSync(new URL('../src/app/utils/resolverLayout.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const api = {};
new Function('require', 'exports', compiled)(createRequire(import.meta.url), api);

// The native ResolverUI disables title and clock. The viewport is the full screen.
const normal = api.getResolverLayout(999, 22);
const large = api.getResolverLayout(1346, 29);
const ultra = api.getResolverLayout(2015, 43);
for (const layout of [normal, large, ultra]) assert.equal(layout.visibleRows, 12);
assert.ok(large.rowHeight > normal.rowHeight * 1.3);
assert.ok(ultra.rowHeight > normal.rowHeight * 1.95);
assert.equal(api.getResolverLayout(10, 20).rowHeight, 24);
const board = api.getResolverLayout(1080, 21, 1920, 13);
assert.equal(board.rowHeight, 88.25);
assert.equal(board.rowFontSize, 40.078125);
assert.equal(board.cubeHeight, 30);
assert.ok((board.cubeWidth + 5) * 13 <= 1920 - board.rowHeight * 3 - 70);
assert.ok(board.statusFontSize > board.problemFontSize);
console.log('Resolver scaling checks passed:', { normal, large, ultra });

const narrow = api.getResolverLayout(881, 17, 900, 13);
assert.ok(narrow.statusFontSize * 4.2 <= narrow.cubeWidth, 'Submission count and minute label must fit the narrow tile.');
assert.ok(narrow.problemFontSize <= narrow.statusFontSize);
const wide = api.getResolverLayout(1080, 21, 1920, 13);
assert.ok(wide.statusFontSize > narrow.statusFontSize * 1.5, 'Full display keeps large text.');
