import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { format } from 'node:util';
import { Script } from 'node:vm';
import { buildSync } from 'esbuild';
import { Db, MongoClient, MongoServerError, ObjectId } from 'mongodb';
import { MongoMemoryReplSet, MongoMemoryServer } from 'mongodb-memory-server';
import { test } from 'node:test';
import { RATING_VERSION } from '../lib/rating';

// Load the real plugin and Rating algorithm without starting Hydro or accessing its configured DB.
const pluginDirectory = path.resolve(__dirname, '..');
const bundled = buildSync({
    entryPoints: [path.join(pluginDirectory, 'index.ts')],
    bundle: true,
    write: false,
    platform: 'node',
    format: 'cjs',
    external: ['hydrooj'],
}).outputFiles[0].text;
const loadPlugin = new Script(`(function(require, module, exports, __dirname) { ${bundled}\n})`, {
    filename: path.join(pluginDirectory, 'index.test.cjs'),
}).runInThisContext();

const CONTEST_TYPE = 30;
const STATUS = {
    STATUS_ACCEPTED: 1,
    STATUS_WAITING: 20,
    STATUS_FETCHED: 21,
    STATUS_COMPILING: 22,
    STATUS_JUDGING: 23,
};

async function harness(client: MongoClient) {
    const database = client.db(`rating_test_${randomUUID().replaceAll('-', '')}`);
    const messages: any[][] = [];
    const reports: string[] = [];
    const logs: string[] = [];
    const persistenceErrors = new Map<string, Error>();
    const failures = { remainingUserWrites: undefined as number | undefined, activation: false, message: false };
    const scripts = new Map<string, Function>();
    const workers = new Map<string, Function>();
    const collection = (name: string) => new Proxy(database.collection<any>(name), {
        get(target, key) {
            if (key === 'updateMany' && name === 'rating.settlement') {
                return async (filter: any, update: any, options: any) => {
                    if (failures.activation && filter.pendingCommit && update.$set?.active === true) {
                        failures.activation = false;
                        throw new Error('injected activation failure');
                    }
                    return target.updateMany(filter, update, options);
                };
            }
            if (key === 'updateOne') {
                return async (filter: any, update: any, options: any) => {
                    // An ordinary transaction error must remain a failure, never a staged commit.
                    const error = name === 'rating.lock' && options?.session && persistenceErrors.get(filter._id);
                    if (error) throw error;
                    if (name === 'domain.user' && !options?.session && failures.remainingUserWrites !== undefined) {
                        if (failures.remainingUserWrites === 0) {
                            failures.remainingUserWrites = undefined;
                            throw new Error('injected interrupted user write');
                        }
                        failures.remainingUserWrites--;
                    }
                    return target.updateOne(filter, update, options);
                };
            }
            const value = Reflect.get(target, key);
            return typeof value === 'function' ? value.bind(target) : value;
        },
    });
    const facade = {
        ObjectId,
        STATUS,
        Schema: require('schemastery'),
        db: {
            client,
            collection,
            ensureIndexes: (target: any, ...indexes: any[]) => target.createIndexes(indexes),
        },
        DocumentModel: { TYPE_CONTEST: CONTEST_TYPE, TYPE_PROBLEM: 10, coll: database.collection('document') },
        ProblemModel: {
            PROJECTION_LIST: ['difficulty'], PROJECTION_PUBLIC: ['difficulty'],
            get: (domainId: string, pid: string) => database.collection('document').findOne({ domainId, docType: 10, docId: +pid }),
        },
        DomainModel: { getMulti: () => database.collection<any>('domain').find({}) },
        ContestModel: {
            isLocked: (tdoc: any) => tdoc.lockAt && !tdoc.unlocked,
            getMulti: (domainId: string) => database.collection('document').find({ domainId, docType: CONTEST_TYPE }),
            getMultiStatus: (domainId: string, filter: any) => database.collection('document.status').find({ domainId, ...filter }),
            RULES: {
                oi: { stat: (_tdoc: any, journal: any[]) => ({ score: journal.reduce((sum, item) => sum + item.score, 0) }) },
            },
        },
        RecordModel: { getMulti: (domainId: string, filter: any) => database.collection('record').find({ domainId, ...filter }) },
        UserModel: { getListForRender: async () => ({}), _deleteUserCache() {} },
        MessageModel: { FLAG_UNREAD: 1, send: async (...args: any[]) => {
            if (failures.message) { failures.message = false; throw new Error('injected message failure'); }
            messages.push(args);
        } },
        ScheduleModel: { count: async () => 1 },
    };
    const module = { exports: {} as any };
    loadPlugin((name: string) => name === 'hydrooj' ? facade : require(name), module, module.exports, pluginDirectory);
    const context = {
        on() {},
        inject: async (_dependencies: string[], callback: Function) => callback({
            worker: { addHandler: (name: string, handler: Function) => workers.set(name, handler) },
        }),
        logger: {
            info: (...args: any[]) => logs.push(format(...args)),
            error: (...args: any[]) => logs.push(format(...args)),
        },
        addScript: (name: string, _description: string, _schema: any, run: Function) => scripts.set(name, run),
    };
    await module.exports.apply(context);
    const run = (options: { domainId?: string, full?: boolean, contestId?: string, tid?: string } = {}) => scripts.get('rating')!(
        { domainId: '', full: false, ...options },
        ({ message }: { message: string }) => reports.push(message),
    );
    return { database, run, messages, reports, logs, workers, persistenceErrors, failures };
}

