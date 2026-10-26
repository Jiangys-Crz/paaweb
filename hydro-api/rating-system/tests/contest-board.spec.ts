import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import { ObjectId } from 'mongodb';
import nunjucks from 'nunjucks';
import { JSDOM } from 'jsdom';
import { buildContestBoard } from '../lib/contest-board';

const beginAt = new Date('2026-10-14T01:00:00Z');
const ridAt = (minutes: number) => ObjectId.createFromTime(beginAt.getTime() / 1000 + minutes * 60);
const tdoc = { docId: 'contest', rule: 'acm', beginAt, endAt: new Date('2026-10-14T06:00:00Z'), pids: [1, 2] };
const headers = [{ type: 'rank' }, { type: 'user' }, { type: 'solved' }, { type: 'problem', raw: 1, value: 'A' }, { type: 'problem', raw: 2, value: 'B' }];
const pdict = { 1: { title: 'A' }, 2: { title: 'B' } };

test('ACM board keeps native tied/star ranks and totals, and shows attempts/minutes', () => {
    const rows = [headers,
        [{ type: 'rank', value: '1' }, { type: 'user', raw: 2 }, { type: 'time', value: '1\n1:20' }, { type: 'record', raw: ridAt(40), score: 100, style: 'first', value: '+2\n0:40' }, { type: 'record', raw: ridAt(50), score: 0, value: '-1' }],
        [{ type: 'rank', value: '1' }, { type: 'user', raw: 3 }, { type: 'time', value: '1\n1:20' }, { type: 'record', raw: ridAt(80), score: 100, value: '+' }, {}],
        [{ type: 'rank', value: '0' }, { type: 'user', raw: 4 }, { type: 'time', value: '0\n0:00' }, {}, {}],
    ];
    const statuses = [{ uid: 2, detail: { 1: { naccept: 2 }, 2: { naccept: 1 } }, journal: [
        { pid: 1, rid: ridAt(10), status: 2 }, { pid: 1, rid: ridAt(20), status: 2 },
        { pid: 1, rid: ridAt(40), status: 1 }, { pid: 2, rid: ridAt(50), status: 2 },
    ] }, { uid: 3, journal: [{ pid: 1, rid: ridAt(80), status: 1 }] }];
    const board = buildContestBoard(tdoc, rows, pdict, statuses, false, 1);
    assert.deepEqual(board.participants.map((row) => row.rank), ['1', '1', '*']);
    assert.equal(board.participants[0].penalty, 80);
    assert.equal(board.participants[0].problems[0].info, '3/40');
    assert.equal(board.participants[0].problems[1].info, '1/50');
    assert.equal(board.participants[0].problems[0].className, 'cell-problem-first-ac');
    assert.equal(board.problems[0].submitted, 4);
    assert.equal(board.problems[0].attempted, 2);
    assert.equal(board.problems[0].accepted, 2);
    assert.equal(board.problems[0].firstSolved, 40);
    assert.equal(board.problems[0].lastSolved, 80);
});

test('frozen board excludes hidden verdicts, scores, timestamps and IDs from cells and statistics', () => {
    const contest = { ...tdoc, lockAt: new Date(beginAt.getTime() + 240 * 60000) };
    const hidden = ridAt(250);
    const rows = [headers, [{ type: 'rank', value: '1' }, { type: 'user', raw: 2 }, { type: 'time', value: '0\n0:00' }, { type: 'record', raw: ridAt(30), score: 0, value: '-1 +1' }, { type: 'record', value: '+1' }]];
    const statuses = [{ uid: 2, detail: { 1: { rid: hidden, score: 100, status: 1 } }, display: { 1: { naccept: 1, npending: 1 }, 2: { npending: 1 } }, journal: [
        { pid: 1, rid: ridAt(30), status: 2 }, { pid: 1, rid: hidden, status: 1 }, { pid: 2, rid: hidden, status: 1 },
    ] }];
    const board = buildContestBoard(contest, rows, pdict, statuses, true, 1);
    assert.equal(board.participants[0].problems[0].className, 'cell-problem-frozen');
    assert.equal(board.participants[0].problems[0].info, '1/30');
    assert.equal(board.participants[0].problems[1].rid, undefined);
    assert.equal(board.problems[0].submitted, 1);
    assert.equal(board.problems[0].accepted, 0);
    assert.equal(board.problems[1].submitted, 0);
    assert.ok(!JSON.stringify(board).includes(String(hidden)));
    assert.ok(!JSON.stringify(board).includes('journal'));
});

