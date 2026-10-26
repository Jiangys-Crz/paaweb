import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import { MongoClient, ObjectId } from 'mongodb';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { JSDOM } from 'jsdom';
import nunjucks from 'nunjucks';
import { matchProblems, prepareRecordConnection, prepareRecordSearch, recordInScope, recordQuery } from '../lib/record-search';

const plugin = path.resolve(__dirname, '..');
const endAt = new Date('2026-01-01T06:00:00Z');
const rid = (offset: number) => ObjectId.createFromTime(endAt.getTime() / 1000 + offset);
const tdoc = { docId: new ObjectId(), pids: [101, 102], rule: 'acm', beginAt: new Date('2026-01-01T01:00:00Z'), endAt };
const problems = [
    { docId: 101, pid: 'P101', title: '还差几个座位', label: 'A' },
    { docId: 102, pid: 'P102', title: '动态规划', label: 'B' },
    { docId: 103, pid: 'P103', title: '图论', label: 'AA' },
];

test('problem search accepts contest letters, IDs and title fragments, including AA and full-width text', () => {
    for (const keyword of ['a', 'Ａ', '101', 'p101', 'A - 还差几个座位', '几个座位']) {
        assert.deepEqual(matchProblems(problems, keyword), [101]);
    }
    assert.deepEqual(matchProblems(problems, 'aa'), [103]);
    assert.deepEqual(matchProblems(problems, '不存在'), []);
    assert.deepEqual(matchProblems(problems, ''), [101, 102, 103]);
});

test('database searches isolate the contest, include corrections and non-attendees, and preserve self scope/pagination', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const records = client.db('record_search').collection<any>('record');
    const users: any = { 2: { _id: 2, uname: 'Alice' }, 3: { _id: 3, uname: 'Bob' }, 4: { _id: 4, uname: 'LateStudent' } };
    const make = (offset: number, data: any = {}) => ({
        _id: rid(offset), domainId: 'system', contest: tdoc.docId, pid: 101, uid: 2, status: 1, lang: 'cpp', code: 'private', ...data,
    });
    const docs = [
        make(-100), make(-90, { pid: 102, uid: 3 }), make(10),
        make(20, { contest: undefined, uid: 4 }), make(-50, { contest: undefined }),
        make(30, { contest: new ObjectId() }), make(40, { contest: undefined, pid: 999 }),
        make(50, { domainId: 'another' }), make(60, { contest: new ObjectId('000000000000000000000000') }),
        make(70, { status: 0, lang: 'py' }),
    ];
    await records.insertMany(docs);
    let projections = 0;
    const models: any = {
        ObjectId, recordLimit: 2, editContestPermission: 'edit',
        RecordModel: { coll: records, PROJECTION_LIST: ['_id', 'pid', 'uid', 'contest', 'status', 'lang', 'domainId'],
            getMulti: (domainId: string, query: any) => records.find({ domainId, ...query }) },
        ProblemModel: { PROJECTION_CONTEST_LIST: [], getList: async () => Object.fromEntries(problems.map((p) => [p.docId, p])) },
        UserModel: { getListForRender: async (_domain: string, ids: number[]) => Object.fromEntries(ids.map((id) => [id, users[id]])),
            getList: async (_domain: string, ids: number[]) => Object.fromEntries(ids.map((id) => [id, users[id]])) },
        ContestModel: { applyProjection: (_contest: any, record: any) => { projections++; return record; } },
    };
    const handler = (args: any = {}) => ({ args, domain: { _id: 'system' },
        user: { _id: 2, own: () => false, hasPerm: () => false },
        url: (_name: string, params: any) => `/record?${new URLSearchParams(params.query)}` });
    const search = async (args: any = {}, options: any = {}, self = false) => {
        const body: any = { tdoc, page: 1, ...options };
        await prepareRecordSearch(handler(args), body, models, self);
        return body;
    };
    const first = await search();
    assert.deepEqual(first.rdocs.map((r: any) => String(r._id)), [String(rid(70)), String(rid(20))]);
    assert.ok(first.ratingRecordsHasNext);
    assert.equal(first.ratingRecordProblems.length, 2, 'suggestions contain only contest problems');
    assert.ok(first.rdocs.every((r: any) => !r.code), 'list response never includes source code');
    const second = await search({}, { page: 2 });
    assert.deepEqual(second.rdocs.map((r: any) => String(r._id)), [String(rid(10)), String(rid(-90))]);
    assert.ok(second.ratingRecordsPreviousUrl);
    const byTitle = await search({ problemKeyword: '动态' });
    assert.deepEqual(byTitle.rdocs.map((r: any) => r.pid), [102]);
    const late = await search({ userKeyword: 'latestu' });
    assert.deepEqual(late.rdocs.map((r: any) => r.uid), [4], 'a correction author needs no contest registration');
    assert.equal((await search({ problemKeyword: 'P999' })).rdocs.length, 0);
    assert.equal((await search({ userKeyword: 'Bob' }, {}, true)).rdocs.length, 0, 'self mode cannot be broadened by username search');
    const own = await search({}, {}, true);
    assert.ok(own.rdocs.every((r: any) => r.uid === 2));
    assert.equal(own.ratingRecordsSelfUid, 2);
    assert.ok(own.ratingRecordsSocketUrl.includes('uidOrName=2'));
    const pending = await search({}, { filterStatus: 0, filterLang: 'py' });
    assert.deepEqual(pending.rdocs.map((r: any) => String(r._id)), [String(rid(70))]);
    assert.ok(projections > 0, 'native projection is applied to every displayed record');
    const beforeEnd = recordQuery(tdoc, { pids: tdoc.pids }, ObjectId, endAt.getTime() - 1);
    assert.equal(beforeEnd.contest, tdoc.docId);
    assert.ok(!beforeEnd.$or);
    const all = await records.find({ domainId: 'system', ...recordQuery(tdoc, { pids: tdoc.pids }, ObjectId) }).toArray();
    assert.deepEqual(all.map((r: any) => String(r._id)).sort(), [-100, -90, 10, 20, 70].map((offset) => String(rid(offset))).sort());
    for (const doc of docs) assert.equal(!!recordInScope(tdoc, doc, 'system'), all.some((r: any) => r._id.equals(doc._id)));
});