async function seedContest(database: Db, domainId: string, day = 0) {
    const docId = new ObjectId();
    const beginAt = new Date(new Date('2025-01-01T10:00:00Z').getTime() + day * 86400000);
    const endAt = new Date(beginAt.getTime() + 2 * 3600000);
    await database.collection<any>('domain').updateOne({ _id: domainId }, { $setOnInsert: { _id: domainId } }, { upsert: true });
    await database.collection('document').insertOne({
        domainId,
        docType: CONTEST_TYPE,
        docId,
        title: `${domainId} rated contest`,
        beginAt,
        endAt,
        rule: 'oi',
        pids: [1],
        rated: true,
        is_rated: true,
        ratingVersion: RATING_VERSION,
        contestType: 'IO',
        scoreRule: { score: -1 },
        publisherId: 1,
        contestAdminIds: [2],
    });
    await database.collection('document.status').insertMany([3, 4, 5, 6].map((uid) => ({
        domainId,
        docId,
        uid,
        attend: true,
        journal: [{
            rid: ObjectId.createFromTime(beginAt.getTime() / 1000 + uid),
            pid: 1,
            status: STATUS.STATUS_ACCEPTED,
            score: 100 - uid,
        }],
    })));
    return docId;
}

async function assertSettled(database: Db, domainId: string, docId: ObjectId, notificationCount = 5) {
    const summaries = await database.collection('rating.settlement').find({ domainId, active: true, recordType: 'contest' }).toArray();
    assert.equal(summaries.length, 1);
    assert.equal(summaries[0].reason, 'settled');
    assert.equal(summaries[0].participantCount, 5);
    assert.deepEqual(summaries[0].ratedContestAdminUids, [2]);
    const records = await database.collection('rating.settlement').find({ domainId, active: true, recordType: 'user' }).sort({ uid: 1 }).toArray();
    assert.deepEqual(records.map((record) => record.uid), [2, 3, 4, 5, 6]);
    assert.ok(records.every((record) => record.after.ratedCount === 1));
    const users = await database.collection('domain.user').find({ domainId }).sort({ uid: 1 }).toArray();
    assert.equal(users.length, 5);
    for (const [index, user] of users.entries()) {
        assert.equal(user.ratedCount, 1);
        assert.equal(user.rating, records[index].after.rating);
        assert.equal(user.internalRating, records[index].after.internalRating);
    }
    const contest = await database.collection('document').findOne({ domainId, docId });
    assert.equal(contest?.ratingSettled, true);
    assert.equal(contest?.rating_settled, true);
    assert.equal(await database.collection('rating.settlement').countDocuments({ domainId, pendingCommit: true }), 0);
    assert.equal(await database.collection('rating.notification').countDocuments({ domainId, status: 'sent' }), notificationCount);
    assert.equal(await database.collection('rating.lock').countDocuments({}), 0);
    return { users, records };
}

