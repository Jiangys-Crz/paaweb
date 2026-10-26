import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

const require = createRequire(import.meta.url);
const api = {};
const source = readFileSync(new URL('../src/app/utils/boardProgress.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true }
}).outputText;
new Function('require', 'exports', compiled)(require, api);

const board = {
  status: 'ENDED', startTime: '2026-05-24T09:30:00',
  freezeTime: '2026-05-24T13:30:00', endTime: '2026-05-24T14:30:00'
};
const endedTime = new Date('2026-05-24T15:00:00').getTime();
let lookups = 0;
const loadEvent = async () => { lookups++; return { unfrozen: true }; };

// All supported response contracts must show the same released, ended state.
for (const payload of [{ unfrozen: true }, { isUnfrozen: true }, {}]) {
  const unfrozen = await api.resolveBoardUnfreezeState(payload, loadEvent);
  assert.equal(api.getBoardProgressStatus({ ...board, ...payload, unfrozen }, endedTime), 'ENDED');
}
assert.equal(lookups, 1, 'Only a missing state should fetch public event details');
assert.equal(await api.resolveBoardUnfreezeState({ unfrozen: false }, loadEvent), false);
assert.equal(lookups, 1, 'An explicit frozen state must not be replaced');
assert.equal(await api.resolveBoardUnfreezeState({}, async () => ({ isUnfrozen: false })), false);

// A fresh lookup must reflect the organizer's unfreeze without reusing the old value.
let event = { unfrozen: false };
const before = await api.resolveBoardUnfreezeState({}, async () => event);
assert.equal(api.getBoardProgressStatus({ ...board, unfrozen: before }, endedTime), 'FROZEN');
event = { unfrozen: true };
const after = await api.resolveBoardUnfreezeState({}, async () => event);
assert.equal(api.getBoardProgressStatus({ ...board, unfrozen: after }, endedTime), 'ENDED');

assert.equal(api.getBoardProgressStatus({ ...board, unfrozen: true }, new Date(board.freezeTime).getTime()), 'FROZEN');
assert.equal(api.getBoardProgressStatus({ ...board, unfrozen: true }, new Date(board.endTime).getTime()), 'ENDED');
await assert.rejects(api.resolveBoardUnfreezeState({}, async () => { throw new Error('lookup failed'); }), /lookup failed/);
for (const type of ['GPLT', 'LANQIAO']) {
  for (const unfrozen of [false, true, undefined]) {
    const scoringBoard = { ...board, type, freezeTime: undefined, unfrozen };
    assert.equal(api.hasBoardFreezeWindow(scoringBoard), false);
    assert.equal(api.getBoardProgressStatus(scoringBoard, new Date('2026-05-24T09:29:59').getTime()), 'PENDING');
    assert.equal(api.getBoardProgressStatus(scoringBoard, new Date(board.freezeTime).getTime()), 'RUNNING');
    assert.equal(api.getBoardProgressStatus(scoringBoard, endedTime), 'ENDED');
  }
  assert.equal(api.hasBoardFreezeWindow({ ...board, type }), false, 'Scoring contests ignore legacy freeze settings');
}
assert.equal(api.hasBoardFreezeWindow({ ...board, type: 'XCPC', freezeTime: undefined }), false);
assert.equal(api.getBoardProgressStatus({ ...board, type: 'XCPC', freezeTime: undefined }, endedTime), 'ENDED');
console.log('Board progress API compatibility and unfreeze regression checks passed.');
