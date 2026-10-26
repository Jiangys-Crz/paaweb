import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { test } from 'node:test';
import { MongoClient, ObjectId } from 'mongodb';
import { MongoMemoryServer } from 'mongodb-memory-server';
import nunjucks from 'nunjucks';
import { JSDOM } from 'jsdom';
import { calculateProblemRatings } from '../lib/problem-rating-data';
import { createProblemRatingService, installProblemRatingService } from '../lib/problem-rating-service';
import {
    estimateProblemRating, independentSamples, problemRatingColor, problemRatingValue, ProblemSample,
    PROBLEM_RATING_VERSION, solveProbability,
} from '../lib/problem-rating';

const plugin = path.resolve(__dirname, '..');
const now = new Date('2026-10-26T00:00:00Z').getTime();
const sample = (uid: number, solved: boolean, strength = 1800): ProblemSample => ({
    uid, solved, strength, ratedCount: 6, source: 'contest', at: 1000,
});

test('difficulty uses the site strength scale, rises with stronger solvers and lower solve rates, and has finite boundaries', () => {
    const half = Array.from({ length: 10 }, (_, i) => sample(i + 2, i < 5));
    assert.equal(solveProbability(1800, 1800), 0.5);
    assert.equal(estimateProblemRating(half).rating, 1800);
    assert.equal(estimateProblemRating(half.map((s) => ({ ...s, strength: 2000 }))).rating, 2000);
    assert.ok(estimateProblemRating(half.map((s, i) => ({ ...s, solved: i < 8 }))).rating! < 1800);
    assert.ok(estimateProblemRating(half.map((s, i) => ({ ...s, solved: i < 2 }))).rating! > 1800);
    assert.ok(Number.isFinite(estimateProblemRating(half.map((s) => ({ ...s, solved: true }))).rating));
    assert.ok(Number.isFinite(estimateProblemRating(half.map((s) => ({ ...s, solved: false }))).rating));
    assert.equal(estimateProblemRating(half.map((s) => ({ ...s, strength: -10000 }))).rating, 800);
    assert.equal(estimateProblemRating(half.map((s) => ({ ...s, strength: 10000 }))).rating, 3500);
});

test('one independent user has one earliest outcome; sparse and malformed samples do not receive fabricated ratings', () => {
    const samples = Array.from({ length: 5 }, (_, i) => sample(i + 2, i < 2));
    const repeated = Array.from({ length: 100 }, () => ({ ...sample(2, true), at: 2000, source: 'practice' as const }));
    assert.deepEqual(estimateProblemRating([...samples, ...repeated]), estimateProblemRating(samples));
    assert.equal(estimateProblemRating(samples.slice(0, 4)).rating, null);
    assert.equal(estimateProblemRating([]).rating, null);
    assert.equal(independentSamples([sample(1, true), { ...sample(2, true), strength: NaN }, { ...sample(3, true), ratedCount: -1 }]).length, 0);
    assert.ok(estimateProblemRating(samples).provisional);
    assert.ok(!estimateProblemRating(Array.from({ length: 20 }, (_, i) => sample(i + 2, i < 10))).provisional);
    assert.equal(estimateProblemRating(samples.map((s) => ({ ...s, ratedCount: 0 }))).effectiveSamples, 1.25);
});

const STATUS = {
    STATUS_WAITING: 0, STATUS_ACCEPTED: 1, STATUS_WRONG_ANSWER: 2, STATUS_TIME_LIMIT_EXCEEDED: 3,
    STATUS_MEMORY_LIMIT_EXCEEDED: 4, STATUS_OUTPUT_LIMIT_EXCEEDED: 5, STATUS_RUNTIME_ERROR: 6,
    STATUS_COMPILE_ERROR: 7, STATUS_SYSTEM_ERROR: 8, STATUS_CANCELED: 9, STATUS_HACKED: 11,
    STATUS_JUDGING: 20, STATUS_COMPILING: 21, STATUS_FETCHED: 22,
};
const at = (time: string | number) => ObjectId.createFromTime(Math.floor(new Date(time).getTime() / 1000));