test('standalone MongoDB settles with either retryWrites setting and reruns without duplicate Rating', { timeout: 180000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    t.after(() => server.stop());
    for (const retryWrites of [true, false]) {
        // eslint-disable-next-line no-await-in-loop
        await t.test(`retryWrites=${retryWrites}`, async (subtest) => {
            const client = await MongoClient.connect(server.getUri(), { retryWrites });
            subtest.after(() => client.close());
            const h = await harness(client);
            const contestId = await seedContest(h.database, 'system');
            assert.equal(await h.run(), true);
            assert.ok(h.reports.some((line) => line.includes('MongoDB 单机模式')));
            assert.ok(!h.reports.some((line) => line.includes('failed:')));
            const first = await assertSettled(h.database, 'system', contestId);
            assert.ok(first.records.every((record) => record.notificationSent));
            assert.equal(h.messages.length, 5);

            assert.equal(await h.run({ domainId: 'system' }), true);
            assert.deepEqual(await assertSettled(h.database, 'system', contestId), first);
            assert.equal(await h.database.collection('rating.settlement').countDocuments({}), 6);
            assert.equal(h.messages.length, 5);

            assert.equal(await h.run({ full: true }), true);
            await assertSettled(h.database, 'system', contestId, 10);
            assert.equal(h.messages.length, 10, 'an explicit full replay sends notifications even for identical results');
            assert.ok(h.messages.slice(5).every((message) => message[2].includes('Rating 重算通知')));
            assert.equal(await h.run(), true);
            assert.equal(h.messages.length, 10, 'ordinary incremental reruns must not repeat recalculation notices');
        });
    }
});

test('failed domains release locks, preserve failure status, and do not stop later domains', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => {
        await client.close();
        await server.stop();
    });
    const h = await harness(client);
    const brokenContest = await seedContest(h.database, 'broken');
    const healthyContest = await seedContest(h.database, 'healthy');
    const failure = new MongoServerError({ message: 'not authorized to update Rating', code: 13, codeName: 'Unauthorized' });
    h.persistenceErrors.set('broken', failure);

    assert.equal(await h.run(), false);
    assert.ok(h.reports.some((line) => line.startsWith('Rating domain broken failed:')));
    assert.ok(!h.reports.some((line) => line.includes('broken: MongoDB 单机模式')));
    await assertSettled(h.database, 'healthy', healthyContest);
    assert.equal(await h.database.collection('domain.user').countDocuments({ domainId: 'broken' }), 0);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ domainId: 'broken' }), 0);
    assert.equal((await h.database.collection('document').findOne({ docId: brokenContest }))?.ratingSettled, undefined);

    await assert.rejects(h.run({ domainId: 'broken' }), (error) => error === failure);
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
    await assert.rejects(h.workers.get('rating-system.daily')!({}), /Rating daily calculation failed/);
    assert.ok(!h.logs.some((line) => line.includes('daily calculation finished')));
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);

    h.persistenceErrors.clear();
    assert.equal(await h.run(), true);
    await assertSettled(h.database, 'broken', brokenContest);
    assert.equal(h.messages.length, 10);
});

test('replica sets retain real transaction commits', { timeout: 180000 }, async (t) => {
    const server = await MongoMemoryReplSet.create({ replSet: { count: 1 } });
    const client = await MongoClient.connect(server.getUri(), { monitorCommands: true });
    t.after(async () => {
        await client.close();
        await server.stop();
    });
    const commands: string[] = [];
    client.on('commandSucceeded', ({ commandName }) => commands.push(commandName));
    const h = await harness(client);
    const contestId = await seedContest(h.database, 'system');
    assert.equal(await h.run(), true);
    await assertSettled(h.database, 'system', contestId);
    assert.ok(commands.includes('commitTransaction'));
    assert.ok(!h.reports.some((line) => line.includes('MongoDB 单机模式')));
    assert.equal(h.messages.length, 5);
    assert.equal(await h.run({ contestId: String(contestId) }), true);
    await assertSettled(h.database, 'system', contestId, 10);
    assert.equal(h.messages.length, 10);
});

