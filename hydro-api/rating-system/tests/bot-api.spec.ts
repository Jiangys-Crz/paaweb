import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { test } from 'node:test';
import { MongoClient, ObjectId } from 'mongodb';
import { MongoMemoryServer } from 'mongodb-memory-server';
import {
    createBotApi, difficultyDistribution, equalBotApiKey, installBotApi, publicBotContest, validBotApiKey, validBotEmail,
} from '../lib/bot-api';
import { PROBLEM_RATING_VERSION } from '../lib/problem-rating';

const key = randomBytes(32).toString('hex');
const rules = { acm: {}, oi: {}, homework: { hidden: true }, hiddenRule: { hidden: true } };
const now = new Date('2026-10-26T12:00:00Z').getTime();
const contest = (extra: any = {}) => ({
    domainId: 'system', docType: 30, docId: new ObjectId(), title: 'System contest', rule: 'acm', assign: [], rated: true,
    beginAt: new Date(now + 3600000), endAt: new Date(now + 7200000), ...extra,
});

test('bot API credentials use bounded constant-time comparison and reject weak/default keys', () => {
    assert.ok(validBotApiKey(key));
    assert.ok(equalBotApiKey(key, key));
    for (const value of [null, [], { $ne: null }, '', key.slice(1), 'a'.repeat(64), 'PLEASE_CHANGE_ME_TO_YOUR_API_KEY_1234567890', 'a'.repeat(257)]) {
        assert.ok(!equalBotApiKey(value, key));
        assert.ok(!equalBotApiKey(key, value));
    }
    assert.ok(!validBotApiKey('12345678'));
    assert.ok(!validBotApiKey(`${key}\n`));
});

test('email validation rejects query objects, arrays, control bytes and unbounded input', () => {
    assert.ok(validBotEmail('Member+site@example.com'));
    assert.ok(validBotEmail('user.name@sub.example.edu.cn'));
    for (const value of [null, ['member@example.com'], { $ne: null }, 'member@example.com\r\nX-Key:test', 'not-email',
        ' member@example.com', 'member@example.com ', `${'a'.repeat(65)}@example.com`, 'a@b', 'a@@example.com']) {
        assert.ok(!validBotEmail(value), JSON.stringify(value));
    }
});

test('contest projection restricts visibility and uses the configured trusted site URL', () => {
    const item = contest();
    const visible = publicBotContest(item, rules, now, 'https://oj.example.com/')!;
    assert.equal(visible.beginAt, item.beginAt.toISOString());
    assert.equal(visible.registrationStart, null);
    assert.equal(visible.registrationEnd, item.endAt.toISOString());
    assert.equal(visible.url, `https://oj.example.com/contest/${item.docId}`);
    assert.equal(publicBotContest(item, rules, now)!.url, `/contest/${item.docId}`);
    assert.equal(publicBotContest(item, rules, now, 'javascript:alert(1)')!.url, `/contest/${item.docId}`);
    assert.equal(publicBotContest(item, rules, now, 'https://user:pass@oj.example.com')!.url, `/contest/${item.docId}`);
    assert.equal(publicBotContest(item, rules, now, 'https://oj.example.com/d/training/?ignored=true#fragment')!.url,
        `https://oj.example.com/d/training/contest/${item.docId}`);
    assert.ok(publicBotContest(contest({ beginAt: new Date(now - 3600000), registrationEnd: new Date(now - 1000) }), rules, now),
        'ongoing contests stay visible after a configured registration deadline');
    for (const changes of [{ domainId: 'another' }, { rule: 'homework' }, { rule: 'hiddenRule' }, { rule: 'unknown' },
        { assign: ['members'] }, { assign: { $ne: [] } }, { hidden: true }, { private: true }, { isHidden: true },
        { visibility: 'private' }, { endAt: new Date(now) }, { beginAt: null }, { docId: '<injected>' }]) {
        assert.equal(publicBotContest(contest(changes), rules, now), null, JSON.stringify(changes));
    }
});

test('difficulty buckets include pending ratings and form a complete partition', () => {
    const result = difficultyDistribution([
        { _id: { difficulty: 800, version: PROBLEM_RATING_VERSION }, count: 2 },
        { _id: { difficulty: 1600, version: PROBLEM_RATING_VERSION }, count: 1 },
        { _id: { difficulty: 3500, version: PROBLEM_RATING_VERSION }, count: 1 },
        { _id: { difficulty: 10 }, count: 1 },
        { _id: { difficulty: 0, version: PROBLEM_RATING_VERSION }, count: 1 },
    ]);
    assert.equal(result.solvedCount, 6);
    assert.deepEqual(result.difficulty.map((bucket) => bucket.count), [2, 0, 1, 0, 1, 2]);
    assert.equal(result.difficulty[0].percent, 33.3);
    assert.ok(difficultyDistribution([]).difficulty.every((bucket) => bucket.percent === 0));
});

