import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

const source = readFileSync(new URL('../src/app/utils/resolverMotion.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const api = {};
new Function('require', 'exports', compiled)(createRequire(import.meta.url), api);

// Values generated independently by Resolver 2.7.1354's Java Animator. The
// sequence includes cruising, braking beyond a new target, a second reversal,
// and retargeting after changing the native time scale.
const nativeValues = [
  0.125, 0.5, 1.125, 2, 3.125, 4.5, 6.125, 7.875,
  9.5, 10.875, 12, 12.875, 13.5, 13.875, 14, 13.875,
  13.5, 12.875, 12, 10.875, 9.75, 8.875, 8.25, 7.875,
  7.75, 7.875, 8.25, 8.875, 7.95646738, 8,
];
const animator = new api.ResolverAnimator(0, api.RESOLVER_ROW_MOVEMENT);
const actual = [];
const advance = (count) => {
  for (let i = 0; i < count; i++) {
    animator.incrementTimeMs(250);
    actual.push(animator.getValue());
  }
};
animator.setTarget(20);
advance(8);
animator.setTarget(2);
advance(12);
animator.setTarget(25);
advance(8);
animator.setTimeScale(0.3);
animator.incrementTimeMs(321);
actual.push(animator.getValue());
animator.setTarget(8);
animator.incrementTimeMs(331);
actual.push(animator.getValue());
assert.equal(actual.length, nativeValues.length);
actual.forEach((value, index) => assert.ok(Math.abs(value - nativeValues[index]) < 1e-9,
  `Native Animator sample ${index}: expected ${nativeValues[index]}, got ${value}`));

const singleRow = new api.ResolverAnimator(0, api.RESOLVER_ROW_MOVEMENT);
singleRow.setTarget(1);
assert.equal(singleRow.getMoveTime(), 1);
singleRow.incrementTimeMs(500);
assert.equal(singleRow.getValue(), 0.5);
singleRow.incrementTimeMs(500);
assert.equal(singleRow.getValue(), 1);
assert.deepEqual(Object.values(api.RESOLVER_DELAY_MS), [1300, 1000, 2250, 1500, 850, 250, 450]);
console.log('Resolver motion matches native Java samples, and timing.');