test('score board uses native weighted/penalized scores, zero scores and separate practice results', () => {
    const contest = { ...tdoc, rule: 'cf', score: { 1: 200, 2: 50 } };
    const practice = { raw: ridAt(301), score: 100, value: 50 };
    const rows = [[headers[0], headers[1], { type: 'total_score' }, headers[3], headers[4]], [
        { type: 'rank', value: '1' }, { type: 'user', raw: 2 }, { type: 'total_score', value: 180 },
        { type: 'record', raw: ridAt(90), score: 100, value: '180' },
        { type: 'records', raw: [{ raw: ridAt(95), score: 0, value: 0 }, practice] },
    ]];
    const board = buildContestBoard(contest, rows, pdict, [{ uid: 2, journal: [
        { pid: 1, rid: ridAt(90), status: 1 }, { pid: 2, rid: ridAt(95), status: 2 },
    ] }], false, 1);
    assert.equal(board.participants[0].score, 180);
    assert.equal(board.participants[0].problems[0].value, 180);
    assert.equal(board.participants[0].problems[1].value, 0);
    assert.equal(board.participants[0].problems[1].practice, practice);
    assert.equal(board.summaries[2].values[0].value, '180.0');
    assert.equal(board.summaries[2].values[1].value, '0.0');
});

// Render the real row template with Hydro's native template globals, without a server/database.
const plugin = path.resolve(__dirname, '..');
const nativeTemplates = process.env.HYDRO_TEMPLATE_DIR
    || path.join(path.dirname(require.resolve('@hydrooj/ui-default/package.json')), 'templates');
const env = new nunjucks.Environment(new nunjucks.FileSystemLoader([
    path.join(plugin, 'templates'), nativeTemplates,
]), { autoescape: true });
env.addGlobal('typeof', (value: any) => typeof value);
env.addFilter('nl2br', (value: any) => String(value).replaceAll('\n', '<br>'));
const context: any = {
    tdoc: { ...tdoc, rule: 'oi', score: { 1: 50 } },
    rdoc: { _id: ridAt(1), uid: 2, pid: 1, domainId: 'system', status: 1, score: 80, time: 0, memory: 0, files: {} },
    udoc: { _id: 2, uname: '<script>alert(1)</script>', avatar: '' }, pdoc: { pid: 1, title: '<b>unsafe</b>' },
    handler: { user: { hasPerm: () => false, _id: 2 }, ratingBoardRecords: false },
    STATUS: { STATUS_ACCEPTED: 1, STATUS_JUDGING: 20 },
    perm: {}, model: { builtin: { STATUS_CODES: { 1: 'pass' }, STATUS_TEXTS: { 1: 'Accepted' } }, setting: { langs: { cpp: { display: 'C++' } } } },
    utils: { getAlphabeticId: (index: number) => String.fromCharCode(65 + index), status: { getScoreColor: () => '#16a34a' } },
    datetimeSpan: () => '<span>2026-10-14 09:01:00</span>', size: () => '0 Bytes',
    avatarUrl: () => '/avatar', _: (value: string) => value, url: (name: string) => `/${name}`,
};

test('initial and WebSocket contest rows use identical columns, escape user content and retain zero resources', () => {
    const initial = env.render('record_main_tr.html', { ...context, ratingContestPage: true });
    const live = env.render('record_main_tr.html', { ...context, handler: { ...context.handler, ratingBoardRecords: true } });
    assert.equal(initial, live);
    const doc = new JSDOM(`<table><tbody>${initial}</tbody></table>`).window.document;
    assert.equal(doc.querySelectorAll('td').length, 9);
    assert.equal(doc.querySelector('.col--score')?.textContent, '40 / 50');
    assert.equal(doc.querySelector('.col--time')?.textContent, '0ms');
    assert.equal(doc.querySelector('.col--memory')?.textContent, '0 Bytes');
    assert.ok(!doc.querySelector('script'));
    assert.ok(!doc.querySelector('.col--problem b'));
    assert.equal(doc.querySelector('.board-submission-verdict')?.textContent?.trim(), 'Accepted');
    assert.equal(doc.querySelectorAll('form').length, 0);
});