test('read-only API isolates database records, counts only committed Rated participation and enforces header auth/rate limits',
    { timeout: 90000 }, async (t) => {
        const server = await MongoMemoryServer.create();
        const client = await MongoClient.connect(server.getUri());
        t.after(async () => { await client.close(); await server.stop(); });
        const database = client.db('bot_api');
        const documents = database.collection<any>('document');
        const records = database.collection<any>('record');
        const settlements = database.collection<any>('rating.settlement');
        let lookups = 0;
        let lastVisualState: any;
        const models: any = {
            Handler: class {},
            db: { collection: (name: string) => database.collection<any>(name),
                ensureIndexes: (target: any, ...indexes: any[]) => target.createIndexes(indexes) },
            ContestModel: { RULES: rules, getMulti: (domainId: string, query: any) => documents.find({ domainId, docType: 30, ...query }) },
            DocumentModel: { coll: documents, TYPE_PROBLEM: 10 },
            RecordModel: { coll: records, RECORD_PRETEST: new ObjectId('000000000000000000000000'),
                RECORD_GENERATE: new ObjectId('000000000000000000000001') },
            UserModel: { getByEmail: async (domainId: string, email: any) => {
                lookups++;
                assert.equal(domainId, 'system');
                assert.equal(typeof email, 'string');
                return email === 'member@example.com' ? { _id: 8, mail: email, password: 'PRIVATE' } : null;
            } },
            STATUS: { STATUS_ACCEPTED: 1 },
            ratingVisual: (state: any) => {
                lastVisualState = state;
                return { rating: state.rating || 0, maxRating: state.maxRating || 0,
                    ratingLevel: state.ratedCount ? 'Skilled' : 'Unrated', ratingColor: state.ratedCount ? '#0000ff' : '#000000' };
            },
        };
        const ongoing = contest({ beginAt: new Date(Date.now() - 10000), endAt: new Date(Date.now() + 3600000) });
        const upcoming = contest({ beginAt: new Date(Date.now() + 7200000), endAt: new Date(Date.now() + 10800000) });
        await documents.insertMany([ongoing, upcoming, contest({ domainId: 'other' }), contest({ assign: ['staff'] }),
            contest({ rule: 'homework' }), contest({ hidden: true }), contest({ isHidden: true }), contest({ private: true }),
            contest({ endAt: new Date(Date.now() - 1000), beginAt: new Date(Date.now() - 2000) }),
            ...[800, 1600, 2400, 0].map((difficulty, index) => ({ domainId: 'system', docType: 10, docId: index + 1,
                difficulty, problemRating: { version: PROBLEM_RATING_VERSION }, hidden: index === 2 })),
            { domainId: 'other', docType: 10, docId: 99, difficulty: 1600, problemRating: { version: PROBLEM_RATING_VERSION } },
        ]);
        await records.insertMany([
            ...[1, 1, 2, 3, 4, 99, 999].map((pid) => ({ domainId: 'system', uid: 8, pid, status: 1 })),
            { domainId: 'other', uid: 8, pid: 5, status: 1 }, { domainId: 'system', uid: 9, pid: 5, status: 1 },
            { domainId: 'system', uid: 8, pid: 5, status: 2 },
            { domainId: 'system', uid: 8, pid: 5, status: 1, contest: models.RecordModel.RECORD_PRETEST },
            { domainId: 'system', uid: 8, pid: 5, status: 1, contest: models.RecordModel.RECORD_GENERATE },
            { domainId: 'system', uid: 8, pid: 5, status: 1, hackTarget: new ObjectId() },
        ]);
        await documents.insertOne({ domainId: 'system', docType: 10, docId: 5, difficulty: 3500,
            problemRating: { version: PROBLEM_RATING_VERSION } });
        await database.collection('domain.user').insertOne({ domainId: 'system', uid: 8,
            rating: 9999, maxRating: 9999, ratedCount: 999, mail: 'PRIVATE', role: 'PRIVATE' });
        const ids = Array.from({ length: 7 }, () => new ObjectId());
        const userHistory = (index: number, extra: any = {}) => ({ domainId: 'system', recordType: 'user', active: true,
            uid: 8, valid: true, qualified: true, contestId: ids[index], settledAt: new Date(now + index * 1000),
            before: { ratedCount: index }, after: { rating: 1600 + index * 20, maxRating: 1700 + index * 20, ratedCount: index + 1 }, ...extra });
        await settlements.insertMany([
            ...ids.map((id, index) => ({ domainId: 'system', contestId: id, recordType: 'contest', active: true,
                reason: index === 4 ? 'fewer_than_5' : 'settled' })),
            userHistory(0), userHistory(0), userHistory(1),
            userHistory(2, { active: false }), userHistory(3, { qualified: false }), userHistory(4),
            userHistory(5, { pendingCommit: true }), userHistory(6, { after: { ratedCount: 6 } }),
            userHistory(1, { domainId: 'other' }), userHistory(1, { uid: 9 }),
        ]);
        const service = createBotApi(models);
        assert.deepEqual((await service.contests()).contests.map((item: any) => item.id), [String(ongoing.docId), String(upcoming.docId)]);
        const profile = await service.profile('member@example.com');
        assert.deepEqual(profile, { rating: 1620, maxRating: 1720, ratedCount: 2, level: 'Skilled', color: '#0000ff', solvedCount: 4,
            difficulty: [
                { label: '800–1100', count: 1, percent: 25 }, { label: '1200–1500', count: 0, percent: 0 },
                { label: '1600–1900', count: 1, percent: 25 }, { label: '2000–2300', count: 0, percent: 0 },
                { label: '2400–3500', count: 1, percent: 25 }, { label: '待评级', count: 1, percent: 25 },
            ] });
        assert.equal(lastVisualState.ratedCount, 2);
        assert.ok(!JSON.stringify(profile).includes('PRIVATE'));
        const before = lookups;
        assert.equal(await service.profile({ $ne: null } as any), null);
        assert.equal(lookups, before);
        assert.equal(await service.profile('unknown@example.com'), null);
        const limited = await Promise.all(Array.from({ length: 15 }, () => service.limit('test', 'client', 5, now)));
        assert.equal(limited.filter(Boolean).length, 5, 'database rate limit remains correct with concurrent requests');

        const routes = new Map<string, any>();
        const context = { Route: (_name: string, route: string, HandlerClass: any) => routes.set(route, HandlerClass),
            inject: (_services: string[], callback: Function) => callback() };
        await installBotApi(context, models, { botApiEnabled: true, botApiKey: key });
        const invoke = async (route: string, querystring = '', headers: any = {}, domainId = 'system') => {
            const HandlerClass = routes.get(`/rating-system/bot/${route}`);
            const handler = new HandlerClass();
            const responseHeaders: any = {};
            Object.assign(handler, { domain: { _id: domainId },
                request: { ip: '127.0.0.1', headers, querystring },
                // Even an administrator's session does not replace the dedicated key.
                user: { hasPriv: () => true }, response: { addHeader: (name: string, value: string) => { responseHeaders[name] = value; } } });
            await handler.get();
            return { ...handler.response, headers: responseHeaders, noCheckPermView: handler.noCheckPermView };
        };
        const authorized = { 'x-api-key': key };
        assert.equal((await invoke('contests')).status, 401);
        assert.equal((await invoke('contests', `apiKey=${key}`)).status, 401, 'a query-string key cannot authorize');
        assert.equal((await invoke('contests', '', { 'x-api-key': [key, key] })).status, 401);
        assert.equal((await invoke('contests', `apiKey=${key}`, authorized)).status, 400);
        assert.equal((await invoke('profile', 'email[$ne]=', authorized)).status, 400);
        assert.equal((await invoke('profile', 'email=member%40example.com&email=other%40example.com', authorized)).status, 400);
        assert.equal((await invoke('profile', 'email=member%40example.com&domainId=other', authorized)).status, 400);
        assert.equal((await invoke('profile', `email=${'x'.repeat(2048)}`, authorized)).status, 400);
        assert.equal((await invoke('profile', 'email=unknown%40example.com', authorized)).body.error, 'not_found');
        assert.equal((await invoke('contests', '', authorized, 'other')).status, 404);
        const ok = await invoke('profile', 'email=member%40example.com', authorized);
        assert.equal(ok.status, 200);
        assert.equal(ok.body.ratedCount, 2);
        assert.equal(ok.headers['Cache-Control'], 'no-store');
        assert.ok(ok.noCheckPermView, 'the key-authorized API can run without a website login');
        const ttl = (await database.collection('rating.bot.rate').indexes()).find((index) => index.name === 'rating_bot_rate_expiry');
        assert.equal(ttl?.expireAfterSeconds, 0);
        await service.limit('profile', key, 1);
        await database.collection('rating.bot.rate').updateMany({ _id: /^profile:/ }, { $set: { count: 60 } });
        const rejected = await invoke('profile', 'email=member%40example.com', authorized);
        assert.equal(rejected.status, 429);
        assert.equal(rejected.headers['Retry-After'], '60');

        const unavailableRoutes = new Map<string, any>();
        await installBotApi({ ...context, Route: (_name: string, route: string, HandlerClass: any) => unavailableRoutes.set(route, HandlerClass) }, models);
        const Disabled = unavailableRoutes.get('/rating-system/bot/contests');
        const disabled = new Disabled();
        Object.assign(disabled, { domain: { _id: 'system' }, request: { ip: '127.0.0.1', headers: authorized, querystring: '' },
            response: { addHeader() {} } });
        await disabled.get();
        assert.equal(disabled.response.status, 503, 'new API is disabled by default');
    });