async function fixtures(client: MongoClient) {
    const database = client.db(`problem_rating_${randomUUID().replaceAll('-', '')}`);
    const docs = database.collection<any>('document');
    const records = database.collection<any>('record');
    const settlements = database.collection<any>('rating.settlement');
    const contests = database.collection<any>('contest');
    const statuses = database.collection<any>('status');
    const locks = new Set<string>();
    const scheduled: any[] = [];
    const state = { failWrite: false, renew: undefined as Function | undefined };
    await database.collection<any>('domain').insertOne({ _id: 'system' });
    await docs.insertMany(Array.from({ length: 6 }, (_, i) => ({
        domainId: 'system', docType: 10, docId: i + 1, pid: `P${i + 1}`, title: `Problem ${i + 1}`,
        owner: 99, maintainer: [98], difficulty: 7, nSubmit: 10000, nAccept: 9000,
    })));
    const id = new ObjectId();
    const beginAt = new Date('2026-10-01T00:00:00Z');
    const endAt = new Date('2026-10-01T05:00:00Z');
    await contests.insertOne({ domainId: 'system', docId: id, pids: [1, 2, 3], owner: 99, maintainer: [97], beginAt, endAt, rule: 'acm' });
    const snapshot = Object.fromEntries(Array.from({ length: 10 }, (_, i) => [i + 10, { internalRating: 1800, rating: 500, ratedCount: 6 }]));
    await settlements.insertOne({ domainId: 'system', contestId: id, active: true, recordType: 'contest', reason: 'settled', stateSnapshot: snapshot });
    await statuses.insertMany(Array.from({ length: 10 }, (_, i) => ({
        domainId: 'system', docId: id, uid: i + 10, attend: true,
        journal: [{ pid: 1, rid: at(beginAt.getTime() + (i + 1) * 10000), status: i < 5 ? 1 : 2 },
            { pid: 2, rid: at(beginAt.getTime() + (i + 1) * 10000), status: 1 }],
    })));
    const frozen = new ObjectId();
    const pending = new ObjectId();
    const live = new ObjectId();
    for (const [contestId, extra] of [[frozen, { lockAt: endAt }], [pending, {}], [live, { endAt: new Date(now + 86400000) }]] as any) {
        await contests.insertOne({ domainId: 'system', docId: contestId, pids: [1], owner: 99, beginAt, endAt, rule: 'acm', ...extra });
        await settlements.insertOne({ domainId: 'system', contestId, active: true, recordType: 'contest', reason: 'settled', stateSnapshot: snapshot });
        await statuses.insertMany(Array.from({ length: 10 }, (_, i) => ({
            domainId: 'system', docId: contestId, uid: i + 10, attend: true,
            journal: [{ pid: 1, rid: at(beginAt.getTime() + (i + 1) * 10000), status: 1 }],
        })));
    }
    await records.insertOne({ domainId: 'system', contest: pending, pid: 1, uid: 10, status: 0 });
    // Remove the live context after its privacy test: practice for problem 4 is outside every contest.
    const practice: any[] = [];
    for (let i = 0; i < 10; i++) {
        const uid = i + 20;
        await settlements.insertMany([
            { domainId: 'system', active: true, recordType: 'user', uid, endTime: new Date('2026-09-01'), after: { internalRating: 1500, ratedCount: 6 } },
            { domainId: 'system', active: true, recordType: 'user', uid, endTime: new Date('2026-11-01'), after: { internalRating: 4000, ratedCount: 20 } },
        ]);
        const first = new Date('2026-10-05T00:00:00Z').getTime() + i * 100000;
        practice.push({ _id: new ObjectId(at(first).toHexString().slice(0, 8) + '0000000000000001'), domainId: 'system', pid: 4, uid, status: 2 });
        practice.push({ _id: new ObjectId(at(first + (i < 5 ? 3600000 : 2 * 86400000)).toHexString().slice(0, 8) + '0000000000000002'), domainId: 'system', pid: 4, uid, status: 1 });
    }
    for (let i = 0; i < 40; i++) practice.push({ _id: new ObjectId(at('2026-10-05T01:00:00Z').toHexString().slice(0, 8) + (i + 10).toString(16).padStart(16, '0')), domainId: 'system', pid: 4, uid: 20, status: 1 });
    for (const [uid, extra] of [[99, {}], [98, {}], [1, {}], [80, { contest: new ObjectId('000000000000000000000000') }],
        [81, { input: ['pretest'] }], [82, { hackTarget: new ObjectId() }], [83, { status: 8 }], [84, { status: 9 }],
        [85, { domainId: 'other' }], [86, { contest: new ObjectId() }]] as any) {
        practice.push({ _id: new ObjectId(at('2026-10-05T00:00:00Z').toHexString().slice(0, 8) + (uid + 200).toString(16).padStart(16, '0')), domainId: 'system', pid: 4, uid, status: 1, ...extra });
    }
    for (let i = 0; i < 4; i++) practice.push({ _id: new ObjectId(at('2026-10-05T00:00:00Z').toHexString().slice(0, 8) + (i + 100).toString(16).padStart(16, '0')), domainId: 'system', pid: 6, uid: i + 30, status: 1 });
    await records.insertMany(practice);
    const coll = new Proxy(docs, { get(target, key) {
        if (key === 'bulkWrite') return async (...args: any[]) => {
            if (state.failWrite) { state.failWrite = false; throw new Error('injected problem write failure'); }
            return (target.bulkWrite as any)(...args);
        };
        const value = Reflect.get(target, key);
        return typeof value === 'function' ? value.bind(target) : value;
    } });
    const models: any = {
        ObjectId, STATUS, Schema: require('schemastery'), db: { collection: (name: string) => database.collection(name) },
        DocumentModel: { coll, TYPE_PROBLEM: 10 }, settlements,
        ProblemModel: { PROJECTION_LIST: ['difficulty'], PROJECTION_PUBLIC: ['difficulty'],
            get: (_domain: string, pid: string) => docs.findOne({ domainId: _domain, docType: 10, ...(Number.isInteger(+pid) ? { docId: +pid } : { pid }) }) },
        RecordModel: { getMulti: (domainId: string, query: any) => records.find({ domainId, ...query }) },
        ContestModel: { isLocked: (contest: any) => contest.lockAt && !contest.unlocked,
            getMulti: (domainId: string) => contests.find({ domainId }),
            getMultiStatus: (domainId: string, query: any) => statuses.find({ domainId, ...query }) },
        DomainModel: { getMulti: () => database.collection('domain').find({}) },
        identityEligible: (status: any) => status.attend && !status.unrank && !status.disqualified,
        ScheduleModel: { count: async () => 0, add: async (task: any) => { scheduled.push(task); } },
        acquire: async (domain: string) => { if (locks.has(domain)) throw new Error(`Rating pool ${domain} is already settling`); locks.add(domain); },
        release: async (domain: string) => { locks.delete(domain); },
        renew: async () => { if (state.renew) await state.renew(); },
    };
    return { database, docs, models, id, records, statuses, locks, state, scheduled };
}

