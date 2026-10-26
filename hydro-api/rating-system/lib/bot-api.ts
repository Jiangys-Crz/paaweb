import { createHash, timingSafeEqual } from 'node:crypto';
import { PROBLEM_RATING_VERSION } from './problem-rating';

const DOMAIN = 'system';
const QUERY_TIMEOUT_MS = 5000;
const RATE_WINDOW_MS = 60000;
const difficultyBuckets = [
    { label: '800–1100', min: 800, max: 1200 },
    { label: '1200–1500', min: 1200, max: 1600 },
    { label: '1600–1900', min: 1600, max: 2000 },
    { label: '2000–2300', min: 2000, max: 2400 },
    { label: '2400–3500', min: 2400, max: 3501 },
];

export interface BotApiConfig { botApiEnabled?: boolean; botApiKey?: string; botApiSiteUrl?: string }

// Accept randomly generated hex/base64/url-safe credentials, but reject empty, short,
// repeated and obvious example credentials. Generate 32 random bytes for production.
export function validBotApiKey(value: unknown): value is string {
    return typeof value === 'string' && value.length >= 32 && value.length <= 256
        && /^[A-Za-z0-9._~+/-]+={0,2}$/.test(value) && new Set(value).size >= 8
        && !/please|change.?me|example|password|replace.?me|your.?api.?key/i.test(value);
}

export function equalBotApiKey(provided: unknown, expected: unknown): boolean {
    if (!validBotApiKey(expected) || typeof provided !== 'string' || provided.length > 256) return false;
    // Hash both values so timingSafeEqual always receives equal-length buffers,
    // including for an incorrect credential with a different length.
    const hash = (value: string) => createHash('sha256').update(value).digest();
    return timingSafeEqual(hash(provided), hash(expected));
}