test('live records use the same contest/problem/user/verdict/language filters and delegate native authorization', async () => {
    const queue = new Map<string, Function>();
    const authorized: any[] = [];
    const h: any = { tdoc, args: { problemKeyword: 'a', userKeyword: 'late', status: '0', lang: 'cpp' }, domain: { _id: 'system' },
        __subscribe: [{ name: 'record/change', target: () => {} }],
        queueSend: (id: string, render: any) => queue.set(id, render),
        onRecordChange(record: any) { authorized.push(record); this.queueSend(String(record._id), async () => ({ html: 'row' })); } };
    await prepareRecordConnection(h, {
        ProblemModel: { getList: async () => Object.fromEntries(problems.map((p) => [p.docId, p])) },
        UserModel: { getById: async (_domain: string, id: number) => ({ _id: id, uname: id === 4 ? 'LateStudent' : 'Other' }) },
    });
    assert.equal(h.__subscribe[0].target, h.onRecordChange);
    const record = { _id: rid(20), domainId: 'system', pid: 101, uid: 4, status: 0, lang: 'cpp' };
    await h.onRecordChange(record);
    assert.equal(authorized.length, 1);
    assert.equal(authorized[0].contest, tdoc.docId);
    assert.ok(!('contest' in record), 'correction association never rewrites stored data');
    assert.deepEqual(await queue.get(String(record._id))!(), { html: 'row' });
    await h.onRecordChange({ ...record, status: 1 });
    assert.deepEqual(await queue.get(String(record._id))!(), { remove: String(record._id) });
    for (const data of [{ pid: 999 }, { pid: 102 }, { contest: new ObjectId() }, { domainId: 'another' }, { uid: 2 }, { lang: 'py' }]) {
        await h.onRecordChange({ ...record, ...data });
    }
    assert.equal(authorized.length, 1);
    const initial = { ...record, _id: rid(21), status: 1 };
    const initialQueue = new Map<string, Function>();
    const initialHandler = { ...h, __subscribe: [],
        onRecordChange(doc: any) { this.queueSend(String(doc._id), async () => ({ html: 'never show filtered verdict' })); },
        async message(_message: any) { await this.onRecordChange(initial); },
        queueSend: (id: string, render: any) => initialQueue.set(id, render),
    };
    await prepareRecordConnection(initialHandler, {
        ProblemModel: { getList: async () => Object.fromEntries(problems.map((p) => [p.docId, p])) },
        UserModel: { getById: async () => ({ _id: 4, uname: 'LateStudent' }) },
    });
    await initialHandler.message({ rids: [String(initial._id)] });
    assert.deepEqual(await initialQueue.get(String(initial._id))!(), { remove: String(initial._id) });
});