test('database calibration uses frozen strengths, unique outcomes and practice windows; it excludes private or invalid data', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await fixtures(client);
    const service = createProblemRatingService(h.models);
    await service.migrate();
    assert.equal((await h.docs.findOne({ docId: 1 }))?.legacyDifficulty, 7);
    assert.equal((await h.docs.findOne({ docId: 1 }))?.difficulty, 0);
    const result = await calculateProblemRatings(h.models, 'system', undefined, () => {}, async () => {}, now);
    assert.deepEqual(result, { total: 6, rated: 4, pending: 2 });
    const first = await h.docs.findOne({ docId: 1 });
    assert.equal(first.difficulty, 1800);
    assert.equal(first.problemRating.samples, 10, 'live, frozen and pending contests cannot affect public difficulty');
    assert.equal(first.problemRating.solved, 5);
    assert.equal(first.problemRating.contestIds, undefined, 'source contest IDs are not part of public rating metadata');
    const second = await h.docs.findOne({ docId: 2 });
    const third = await h.docs.findOne({ docId: 3 });
    assert.ok(second.difficulty < first.difficulty && first.difficulty < third.difficulty);
    const fourth = await h.docs.findOne({ docId: 4 });
    assert.equal(fourth.difficulty, 1500, 'use historical internal strength, never future/displayed Rating');
    assert.equal(fourth.problemRating.samples, 10, 'repeated, owner, maintainer, pretest, hack and foreign records do not add observations');
    assert.equal(fourth.problemRating.solved, 5, 'AC outside the first 24 hours cannot retroactively solve the observation');
    assert.equal((await h.docs.findOne({ docId: 6 }))?.difficulty, 0);
    assert.equal(await h.docs.countDocuments({ docType: 10, difficulty: 1800 }), 1, 'native difficulty filtering uses the new numeric value');
    // Canceling a past AC changes difficulty without changing the frozen calibration strength.
    await h.statuses.updateOne({ docId: h.id, uid: 10 }, { $set: { 'journal.0.status': 9 } });
    await calculateProblemRatings(h.models, 'system', [1], () => {}, async () => {}, now);
    assert.ok((await h.docs.findOne({ docId: 1 }))!.difficulty > first.difficulty);
    const last = (await h.docs.findOne({ docId: 1 }))!.difficulty;
    await h.records.insertOne({ domainId: 'system', contest: h.id, pid: 1, uid: 11, status: 0 });
    await calculateProblemRatings(h.models, 'system', [1], () => {}, async () => {}, now);
    assert.equal((await h.docs.findOne({ docId: 1 }))!.difficulty, last, 'rejudging retains the last published estimate');
    assert.equal((await h.docs.findOne({ docId: 1 }))?.legacyDifficulty, 7);
});