test('nested server causes enable fallback, while unrelated wrapped code 20 errors do not', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => {
        await client.close();
        await server.stop();
    });
    const h = await harness(client);
    const rawContest = await seedContest(h.database, 'raw');
    h.persistenceErrors.set('raw', new MongoServerError({
        message: 'Transaction numbers are only allowed on a replica set member or mongos',
        code: 20,
        codeName: 'IllegalOperation',
    }));
    assert.equal(await h.run({ domainId: 'raw' }), true);
    await assertSettled(h.database, 'raw', rawContest);

    const nestedContest = await seedContest(h.database, 'nested');
    const originalError = Object.assign(new Error('wrapped server response'), {
        errorResponse: {
            code: 20,
            codeName: 'IllegalOperation',
            errmsg: 'Transaction numbers are only allowed on a replica set member or mongos',
        },
    });
    h.persistenceErrors.set('nested', new Error('outer failure', { cause: originalError }));
    assert.equal(await h.run({ domainId: 'nested' }), true);
    await assertSettled(h.database, 'nested', nestedContest);

    await seedContest(h.database, 'unrelated');
    const unrelated = new MongoServerError({ message: 'Unsupported operation in this configuration', code: 20 });
    const wrapperMessage = 'This MongoDB deployment does not support retryable writes. '
        + 'Please add retryWrites=false to your connection string.';
    const wrapped = Object.assign(new Error(wrapperMessage), { originalError: unrelated });
    // A malformed cyclic error chain must terminate instead of hanging the script.
    (unrelated as any).cause = wrapped;
    h.persistenceErrors.set('unrelated', wrapped);
    await assert.rejects(h.run({ domainId: 'unrelated' }), (error) => error === wrapped);
    assert.ok(!h.reports.some((line) => line.includes('unrelated: MongoDB 单机模式')));
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ domainId: 'unrelated' }), 0);
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
});

test('an interrupted standalone commit is rebuilt safely on the next run', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => {
        await client.close();
        await server.stop();
    });
    const h = await harness(client);
    const contestId = await seedContest(h.database, 'system');
    h.failures.remainingUserWrites = 1;
    assert.equal(await h.run(), false);
    assert.equal(await h.database.collection('domain.user').countDocuments({ domainId: 'system', ratedCount: 1 }), 1);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ pendingCommit: true, active: false }), 6);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ active: true }), 0);
    assert.equal((await h.database.collection('document').findOne({ docId: contestId }))?.ratingSettled, undefined);
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
    assert.equal(h.messages.length, 0);

    assert.equal(await h.run(), true);
    assert.ok(h.reports.some((line) => line.includes('检测到未完成的提交流水')));
    await assertSettled(h.database, 'system', contestId);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({}), 6);
    assert.equal(h.messages.length, 5);
    assert.equal(await h.run(), true);
    assert.equal(h.messages.length, 5);
});

async function activeRecords(database: Db, contestId: ObjectId) {
    return database.collection('rating.settlement').find({ contestId, active: true }).sort({ recordType: 1, uid: 1 }).toArray();
}

async function userStates(database: Db) {
    return database.collection('domain.user').find({ domainId: 'system' }).sort({ uid: 1 }).project({
        _id: 0, uid: 1, rating: 1, internalRating: 1, ratedCount: 1, maxRating: 1,
    }).toArray();
}

test('targeted replay restores historical Rating, cascades only settled dependents and notifies on each explicit run', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const firstId = await seedContest(h.database, 'system');
    const targetId = await seedContest(h.database, 'system', 1);
    const nextId = await seedContest(h.database, 'system', 2);
    assert.equal(await h.run(), true);
    const originalFirst = await activeRecords(h.database, firstId);
    const originalTarget = await activeRecords(h.database, targetId);
    assert.equal(h.messages.length, 15);
    const uncalculated = await seedContest(h.database, 'system', 3);
    await h.database.collection('document.status').updateOne({ docId: targetId, uid: 3 }, { $set: { 'journal.0.score': 1 } });
    assert.equal(await h.run({ contestId: String(targetId) }), true, 'domain is inferred from the contest ID');
    assert.deepEqual(await activeRecords(h.database, firstId), originalFirst, 'earlier history is immutable');
    assert.notDeepEqual(await activeRecords(h.database, targetId), originalTarget);
    assert.equal((await activeRecords(h.database, uncalculated)).length, 0);
    assert.equal(h.messages.length, 25, 'selected and dependent participants are both notified');
    assert.ok(h.messages.slice(15).every((message) => message[2].includes('Rating 重算通知')));
    const first = originalFirst.filter((row) => row.recordType === 'user');
    const target = (await activeRecords(h.database, targetId)).filter((row) => row.recordType === 'user');
    const next = (await activeRecords(h.database, nextId)).filter((row) => row.recordType === 'user');
    for (const row of target) assert.deepEqual(row.before, first.find((prior) => prior.uid === row.uid)!.after);
    for (const row of next) assert.deepEqual(row.before, target.find((prior) => prior.uid === row.uid)!.after);
    assert.ok((await userStates(h.database)).every((state) => state.ratedCount === 3));
    const expected = await harness(client);
    await expected.database.collection<any>('domain').insertOne({ _id: 'system' });
    await expected.database.collection('document').insertMany(await h.database.collection('document').find({ docId: { $ne: uncalculated } }).toArray());
    await expected.database.collection('document.status').insertMany(await h.database.collection('document.status').find({ docId: { $ne: uncalculated } }).toArray());
    assert.equal(await expected.run({ full: true }), true);
    assert.deepEqual(await userStates(h.database), await userStates(expected.database), 'targeted replay matches a clean history replay');
    const beforeRepeat = await userStates(h.database);
    assert.equal(await h.run({ domainId: 'system', tid: String(targetId).toUpperCase() }), true);
    assert.deepEqual(await userStates(h.database), beforeRepeat, 'identical reruns never stack the Rating delta');
    assert.equal(h.messages.length, 35, 'an identical explicit rerun still sends new notices');
    assert.equal(await h.run(), true);
    assert.equal(h.messages.length, 40, 'no parameters only settles the previously uncalculated contest');
    assert.ok((await userStates(h.database)).every((state) => state.ratedCount === 4));
    assert.equal(await h.run(), true);
    assert.equal(h.messages.length, 40);
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
});