test('ended contest navigation is visible without registration, without granting code export or management', () => {
    const loader = new nunjucks.FileSystemLoader(path.join(plugin, 'templates'));
    const env = new nunjucks.Environment({ getSource(name: string) {
        if (name === 'components/contest.html') return { src: '{% macro render_time(t) %}{{ t }}{% endmacro %}{% macro render_duration(t) %}5{% endmacro %}', path: name };
        if (name === 'components/user.html') return { src: '{% macro render_inline(u) %}{{ u.uname }}{% endmacro %}', path: name };
        return loader.getSource(name);
    } } as any);
    const model = { contest: { isDone: () => true, statusText: () => 'Ended', canShowScoreboard: () => true,
        canShowRecord: () => true, canShowSelfRecord: () => true, RULES: { acm: { TEXT: 'XCPC' } } } };
    const context = { tdoc, tsdoc: null, ratingContestUi: { beforeStart: false }, model, perm: {}, PRIV: {},
        handler: { user: { _id: 4, own: () => false, hasPerm: () => false, hasPriv: () => false } },
        _: (value: string) => value, url: (name: string) => `/${name}` };
    const html = env.render('partials/contest_header.html', context);
    for (const name of ['比赛说明', '题目列表', '排行榜', '所有递交', '我的递交']) assert.ok(html.includes(name), name);
    for (const name of ['导出代码', '编辑比赛', '比赛管理']) assert.ok(!html.includes(name), name);
    model.contest.isDone = () => false;
    assert.ok(!env.render('partials/contest_header.html', context).includes('aria-label="比赛导航"'));
});