test('persistent queues survive concurrent judgments, pool locks and write failures', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await fixtures(client);
    const service = createProblemRatingService(h.models);
    await service.mark('system', [4]);
    h.locks.add('system');
    assert.equal(await service.drain('system'), null);
    assert.equal(await h.database.collection('rating.problem.queue').countDocuments({}), 1);
    h.locks.clear();
    await h.models.settlements.insertOne({ domainId: 'system', active: false, pendingCommit: true });
    assert.equal(await service.drain('system'), null);
    assert.equal(h.locks.size, 0);
    await h.models.settlements.deleteMany({ pendingCommit: true });
    h.state.failWrite = true;
    await assert.rejects(service.drain('system'), /problem write failure/);
    assert.equal(h.locks.size, 0);
    let marked = false;
    h.state.renew = async () => { if (!marked) { marked = true; await service.mark('system', [6]); } };
    await service.drain('system');
    assert.equal(await h.database.collection('rating.problem.queue').countDocuments({}), 1, 'new revision is retained');
    h.state.renew = undefined;
    await service.drain('system');
    assert.equal(await h.database.collection('rating.problem.queue').countDocuments({}), 0);
    assert.equal(h.locks.size, 0);
});

test('installation replaces manual scores, registers updates and exposes consistent ratings in templates', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await fixtures(client);
    const events = new Map<string, Function>();
    const scripts = new Map<string, Function>();
    const jobs = new Map<string, Function>();
    const ctx = {
        on: (name: string, fn: Function) => events.set(name, fn),
        inject: async (_deps: string[], fn: Function) => fn({ worker: { addHandler: (name: string, run: Function) => jobs.set(name, run) } }),
        addScript: (name: string, _description: string, _schema: any, run: Function) => scripts.set(name, run),
        logger: { info() {} },
    };
    const service = await installProblemRatingService(ctx, h.models);
    assert.ok(service);
    const edit = { difficulty: 10, problemRating: { rating: 3500 }, title: 'keep title' };
    events.get('problem/before-edit')!(edit, {});
    assert.deepEqual(edit, { title: 'keep title' });
    const added: any = { difficulty: 9 };
    events.get('problem/before-add')!('system', '', 99, 7, added);
    assert.equal(added.difficulty, 0);
    assert.equal(added.legacyDifficulty, 9);
    assert.ok(jobs.has('rating-system.problem'));
    assert.ok(h.scheduled.some((task) => task.subType === 'rating-system.problem' && task.interval[1] === 'minute'));
    assert.ok(h.models.ProblemModel.PROJECTION_LIST.includes('problemRating'));
    assert.ok(!h.models.ProblemModel.PROJECTION_PUBLIC.includes('problemRatingSources'));
    await scripts.get('problem-rating')!({ domainId: 'system', pid: '' }, () => {});
    const problem = await h.docs.findOne({ docId: 1 });
    assert.equal(problemRatingValue(problem), 1800);
    assert.equal(problemRatingColor(problem), 'blue');
    assert.equal(problemRatingValue({ difficulty: 8 }), null);
    const env = new nunjucks.Environment(new nunjucks.FileSystemLoader(path.join(plugin, 'templates')), { autoescape: true });
    const html = env.render('partials/rating_problem_difficulty.html', { pdoc: problem, model: { problem: h.models.ProblemModel }, _: (value: string) => value });
    assert.ok(html.includes('data-problem-rating="1800"'));
    assert.ok(!html.includes('data-problem-rating="7"'));
    const pending = env.render('partials/rating_problem_difficulty.html', { pdoc: { difficulty: 7 }, model: { problem: h.models.ProblemModel }, _: (value: string) => value });
    assert.ok(pending.includes('Awaiting rating'));
    let rendererArgs: any;
    const handler = { renderHTML: (_name: string, args: any) => { rendererArgs = args; } };
    events.get('handler/create/http')!(handler);
    handler.renderHTML('test', {});
    assert.equal(rendererArgs.lib.difficulty(10000, 9000), null, 'legacy acceptance-ratio fallback is disabled');
    await h.docs.updateOne({ docId: 1 }, { $set: { title: '图论 [a]', tag: ['算法'] } });
    await h.docs.insertOne({ domainId: 'system', docType: 10, docId: 99, title: '图论 [a]', tag: ['算法'], difficulty: 1800, hidden: true });
    const query: any = { domainId: 'system', docType: 10, hidden: { $ne: true }, difficulty: 1800,
        docId: { $in: [] }, $and: [{ tag: '算法' }] };
    const searchHandler = { args: { q: '"图论 [a]" category:算法 difficulty:1800' }, queryContext: { fail: true } };
    events.get('problem/list')!(query, searchHandler, []);
    assert.equal(searchHandler.queryContext.fail, false);
    assert.equal(await h.docs.countDocuments(query), 1, 'combined text/rating search retains category and hidden-problem permissions');
});