test('target arguments are validated before writes and a never-settled target does not settle other contests', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const target = await seedContest(h.database, 'system');
    const other = await seedContest(h.database, 'system', 1);
    await assert.rejects(h.run({ contestId: 'bad-id' }), /24 位/);
    await assert.rejects(h.run({ contestId: String(new ObjectId()) }), /找不到/);
    await assert.rejects(h.run({ domainId: 'wrong-domain', contestId: String(target) }), /找不到/);
    await assert.rejects(h.run({ contestId: String(target), tid: String(other) }), /不同的比赛/);
    await assert.rejects(h.run({ contestId: String(target), full: true }), /full=true/);
    await h.database.collection('document').updateOne({ docId: target }, { $set: { endAt: new Date(Date.now() + 86400000) } });
    await assert.rejects(h.run({ contestId: String(target) }), /尚未结束/);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({}), 0);
    assert.equal(h.messages.length, 0);
    await h.database.collection('document').updateOne({ docId: target }, { $set: { endAt: new Date('2025-01-01T12:00:00Z') } });
    assert.equal(await h.run({ contestId: String(target) }), true);
    assert.equal((await activeRecords(h.database, other)).length, 0);
    assert.equal(h.messages.length, 5);
    assert.ok((await userStates(h.database)).every((state) => state.ratedCount === 1));
});

test('recalculation removes invalidated contributions, rebuilds later maxima/counts and notifies removed users', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const target = await seedContest(h.database, 'system');
    const later = await seedContest(h.database, 'system', 1);
    await h.run();
    await h.database.collection('document.status').updateOne({ docId: target, uid: 3 }, { $set: { unrank: true } });
    assert.equal(await h.run({ contestId: String(target) }), true);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ contestId: target, active: true, recordType: 'user' }), 0);
    assert.equal((await h.database.collection('document').findOne({ docId: target }))?.ratingSettled, undefined);
    assert.ok((await userStates(h.database)).every((state) => state.ratedCount === 1));
    const laterRows = (await activeRecords(h.database, later)).filter((row) => row.recordType === 'user');
    assert.ok(laterRows.every((row) => row.before.ratedCount === 0));
    assert.equal(h.messages.length, 20);
    assert.equal(h.messages.filter((message) => message[2].includes('不再计入你的 Rating')).length, 5);
    assert.equal(await h.run(), true);
    assert.equal(h.messages.length, 20);
});