test('dynamic search debounces typing, rejects stale results and preserves contest/self scope through paging and reset', async () => {
    const dom = new JSDOM('<div class="rating-submissions-card"><form data-rating-record-filter data-record-url="/record" data-record-page="1">'
        + '<input type="hidden" name="tid" value="this-contest"><input type="hidden" name="uidOrName" value="2">'
        + '<input type="text" name="problemKeyword"><input type="text" name="userKeyword">'
        + '<select name="status"><option value="">Status</option><option value="0">Pending</option></select>'
        + '<select name="lang"><option value="">All Languages</option><option value="cpp">C++</option></select>'
        + '<a data-rating-record-reset>Reset</a><p data-rating-record-status hidden></p></form>'
        + '<div data-rating-record-results>Initial</div></div>', {
        url: 'http://localhost/record?tid=this-contest&uidOrName=2', runScripts: 'outside-only', pretendToBeVisual: true,
    });
    const calls: any[] = [];
    (dom.window as any).fetch = (url: string) => new Promise((resolve) => calls.push({ url: new URL(url), resolve }));
    dom.window.eval(fs.readFileSync(path.join(plugin, 'public/rating-system.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    const form = dom.window.document.querySelector('form')!;
    const problem = form.querySelector<HTMLInputElement>('[name="problemKeyword"]')!;
    const results = dom.window.document.querySelector('[data-rating-record-results]')!;
    const settle = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms));
    const respond = (index: number, html: string, page = 1) => calls[index].resolve({ ok: true, json: async () => ({ html, page }) });
    problem.value = 'A'; problem.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    await settle(280);
    assert.equal(calls.length, 1);
    problem.value = '动态'; problem.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    respond(0, 'Stale');
    await settle();
    assert.equal(results.textContent, 'Initial');
    await settle(270);
    assert.equal(calls.length, 2);
    assert.equal(calls[1].url.searchParams.get('problemKeyword'), '动态');
    assert.equal(calls[1].url.searchParams.get('tid'), 'this-contest');
    assert.equal(calls[1].url.searchParams.get('uidOrName'), '2');
    respond(1, 'Matched<a data-rating-record-page="2" href="?page=2">Next</a>');
    await settle();
    assert.ok(results.textContent?.includes('Matched'));
    assert.ok(!dom.window.location.search.includes('ratingDynamic'));
    results.querySelector('a')!.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true, cancelable: true }));
    assert.equal(calls[2].url.searchParams.get('page'), '2');
    respond(2, 'Page 2', 2); await settle();
    form.querySelector('[data-rating-record-reset]')!.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true, cancelable: true }));
    assert.equal(calls[3].url.searchParams.get('problemKeyword'), '');
    assert.equal(calls[3].url.searchParams.get('uidOrName'), '2');
    assert.equal(calls[3].url.searchParams.get('tid'), 'this-contest');
    assert.equal(calls[3].url.searchParams.get('page'), null);
    respond(3, 'Reset'); await settle();
    form.dispatchEvent(new dom.window.CompositionEvent('compositionstart', { bubbles: true }));
    problem.value = '中'; problem.dispatchEvent(new dom.window.InputEvent('input', { bubbles: true, isComposing: true }));
    await settle(280); assert.equal(calls.length, 4);
    form.dispatchEvent(new dom.window.CompositionEvent('compositionend', { bubbles: true }));
    await settle(280); assert.equal(calls.length, 5);
    respond(4, '中文'); await settle();
    dom.window.close();
});

test('live browser rows replace the same ID and restore the empty state when a verdict leaves the filter', async () => {
    const dom = new JSDOM('<div class="rating-submissions-card"><form data-rating-record-filter data-record-url="/record" '
        + 'data-record-socket="record-conn?tid=contest&amp;ratingBoard=1" data-record-page="1" data-record-limit="2">'
        + '<a data-rating-record-reset>Reset</a></form><div data-rating-record-results><table><thead><tr><th>ID</th></tr></thead>'
        + '<tbody><tr data-rating-board-empty><td>No submissions</td></tr></tbody></table></div></div>', {
        url: 'http://localhost/record?tid=contest', runScripts: 'outside-only', pretendToBeVisual: true,
    });
    const sockets: any[] = [];
    (dom.window as any).WebSocket = class {
        readyState = 1;
        sent: any[] = [];
        onmessage: any;
        onopen: any;
        constructor(public url: string) { sockets.push(this); }
        send(value: string) { this.sent.push(value); }
        close() {}
    };
    dom.window.eval(fs.readFileSync(path.join(plugin, 'public/rating-system.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    assert.equal(sockets.length, 1, 'only the plugin owns the contest record connection');
    const socket = sockets[0];
    assert.ok(socket.url.startsWith('ws://localhost/record-conn?tid=contest'));
    socket.onopen();
    assert.deepEqual(JSON.parse(socket.sent[0]), { rids: [] });
    const update = (message: any) => socket.onmessage({ data: JSON.stringify(message) });
    update({ html: '<tr data-rid="r1"><td>Pending</td></tr>' });
    update({ html: '<tr data-rid="r1"><td>Accepted</td></tr>' });
    const doc = dom.window.document;
    assert.equal(doc.querySelectorAll('[data-rid]').length, 1);
    assert.equal(doc.querySelector('[data-rid]')?.textContent, 'Accepted');
    update({ remove: 'r1' });
    assert.equal(doc.querySelectorAll('[data-rid]').length, 0);
    assert.equal(doc.querySelectorAll('[data-rating-board-empty]').length, 1);
    dom.window.close();
});