test('ordinary and homework rows keep Hydro columns; contest managers keep authorized actions', () => {
    const native = env.render('record_main_tr.html', { ...context, pdoc: { ...context.pdoc, pid: 'P1' }, rdoc: { ...context.rdoc, lang: 'cpp' } });
    assert.equal(new JSDOM(`<table>${native}</table>`).window.document.querySelectorAll('td').length, 7);
    const manager = env.render('record_main_tr.html', {
        ...context, ratingContestPage: true, handler: { user: { hasPerm: () => true } },
    });
    assert.ok(manager.includes('value="rejudge"'));
    assert.ok(manager.includes('value="cancel"'));
});

test('AJAX and resized tables reinitialize without dropping the native row IDs', async () => {
    const html = env.render('record_main_tr.html', { ...context, ratingContestPage: true });
    const dom = new JSDOM(`<div class="rating-external-board"><div data-rating-board-fit><div class="rating-board-fit-slot"><div class="rating-board-fit-inner" data-board-width="1180"><table><tbody>${html}</tbody></table></div></div></div></div>`, {
        runScripts: 'outside-only', pretendToBeVisual: true,
    });
    dom.window.eval(fs.readFileSync(path.join(plugin, 'public/rating-system.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 40));
    assert.equal(dom.window.document.querySelector('tr')?.getAttribute('data-rid'), String(context.rdoc._id));
    assert.ok(dom.window.document.querySelector<HTMLElement>('.rating-board-fit-inner')?.style.transform.startsWith('scale('));
    dom.window.close();
});

test('live username/school/display-name search combines with Rating participation and survives native refresh', async () => {
    const rows = '<tr data-rating-board-participant data-uid="2" data-unrank="0" data-board-search="alice 张三 示例大学"><td>Alice</td></tr>'
        + '<tr data-rating-board-participant data-uid="3" data-unrank="0" data-board-search="bob 李四 EXAMPLE"><td>Bob</td></tr>'
        + '<tr data-rating-board-participant data-uid="4" data-unrank="1" data-board-search="cathy 张三 EXAMPLE"><td>Cathy</td></tr>';
    const dom = new JSDOM('<div class="rating-scoreboard-card rating-external-board"><input data-rating-board-search>'
        + '<select data-rating-board-participation><option value="all">ALL</option><option value="rank">Rating 排名</option><option value="unrank">不计 Rating</option></select>'
        + `<table><tbody>${rows}</tbody></table></div>`, { runScripts: 'outside-only', pretendToBeVisual: true });
    dom.window.eval(fs.readFileSync(path.join(plugin, 'public/rating-system.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    const search = dom.window.document.querySelector<HTMLInputElement>('[data-rating-board-search]')!;
    const participation = dom.window.document.querySelector<HTMLSelectElement>('[data-rating-board-participation]')!;
    const settle = () => new Promise((resolve) => setTimeout(resolve, 40));
    const visible = () => [...dom.window.document.querySelectorAll<HTMLElement>('[data-rating-board-participant]')]
        .filter((row) => row.style.display !== 'none').map((row) => row.dataset.uid);
    search.value = ' 张三 ';
    search.dispatchEvent(new dom.window.Event('input'));
    await settle();
    assert.deepEqual(visible(), ['2', '4']);
    participation.value = 'unrank';
    participation.dispatchEvent(new dom.window.Event('change'));
    await settle();
    assert.deepEqual(visible(), ['4']);
    participation.value = 'rank';
    participation.dispatchEvent(new dom.window.Event('change'));
    await settle();
    assert.deepEqual(visible(), ['2']);
    search.value = 'example';
    search.dispatchEvent(new dom.window.Event('input'));
    await settle();
    assert.deepEqual(visible(), ['3']);
    // Native PJAX replaces the rows, then shows all of them asynchronously.
    dom.window.document.querySelector('tbody')!.innerHTML = rows;
    await settle();
    assert.deepEqual(visible(), ['3']);
    participation.value = 'all';
    participation.dispatchEvent(new dom.window.Event('change'));
    await settle();
    assert.deepEqual(visible(), ['3', '4']);
    search.value = 'ALICE';
    search.dispatchEvent(new dom.window.Event('input'));
    await settle();
    assert.deepEqual(visible(), ['2']);
    dom.window.close();
});