test('a failed dependent replay leaves committed history untouched and retries notifications only after a successful commit', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const target = await seedContest(h.database, 'system');
    const later = await seedContest(h.database, 'system', 1);
    await h.run();
    const before = await userStates(h.database);
    const records = await activeRecords(h.database, target);
    await h.database.collection('record').insertOne({ domainId: 'system', contest: later, status: STATUS.STATUS_WAITING });
    await assert.rejects(h.run({ contestId: String(target) }), /pending judgments/);
    assert.deepEqual(await userStates(h.database), before);
    assert.deepEqual(await activeRecords(h.database, target), records);
    assert.equal(h.messages.length, 10);
    await h.database.collection('record').deleteMany({});
    h.failures.remainingUserWrites = 1;
    await assert.rejects(h.run({ contestId: String(target) }), /injected interrupted user write/);
    assert.equal(h.messages.length, 10, 'an incomplete staged write sends nothing');
    const staged = await h.database.collection('rating.settlement').find({ pendingCommit: true }).toArray();
    assert.equal(staged.length, 12);
    const runId = staged[0].recalculation.notificationRunId;
    assert.equal(await h.run(), true, 'the next ordinary run resumes the exact requested replay');
    assert.deepEqual(await userStates(h.database), before);
    assert.equal(h.messages.length, 20);
    assert.equal(await h.database.collection('rating.notification').countDocuments({ notificationRunId: runId, status: 'sent' }), 10);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ pendingCommit: true }), 0);
    assert.equal(await h.run(), true);
    assert.equal(h.messages.length, 20, 'recovery notices are not sent twice');
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
});

test('staged replay recovery retains removal notices after superseding old settlements', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const target = await seedContest(h.database, 'system');
    await seedContest(h.database, 'system', 1);
    await h.run();
    await h.database.collection('document.status').updateOne({ docId: target, uid: 3 }, { $set: { unrank: true } });
    h.failures.activation = true;
    await assert.rejects(h.run({ contestId: String(target) }), /activation failure/);
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ active: true }), 0);
    assert.equal(h.messages.length, 10);
    assert.equal(await h.run({ contestId: String(target) }), true);
    assert.equal(h.messages.length, 20);
    assert.equal(h.messages.filter((message) => message[2].includes('不再计入你的 Rating')).length, 5);
    assert.ok((await userStates(h.database)).every((state) => state.ratedCount === 1));
    assert.equal(await h.database.collection('rating.settlement').countDocuments({ pendingCommit: true }), 0);
    await h.run();
    assert.equal(h.messages.length, 20);
});

test('failed recalculation messages retry without repeating the other delivered messages', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const target = await seedContest(h.database, 'system');
    await h.run();
    h.failures.message = true;
    assert.equal(await h.run({ contestId: String(target) }), true);
    assert.equal(h.messages.length, 9);
    assert.equal(await h.database.collection('rating.notification').countDocuments({ status: 'failed' }), 1);
    await h.run();
    assert.equal(h.messages.length, 10);
    assert.equal(await h.database.collection('rating.notification').countDocuments({ status: 'failed' }), 0);
    await h.run();
    assert.equal(h.messages.length, 10);
});

test('the Rating script rebuilds each problem difficulty automatically after settlement and explicit replay', { timeout: 90000 }, async (t) => {
    const server = await MongoMemoryServer.create();
    const client = await MongoClient.connect(server.getUri());
    t.after(async () => { await client.close(); await server.stop(); });
    const h = await harness(client);
    const contestId = await seedContest(h.database, 'system');
    await h.database.collection('document.status').insertOne({
        domainId: 'system', docId: contestId, uid: 7, attend: true,
        journal: [{ rid: ObjectId.createFromTime(new Date('2025-01-01T10:00:07Z').getTime() / 1000), pid: 1, status: 1, score: 93 }],
    });
    await h.database.collection('document').insertOne({ domainId: 'system', docType: 10, docId: 1, owner: 1, difficulty: 8 });
    assert.equal(await h.run(), true);
    let problem = await h.database.collection('document').findOne({ docType: 10, docId: 1 });
    assert.equal(problem?.difficulty, 1300, 'internal strengths are used instead of newbie display bonuses');
    assert.equal(problem?.problemRating.samples, 5, 'the synthetic contest admin is not a solving sample');
    await h.database.collection('document.status').updateOne({ docId: contestId, uid: 3 }, { $set: { unrank: true } });
    assert.equal(await h.run({ contestId: String(contestId) }), true);
    problem = await h.database.collection('document').findOne({ docType: 10, docId: 1 });
    assert.equal(problem?.difficulty, 0, 'insufficient independent samples become pending rather than reverting to old difficulty');
    assert.equal(problem?.problemRating.samples, 4);
    assert.equal(await h.database.collection('rating.problem.queue').countDocuments({}), 0);
    assert.equal(await h.database.collection('rating.lock').countDocuments({}), 0);
});
