import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const source = readFileSync(new URL('../src/app/components/ResolverAwards.tsx', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
const api = {}, originalRequire = createRequire(import.meta.url);
const appDependencies = new Map();
for (const [name, file] of [
  ['@/app/utils/boardParticipants', '../src/app/utils/boardParticipants.ts'],
  ['@/utils/advisorNames', '../src/utils/advisorNames.ts'],
]) {
  const dependency = {};
  const code = ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  new Function('exports', code)(dependency);
  appDependencies.set(name, dependency);
}
new Function('require', 'exports', compiled)(name => name.endsWith('.css') ? {} : appDependencies.get(name) || originalRequire(name), api);
const row = { itemId: 7, rank: 1, name: '⭐ 👧 星河队', organization: '测试大学', members: '甲、乙、丙', coach: '李老师', solved: 8, problemStatus: {}, photo: '/default.jpg', hasTeamPhoto: false, firstSolveIds: ['A', 'B', 'K'] };
const render = (override = {}, medal = 'Gold') => renderToStaticMarkup(React.createElement(api.ResolverAwardView, { row: { ...row, ...override }, medal, awards: [{ citation: `${medal} Medal`, type: 'Medal' }, { citation: 'First Solve A, B and K', type: 'First to Solve' }] }));
const missing = render();
assert.ok(missing.includes('<svg class="resolver-medal-illustration"'));
assert.ok(!missing.includes('/default.jpg'), 'Default photo must never replace a missing team photo.');
for (const value of ['测试大学', '李老师', '甲、乙、丙', '金奖', 'Gold', 'A, B, K']) assert.ok(missing.includes(value), value);
assert.ok(!missing.includes('First Solve A, B and K'), 'First solve must appear only in the bottom statistic, not as an award plaque.');
assert.ok(missing.includes('FIRST SOLVE'));
assert.ok(!missing.includes('SCHOOL / ORGANIZATION'));
assert.ok(!missing.includes('ALT. TEAM NAME'), 'Do not invent an alternate team name.');
const photo = render({ photo: '/team.jpg', hasTeamPhoto: true, englishName: 'Star Team' });
assert.ok(photo.includes('resolver-award-view-photo'));
assert.ok(photo.includes('/team.jpg'));
assert.ok(!photo.includes('<svg class="resolver-medal-illustration"'));
assert.ok(photo.includes('ALT. TEAM NAME') && photo.includes('Star Team'));
const missingCoach = render({ coach: '', advisor: '' });
assert.ok(missingCoach.includes('指导老师') && missingCoach.includes('无'));
const silver = render({}, 'Silver');
assert.ok(silver.includes('银奖') && silver.includes('Silver'));
console.log('Resolver ceremony photo gating, medal fallback, team fields, and combined first solves passed.');
