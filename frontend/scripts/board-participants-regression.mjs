import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const api = {};
const source = readFileSync(new URL('../src/app/utils/boardParticipants.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2017 }
}).outputText;
new Function('exports', compiled)(api);

const participants = [
  { name: 'official', isStar: false, isGirl: false },
  { name: 'girls', isStar: false, isGirl: true },
  { name: 'unofficial', isStar: true, isGirl: false },
  { name: 'unofficial girls', isStar: true, isGirl: true },
  { name: 'legacy' }
];
const select = filter => participants.filter(row => api.matchesBoardParticipantType(row, filter));
assert.deepEqual(select('official').map(row => row.name), ['official', 'girls', 'legacy']);
assert.deepEqual(select('girl').map(row => row.name), ['girls']);
assert.deepEqual(select('star').map(row => row.name), ['unofficial', 'unofficial girls']);
assert.deepEqual(select('all'), participants);
assert.ok(select('girl').every(row => select('official').includes(row)));
console.log('Board female participants remain a subset of official participants.');

const boardRows = [
  { itemId: 11, rank: 1, name: '同名队伍', organization: 'Alpha University', members: 'Hidden Member', solved: 5 },
  { itemId: 12, rank: -1, name: '同名队伍', organization: 'Beta University', isStar: true, solved: 4 },
  { itemId: 13, rank: 2, name: '张三', organization: '示例大学', solved: 3 },
  { itemId: 14, rank: 3, name: 'No Organization', solved: 2 }
];
const search = query => boardRows.filter(row => api.matchesBoardParticipantSearch(row, query)).map(row => row.itemId);
assert.deepEqual(search('  张三  '), [13]);
assert.deepEqual(search('示例大学'), [13]);
assert.deepEqual(search('UNIVERSITY'), [11, 12]);
assert.deepEqual(search('ａｌｐｈａ'), [11]);
assert.deepEqual(search('University 同名'), [11, 12]);
assert.deepEqual(search('Hidden Member'), [], 'Team search uses the team name and institution, not member names.');
assert.deepEqual(search('missing'), []);
assert.deepEqual(search('  '), [11, 12, 13, 14]);

const snapshot = JSON.stringify(boardRows);
const displayed = api.buildBoardDisplayRows(boardRows, [13, 12, 13, 999]);
const pinned = displayed.filter(row => row.isPinned);
const ranked = displayed.filter(row => !row.isPinned);
assert.deepEqual(pinned.map(row => row.itemId), [13, 12], 'Keep selection order and ignore duplicate or unavailable IDs.');
assert.deepEqual(pinned.map(row => row.rank), [2, -1], 'Pinned copies keep official and unofficial ranks.');
assert.deepEqual(ranked.map(row => row.itemId), [11, 12, 13, 14], 'Every participant remains in the ranked list.');
assert.deepEqual(ranked.map(row => row.boardPosition), [1, 2, 3, 4], 'Adding pins does not shift original row numbers.');
assert.equal(new Set(displayed.map(row => row.boardRowKey)).size, displayed.length, 'Pinned and ranked copies need distinct row keys.');
assert.equal(JSON.stringify(boardRows), snapshot, 'Displaying pinned copies must not mutate board data.');
assert.deepEqual(api.buildBoardDisplayRows(boardRows, []).map(row => row.itemId), [11, 12, 13, 14]);
assert.deepEqual(api.buildBoardDisplayRows(boardRows, [12]).filter(row => row.isPinned).map(row => row.itemId), [12], 'Removing one selection keeps the other.');
assert.deepEqual(api.buildBoardDisplayRows([], [13]), []);

const refreshedRows = [
  { ...boardRows[2], rank: 1, solved: 6 },
  { ...boardRows[0], rank: 2 },
  boardRows[1],
  boardRows[3]
];
const refreshed = api.buildBoardDisplayRows(refreshedRows, [13]);
assert.equal(refreshed[0].rank, 1, 'Pinned rows use the latest rank after refresh.');
assert.equal(refreshed[0].solved, 6, 'Pinned rows use the latest score after refresh.');
assert.equal(refreshed[0].boardPosition, 1);
assert.equal(refreshed.filter(row => row.itemId === 13).length, 2);
console.log('Board search and multiple pinned rows preserve identities, original standings, and refreshed results.');