test('difficulty selection preserves the existing search and replaces the previous difficulty token', () => {
    const dom = new JSDOM('<form id="searchForm"><input name="q" value="图论 category:动态规划 difficulty:8"><button>Search</button></form>'
        + '<select data-problem-rating-filter><option value="">All</option><option value="1600">1600</option><option value="0">Pending</option></select>',
    { runScripts: 'outside-only', pretendToBeVisual: true });
    const form = dom.window.document.querySelector('form')!;
    let submitted = '';
    form.addEventListener('submit', (event) => { event.preventDefault(); submitted = form.querySelector<HTMLInputElement>('input')!.value; });
    dom.window.eval(fs.readFileSync(path.join(plugin, 'public/rating-system.js'), 'utf8'));
    dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
    const select = dom.window.document.querySelector('select')!;
    select.value = '1600'; select.dispatchEvent(new dom.window.Event('change'));
    assert.equal(submitted, '图论 category:动态规划 difficulty:1600');
    select.value = '0'; select.dispatchEvent(new dom.window.Event('change'));
    assert.equal(submitted, '图论 category:动态规划 difficulty:0');
    select.value = ''; select.dispatchEvent(new dom.window.Event('change'));
    assert.equal(submitted, '图论 category:动态规划');
    dom.window.close();
});
