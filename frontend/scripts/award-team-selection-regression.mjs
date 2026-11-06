import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const source = readFileSync(new URL('../src/app/admin/_legacy/contest/awardTeamSelection.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const api = {};
new Function('exports', code)(api);
const merge = api.mergeVisibleAwardSelection;
const saved = [1, 90]; // ID 90 is unavailable in the current response and must survive.
let draft = [...saved];
draft = merge(draft, [1, 2, 3], [1, 2]);
assert.deepEqual(draft, [1, 90, 2]);
// A search exposes another team; selecting it must not discard earlier selections.
draft = merge(draft, [3, 4], [1, 2, 3, 90]);
assert.deepEqual(draft, [1, 90, 2, 3]);
// Deselect team 2 from a filtered view while preserving everything else.
draft = merge(draft, [2, 4], [1, 3, 90]);
assert.deepEqual(draft, [1, 90, 3]);
assert.deepEqual(saved, [1, 90], 'editing a draft must not mutate saved selection on cancel');
assert.deepEqual(merge(draft, [], []), draft, 'an empty search must not clear previous selection');
assert.deepEqual(merge(draft, [1, 3], []), [90], 'clear visible selection preserves unavailable entries');
console.log('Award selection retains hidden IDs across search, paging, deselection and cancellation.');