export function validBotEmail(value: unknown): value is string {
    if (typeof value !== 'string' || value.length > 254 || value !== value.trim()) return false;
    return /^[^\s<>()[\]\\,;:"\x00-\x1f\x7f@]{1,64}@[a-z\d](?:[a-z\d.-]{0,251}[a-z\d])?\.[a-z]{2,63}$/i.test(value);
}

function iso(value: unknown): string | null {
    if (!(value instanceof Date) && typeof value !== 'string' && typeof value !== 'number') return null;
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toISOString() : null;
}

function trustedSiteUrl(value: string) {
    if (!value) return '';
    try {
        const url = new URL(value);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return '';
        return `${url.origin}${url.pathname.replace(/\/$/, '')}`;
    } catch { return ''; }
}

export function publicBotContest(contest: any, rules: any, now = Date.now(), siteUrl = '') {
    const beginAt = iso(contest.beginAt);
    const endAt = iso(contest.endAt);
    const id = String(contest.docId || '');
    if (contest.domainId !== DOMAIN || !/^[a-f\d]{24}$/i.test(id) || !beginAt || !endAt
        || beginAt >= endAt || new Date(endAt).getTime() <= now
        || !rules[contest.rule] || rules[contest.rule].hidden || contest.rule === 'homework'
        || contest.hidden || contest.isHidden || contest.private || contest.visibility === 'private'
        || (contest.assign !== undefined && (!Array.isArray(contest.assign) || contest.assign.length))) return null;
    const registrationStart = iso(contest.registrationStart ?? contest.registrationBeginAt ?? contest.registration_start);
    // Native Hydro allows a previously unregistered user to join until the contest ends.
    // A missing opening date is deliberately null rather than an invented creation date.
    const registrationEnd = iso(contest.registrationEnd ?? contest.registrationEndAt ?? contest.registration_end) || endAt;
    return {
        id, title: String(contest.title || '').slice(0, 300), beginAt, endAt,
        registrationStart, registrationEnd, url: `${trustedSiteUrl(siteUrl)}/contest/${id}`,
        rated: (contest.rated ?? contest.is_rated) === true,
    };
}

export function difficultyDistribution(rows: any[]) {
    const counts = [...difficultyBuckets.map((bucket) => ({ label: bucket.label, count: 0 })), { label: '待评级', count: 0 }];
    for (const row of rows) {
        const value = row._id?.version === PROBLEM_RATING_VERSION ? row._id?.difficulty : null;
        const index = Number.isInteger(value)
            ? difficultyBuckets.findIndex((bucket) => value >= bucket.min && value < bucket.max) : -1;
        counts[index >= 0 ? index : counts.length - 1].count += Math.max(0, row.count || 0);
    }
    const solvedCount = counts.reduce((total, bucket) => total + bucket.count, 0);
    return {
        solvedCount,
        difficulty: counts.map((bucket) => ({ ...bucket, percent: solvedCount ? Math.round(bucket.count * 1000 / solvedCount) / 10 : 0 })),
    };
}

/** Read-only model adapter; all domain and collection selectors are server controlled. */
export function createBotApi(models: any, config: BotApiConfig = {}) {
    const { db, ContestModel, DocumentModel, RecordModel, UserModel, STATUS, ratingVisual } = models;
    const settlements = db.collection('rating.settlement');
    const rates = db.collection('rating.bot.rate');

    async function limit(scope: string, identifier: string, max: number, now = Date.now()) {
        const period = Math.floor(now / RATE_WINDOW_MS);
        const digest = createHash('sha256').update(identifier).digest('hex');
        const _id = `${scope}:${period}:${digest}`;
        try {
            await rates.findOneAndUpdate({ _id, count: { $lt: max } }, {
                $inc: { count: 1 }, $setOnInsert: { expireAt: new Date((period + 2) * RATE_WINDOW_MS) },
            }, { upsert: true, returnDocument: 'after', maxTimeMS: QUERY_TIMEOUT_MS });
            return true;
        } catch (error: any) {
            if (error?.code === 11000) return false;
            throw error;
        }
    }

    async function contests(now = Date.now()) {
        const rules = Object.keys(ContestModel.RULES).filter((rule) => rule !== 'homework' && !ContestModel.RULES[rule].hidden);
        const docs = await ContestModel.getMulti(DOMAIN, {
            endAt: { $gt: new Date(now) }, rule: { $in: rules },
            hidden: { $ne: true }, isHidden: { $ne: true }, private: { $ne: true }, visibility: { $ne: 'private' },
            $or: [{ assign: { $exists: false } }, { assign: { $size: 0 } }],
        }).project({
            domainId: 1, docId: 1, title: 1, beginAt: 1, endAt: 1, rule: 1, assign: 1,
            hidden: 1, isHidden: 1, private: 1, visibility: 1, rated: 1, is_rated: 1,
            registrationStart: 1, registrationEnd: 1, registrationBeginAt: 1, registrationEndAt: 1,
            registration_start: 1, registration_end: 1,
        }).sort({ beginAt: 1, docId: 1 }).limit(100).maxTimeMS(QUERY_TIMEOUT_MS).toArray();
        return { contests: docs.map((doc: any) => publicBotContest(doc, ContestModel.RULES, now, config.botApiSiteUrl)).filter(Boolean) };
    }

    async function profile(email: string) {
        // Never pass arrays, objects, regular expressions or arbitrary query operators to a model.
        if (!validBotEmail(email)) return null;
        const user = await UserModel.getByEmail(DOMAIN, email);
        if (!user || !Number.isSafeInteger(user._id) || user._id <= 1) return null;
        const uid = user._id;
        const [stored, history, solved] = await Promise.all([
            db.collection('domain.user').findOne({ domainId: DOMAIN, uid }, {
                projection: { rating: 1, maxRating: 1, max_rating: 1, internalRating: 1, internal_rating: 1 },
                maxTimeMS: QUERY_TIMEOUT_MS,
            }),
            settlements.aggregate([
                { $match: { domainId: DOMAIN, uid, recordType: 'user', active: true, valid: true, qualified: true,
                    pendingCommit: { $ne: true }, $expr: { $eq: ['$after.ratedCount', { $add: ['$before.ratedCount', 1] }] } } },
                { $sort: { settledAt: 1, _id: 1 } },
                { $group: { _id: '$contestId', after: { $last: '$after' }, settledAt: { $last: '$settledAt' } } },
                { $lookup: { from: 'rating.settlement', let: { contestId: '$_id' }, pipeline: [
                    { $match: { domainId: DOMAIN, recordType: 'contest', active: true, reason: 'settled',
                        pendingCommit: { $ne: true }, $expr: { $eq: ['$contestId', '$$contestId'] } } },
                    { $limit: 1 }, { $project: { _id: 1 } },
                ], as: 'summary' } },
                { $match: { 'summary.0': { $exists: true } } },
                { $sort: { 'after.ratedCount': -1, settledAt: -1, _id: -1 } },
                { $group: { _id: null, ratedCount: { $sum: 1 }, after: { $first: '$after' } } },
            ], { maxTimeMS: QUERY_TIMEOUT_MS }).toArray(),
            RecordModel.coll.aggregate([
                { $match: { domainId: DOMAIN, uid, status: STATUS.STATUS_ACCEPTED,
                    contest: { $nin: [RecordModel.RECORD_PRETEST, RecordModel.RECORD_GENERATE] },
                    hackTarget: { $exists: false }, canceled: { $ne: true }, disqualified: { $ne: true } } },
                { $group: { _id: '$pid' } },
                { $lookup: { from: DocumentModel.coll.collectionName, let: { pid: '$_id' }, pipeline: [
                    { $match: { domainId: DOMAIN, docType: DocumentModel.TYPE_PROBLEM,
                        $expr: { $eq: ['$docId', '$$pid'] } } },
                    { $limit: 1 }, { $project: { difficulty: 1, 'problemRating.version': 1 } },
                ], as: 'problem' } },
                { $unwind: '$problem' },
                { $group: { _id: { difficulty: '$problem.difficulty', version: '$problem.problemRating.version' }, count: { $sum: 1 } } },
            ], { maxTimeMS: QUERY_TIMEOUT_MS }).toArray(),
        ]);
        const ratedCount = history[0]?.ratedCount || 0;
        // Read a committed settlement snapshot when available. In standalone MongoDB a
        // staged recalculation may have written domain.user before activating its records.
        const visual = ratingVisual({ ...(history[0]?.after || stored || {}), ratedCount, rated_count: ratedCount });
        return {
            rating: visual.rating, maxRating: visual.maxRating, ratedCount,
            level: visual.ratingLevel, color: visual.ratingColor, ...difficultyDistribution(solved),
        };
    }
    return { limit, contests, profile };
}

function json(handler: any, status: number, body: any) {
    handler.response.status = status;
    handler.response.template = null;
    handler.response.type = 'application/json; charset=utf-8';
    handler.response.body = body;
    handler.response.addHeader('Cache-Control', 'no-store');
    handler.response.addHeader('X-Content-Type-Options', 'nosniff');
}

export async function installBotApi(ctx: any, models: any, config: BotApiConfig = {}) {
    // Maintenance/test runtimes need not expose the web service.
    if (typeof ctx.Route !== 'function' || !models.Handler) return;
    const service = createBotApi(models, { ...config, botApiSiteUrl: config.botApiSiteUrl || models.SystemModel?.get('server.url') || '' });
    await ctx.inject(['db'], async () => {
        await models.db.ensureIndexes(models.db.collection('rating.bot.rate'),
            { key: { expireAt: 1 }, name: 'rating_bot_rate_expiry', expireAfterSeconds: 0 });
        await models.db.ensureIndexes(models.RecordModel.coll,
            { key: { domainId: 1, uid: 1, status: 1, pid: 1 }, name: 'rating_bot_solved' });
    });

    class BotApiHandler extends models.Handler {
        // Authentication belongs solely to the dedicated API key, including when guests
        // cannot view the Hydro site. Session privileges never bypass it.
        noCheckPermView = true;

        async run(kind: 'contests' | 'profile') {
            try {
                if (this.domain?._id !== DOMAIN) return json(this, 404, { error: 'not_found' });
                if (config.botApiEnabled !== true || !validBotApiKey(config.botApiKey)) {
                    return json(this, 503, { error: 'bot_api_unavailable' });
                }
                if (!await service.limit('request', String(this.request.ip || 'unknown').slice(0, 128), 90)) {
                    this.response.addHeader('Retry-After', '60');
                    return json(this, 429, { error: 'rate_limited' });
                }
                if (!equalBotApiKey(this.request.headers['x-api-key'], config.botApiKey)) {
                    return json(this, 401, { error: 'unauthorized' });
                }
                const querystring = this.request.querystring || '';
                if (typeof querystring !== 'string' || querystring.length > 1024) {
                    return json(this, 400, { error: 'invalid_request' });
                }
                const params = new URLSearchParams(querystring);
                const allowed = kind === 'profile' ? ['email'] : [];
                if ([...params.keys()].some((key) => !allowed.includes(key))
                    || [...params.keys()].some((key) => params.getAll(key).length !== 1)
                    || (kind === 'profile' && !validBotEmail(params.get('email')))) {
                    return json(this, 400, { error: 'invalid_request' });
                }
                if (!await service.limit(kind, config.botApiKey!, kind === 'profile' ? 60 : 180)) {
                    this.response.addHeader('Retry-After', '60');
                    return json(this, 429, { error: 'rate_limited' });
                }
                const body = kind === 'contests' ? await service.contests() : await service.profile(params.get('email')!);
                return body ? json(this, 200, body) : json(this, 404, { error: 'not_found' });
            } catch {
                // Do not log email addresses, credentials, user documents or database errors.
                json(this, 503, { error: 'bot_api_unavailable' });
            }
        }
    }
    class BotContestsHandler extends BotApiHandler { async get() { return this.run('contests'); } }
    class BotProfileHandler extends BotApiHandler { async get() { return this.run('profile'); } }
    ctx.Route('rating_system_bot_contests', '/rating-system/bot/contests', BotContestsHandler);
    ctx.Route('rating_system_bot_profile', '/rating-system/bot/profile', BotProfileHandler);
}
