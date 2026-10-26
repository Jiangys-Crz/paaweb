import path from 'node:path';
import {
    ConnectionHandler, ContestModel, ContestNotAttendedError, ContestNotEndedError, ContestNotLiveError, ContestScoreboardHiddenError, Context, db, DocumentModel,
    DomainModel, Handler, InvalidTokenError, MessageModel, ObjectId, PERM, PRIV, ProblemModel, RecordModel,
    ScheduleModel, Schema, STATUS, SystemModel, Types, UserModel, ValidationError } from 'hydrooj';
import {
    calculate, displayRating, INITIAL_INTERNAL_RATING,
    initialState, RatedInput, RATING_VERSION,
    RatingState } from './lib/rating';
import { buildContestBoard } from './lib/contest-board';
import { prepareRecordConnection, prepareRecordSearch } from './lib/record-search';
import { installProblemRatingService } from './lib/problem-rating-service';
import { BotApiConfig, installBotApi } from './lib/bot-api';

export const Config = Schema.object({
    botApiEnabled: Schema.boolean().default(false).description('启用 QQ 机器人只读 API（仅 system 域）'),
    botApiKey: Schema.string().default('').role('secret').description('QQ 机器人 API 密钥；请使用至少 32 个随机字符，与官网后台配置一致'),
    botApiSiteUrl: Schema.string().default('').description('比赛链接的 Hydro 公开根地址；留空使用 Hydro server.url'),
});

let problemRatings: Awaited<ReturnType<typeof installProblemRatingService>>;

const DOMAIN_USER_FIELDS = ['internalRating', 'internal_rating', 'rating', 'ratedCount', 'rated_count', 'maxRating', 'max_rating'];
const RATING_SCHEDULE = 'rating-system.daily';
const DEFAULT_RATING_MIN = 0;
// Covers all standard tiers, including Supreme, while keeping the edit form numeric.
const DEFAULT_RATING_MAX = 10000;
const TRANSACTION_SUPPORT_ERROR = /transaction|replica set|mongos/i;
const ratingUsers = db.collection('domain.user');
const settlements = (db as any).collection('rating.settlement');
const locks = (db as any).collection('rating.lock');
// A normal settlement and each explicit recalculation have their own notification claims.
const ratingNotifications = (db as any).collection('rating.notification');
// After settlement, users may only edit the contest description. `unlocked` is a runtime scoreboard
// state changed by Hydro's dedicated unlock action rather than contest configuration.
const SETTLED_MUTABLE_FIELDS = new Set(['content', 'unlocked']);
// beginAt/endAt survive an edit round-trip through a float hour duration, so compare them loosely.
const SETTLED_TIME_FIELDS = new Set(['beginAt', 'endAt', 'start_time', 'end_time']);
const RATING_NOTIFICATION_QUEUED = 'queued';
const RATING_NOTIFICATION_BATCH = 500;
const RATING_NOTIFICATION_PENDING = 'pending';
const RATING_NOTIFICATION_SENT = 'sent';
const RATING_NOTIFICATION_FAILED = 'failed';
// A claim in one of these states may not be queued again for the same result.
const RATING_NOTIFICATION_OPEN = [
    RATING_NOTIFICATION_QUEUED, RATING_NOTIFICATION_PENDING, RATING_NOTIFICATION_SENT,
];
const SETTLED_MARKER_SET = {
    ratingSettled: true, rating_settled: true,
};
const SETTLED_MARKER_UNSET = {
    ratingSettled: '', rating_settled: '',
    ratingSettledAt: '', rating_settled_at: '',
    ratingSettledBatch: '', rating_settled_batch: '',
};
const finalStatuses = new Set([
    STATUS.STATUS_ACCEPTED,
    STATUS.STATUS_WRONG_ANSWER,
    STATUS.STATUS_TIME_LIMIT_EXCEEDED,
    STATUS.STATUS_MEMORY_LIMIT_EXCEEDED,
    STATUS.STATUS_OUTPUT_LIMIT_EXCEEDED,
    STATUS.STATUS_RUNTIME_ERROR,
    STATUS.STATUS_COMPILE_ERROR,
    STATUS.STATUS_ETC,
    STATUS.STATUS_HACKED,
    STATUS.STATUS_FORMAT_ERROR,
    STATUS.STATUS_HACK_SUCCESSFUL,
    STATUS.STATUS_HACK_UNSUCCESSFUL,
]);

const asNumber = (value: any, fallback: number) => typeof value === 'number' && Number.isFinite(value) ? Math.trunc(value) : fallback;

export function getRatingState(source: any): RatingState {
    const state = initialState();
    state.internalRating = asNumber(source?.internalRating ?? source?.internal_rating, INITIAL_INTERNAL_RATING);
    state.ratedCount = Math.max(0, asNumber(source?.ratedCount ?? source?.rated_count, 0));
    state.rating = Math.max(0, asNumber(source?.rating, displayRating(state.internalRating, state.ratedCount)));
    state.maxRating = Math.max(state.rating, asNumber(source?.maxRating ?? source?.max_rating, state.rating));
    return state;
}

function stateFields(state: RatingState): Record<string, number> {
    return {
        internalRating: state.internalRating,
        internal_rating: state.internalRating,
        rating: state.rating,
        ratedCount: state.ratedCount,
        rated_count: state.ratedCount,
        maxRating: state.maxRating,
        max_rating: state.maxRating,
    };
}

function graphemes(value: string): [string, string] {
    const text = String(value || '');
    try {
        const segments = Array.from(new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text), (i) => i.segment);
        return [segments[0] || '', segments.slice(1).join('')];
    } catch {
        const chars = Array.from(text);
        return [chars[0] || '', chars.slice(1).join('')];
    }
}

export interface RatingVisual {
    rating: number;
    maxRating: number;
    ratedCount: number;
    ratingLevel: string;
    ratingStatus: string;
    ratingColor: string;
    ratingBold: boolean;
    ratingFirstColor: string;
    ratingRestColor: string;
    ratingFirst: string;
    ratingRest: string;
    ratingLevelFirst: string;
    ratingLevelRest: string;
    ratingDisplayFirst: string;
    ratingDisplayRest: string;
    ratingStyle: string;
    ratingMaxStyle: string;
}

export function ratingVisual(source: any): RatingVisual {
    const state = getRatingState(source);
    const rating = state.rating;
    let ratingLevel = 'Unrated';
    let ratingColor = '#000000';
    let ratingBold = false;
    const ratingStatus = state.ratedCount === 0 ? 'Unrated'
        : state.ratedCount < 6 ? `Rating in progress ${state.ratedCount}/6` : 'Rated';
    if (state.ratedCount > 0) {
        ratingBold = true;
        if (rating < 1200) [ratingLevel, ratingColor] = ['Novice', '#808080'];
        else if (rating < 1400) [ratingLevel, ratingColor] = ['Trainee', '#008000'];
        else if (rating < 1600) [ratingLevel, ratingColor] = ['Practitioner', '#03a89e'];
        else if (rating < 1900) [ratingLevel, ratingColor] = ['Skilled', '#0000ff'];
        else if (rating < 2100) [ratingLevel, ratingColor] = ['Proficient', '#aa00aa'];
        else if (rating < 2300) [ratingLevel, ratingColor] = ['Elite', '#ff8c00'];
        else if (rating < 2400) [ratingLevel, ratingColor] = ['Core Elite', '#ff8c00'];
        else if (rating < 2600) [ratingLevel, ratingColor] = ['Apex', '#ff0000'];
        else if (rating < 3000) [ratingLevel, ratingColor] = ['Veteran', '#ff0000'];
        else if (rating < 4000) {
            ratingLevel = 'Legend';
            ratingColor = '#ff0000';
        } else {
            ratingLevel = 'Supreme';
            ratingColor = '#000000';
        }
    }
    const [ratingFirst, ratingRest] = graphemes(String(source?.uname || source?.displayName || ''));
    const [ratingDisplayFirst, ratingDisplayRest] = graphemes(String(source?.displayName || source?.uname || ''));
    const [ratingLevelFirst, ratingLevelRest] = graphemes(ratingLevel);
    const ratingFirstColor = ['Veteran', 'Legend'].includes(ratingLevel) ? '#000000'
        : ratingLevel === 'Supreme' ? '#ff0000' : ratingColor;
    const ratingRestColor = ['Veteran', 'Legend'].includes(ratingLevel) ? '#ff0000'
        : ratingLevel === 'Supreme' ? '#000000' : ratingColor;
    const ratingStyle = `color:${ratingColor};${ratingBold ? 'font-weight:700;' : ''}`;
    const ratingMaxStyle = ratingStyle;
    return {
        rating,
        maxRating: state.maxRating,
        ratedCount: state.ratedCount,
        ratingLevel,
        ratingStatus,
        ratingColor,
        ratingBold,
        ratingFirstColor,
        ratingRestColor,
        ratingFirst,
        ratingRest,
        ratingLevelFirst,
        ratingLevelRest,
        ratingDisplayFirst,
        ratingDisplayRest,
        ratingStyle,
        ratingMaxStyle,
    };
}

function decorate(source: any): any {
    if (!source) return source;
    const visual = ratingVisual(source);
    if (source._dudoc) return source;
    Object.assign(source, visual);
    return source;
}

function installUserFields() {
    const User = (UserModel as any).User;
    if (!User?.prototype || User.prototype.__ratingSystemInstalled) return;
    Object.defineProperty(User.prototype, '__ratingSystemInstalled', { value: true });
    const getters = ['rating', 'maxRating', 'ratedCount', 'internalRating'];
    for (const key of getters) {
        Object.defineProperty(User.prototype, key, {
            configurable: true,
            enumerable: true,
            get() { return getRatingState(this._dudoc)[key]; },
        });
    }
    for (const key of Object.keys(ratingVisual({}))) {
        if (getters.includes(key)) continue;
        Object.defineProperty(User.prototype, key, {
            configurable: true,
            enumerable: true,
            get() { return ratingVisual({ ...this._dudoc, uname: this.uname, _id: this._id, priv: this.priv, role: this.role })[key]; },
        });
    }
    const originalGetFields = User.prototype.getFields;
    User.prototype.getFields = function getFields(type = 'public') {
        return Array.from(new Set(originalGetFields.call(this, type).concat(DOMAIN_USER_FIELDS, [
            'maxRating', 'ratedCount', 'ratingLevel', 'ratingColor', 'ratingBold',
            'ratingFirstColor', 'ratingRestColor', 'ratingFirst', 'ratingRest', 'ratingStyle', 'ratingMaxStyle',
            'ratingDisplayFirst', 'ratingDisplayRest', 'ratingLevelFirst', 'ratingLevelRest', 'ratingStatus',
        ])));
    };
}

function patchUserRender() {
    const model: any = UserModel;
    if (model.__ratingSystemRenderInstalled) return;
    model.__ratingSystemRenderInstalled = true;
    const original = model.getListForRender.bind(model);
    model.getListForRender = async (...args: any[]) => {
        const result = await original(...args);
        const domainId = args[0];
        const uids = args[1] || [];
        const domainDocs = domainId
            ? await DomainModel.getDomainUserMulti(domainId, uids)
                .project({ uid: 1, ...Object.fromEntries(DOMAIN_USER_FIELDS.map((key) => [key, 1])) }).toArray()
            : [];
        const byUid = Object.fromEntries(domainDocs.map((doc: any) => [doc.uid, doc]));
        for (const uid of uids) decorate(Object.assign(result[uid] ||= { _id: uid }, byUid[uid] || {}));
        return result;
    };
}

function scoreRuleFor(rule: string): { contestType: string, sort: Record<string, number> } | null {
    if (rule === 'acm') return { contestType: 'XCPC', sort: { accept: -1, time: 1 } };
    if (rule === 'oi') return { contestType: 'IO', sort: { score: -1 } };
    if (rule === 'ioi' || rule === 'strictioi') return { contestType: 'IOI', sort: { score: -1 } };
    return null;
}

function sameRule(a: any, b: any): boolean {
    return JSON.stringify(a || null) === JSON.stringify(b || null);
}

function validScoreRule(contestType: string, scoreRule: any): scoreRule is Record<string, number> {
    if (!scoreRule || typeof scoreRule !== 'object' || Array.isArray(scoreRule)) return false;
    const entries = Object.entries(scoreRule);
    if (!entries.length || entries.some(([, value]) => value !== 1 && value !== -1)) return false;
    if (contestType === 'XCPC') {
        return entries[0]?.[0] === 'accept' && entries[0]?.[1] === -1
            && entries[1]?.[0] === 'time' && entries[1]?.[1] === 1;
    }
    return entries[0]?.[0] === 'score' && entries[0]?.[1] === -1;
}

function organizerIds(tdoc: any) {
    const publisherId = tdoc.publisher_id ?? tdoc.publisherId;
    // The contest editor's maintainer field is the live source of truth. The mirrored fields
    // only remain as a fallback for documents written by older plugin versions.
    const contestAdminIds = Array.isArray(tdoc.maintainer)
        ? tdoc.maintainer
        : (tdoc.contest_admin_ids ?? tdoc.contestAdminIds);
    if (!Number.isSafeInteger(publisherId) || publisherId < 1
        || !Array.isArray(contestAdminIds)
        || contestAdminIds.some((uid: any) => !Number.isSafeInteger(uid) || uid < 1)) return null;
    return { publisherId, contestAdminIds: [...new Set<number>(contestAdminIds)].sort((a, b) => a - b) };
}

function organizerRole(uid: number, organizers: ReturnType<typeof organizerIds>): string | null {
    if (uid === organizers?.publisherId) return 'publisher';
    // Contest admins are the per-contest maintainer list only. Global/domain roles do not apply here.
    if (organizers?.contestAdminIds.includes(uid)) return 'contest_admin';
    return null;
}

function contestStart(tdoc: any): Date {
    return new Date(tdoc?.beginAt ?? tdoc?.start_time);
}

function contestEnd(tdoc: any): Date {
    return new Date(tdoc?.endAt ?? tdoc?.end_time);
}

async function handlerContest(h: Handler): Promise<any> {
    const handler: any = h;
    if (handler.tdoc) return handler.tdoc;
    const rawTid = h.args?.tid;
    if (!rawTid || !ObjectId.isValid(String(rawTid))) return null;
    return await ContestModel.get(h.domain._id, new ObjectId(String(rawTid)));
}

function shouldUseOrdinaryContestPermissions(h: Handler, tdoc: any): boolean {
    return !!tdoc && tdoc.rule !== 'homework' && !ContestModel.isDone(tdoc) && !h.user.own(tdoc);
}

function useOrdinaryContestPermissions(h: Handler) {
    // The request user is a private per-request User instance. Intersecting its permissions here
    // prevents platform/domain roles from bypassing contest rules without changing the account.
    h.user.perm &= PERM.PERM_DEFAULT;
    h.user.priv &= PRIV.PRIV_DEFAULT;
}

async function restrictContestPermissionsBeforeEnd(h: Handler) {
    // `tid` is also used by training and homework routes. Only contest handlers
    // (including their problem/record subclasses) should resolve it as a contest.
    let prototype = Object.getPrototypeOf(h);
    while (prototype && ![
        'ContestDetailBaseHandler', 'ContestEditHandler', 'ContestCodeHandler',
    ].includes(prototype.constructor?.name)) {
        prototype = Object.getPrototypeOf(prototype);
    }
    if (!prototype) return;
    const tdoc = await handlerContest(h);
    if (shouldUseOrdinaryContestPermissions(h, tdoc)) useOrdinaryContestPermissions(h);
}

async function restrictElevatedContestAccessBeforeEnd(h: Handler) {
    const tdoc = await handlerContest(h);
    if (!shouldUseOrdinaryContestPermissions(h, tdoc)) return;
    throw new ContestNotEndedError(h.domain._id, tdoc.docId);
}

function removeContestRecordManagement(h: Handler) {
    useOrdinaryContestPermissions(h);
}

function selfRecordFilter(h: Handler): boolean {
    const value = h.args?.uidOrName;
    if (value === undefined || value === null || value === '') return false;
    return String(value) === String(h.user._id) || String(value) === h.user.uname;
}

async function restrictContestRecordListBeforeEnd(h: Handler) {
    const tdoc = await handlerContest(h);
    if (!shouldUseOrdinaryContestPermissions(h, tdoc)) return;
    if (!selfRecordFilter(h)) throw new ContestNotEndedError(h.domain._id, tdoc.docId);
    removeContestRecordManagement(h);
}

async function restrictContestRecordDetailBeforeEnd(h: Handler) {
    const rdoc: any = (h as any).rdoc;
    if (!rdoc?.contest || String(rdoc.contest).startsWith('0'.repeat(23))) return;
    const tdoc = await ContestModel.get(h.domain._id, rdoc.contest);
    if (!shouldUseOrdinaryContestPermissions(h, tdoc)) return;
    useOrdinaryContestPermissions(h);
    if (rdoc.uid !== h.user._id) throw new ContestNotEndedError(h.domain._id, tdoc.docId);
}

async function restrictHiddenScoreboardBeforeEnd(h: Handler) {
    const tdoc: any = (h as any).tdoc;
    if (!shouldUseOrdinaryContestPermissions(h, tdoc)) return;
    if (!ContestModel.canShowScoreboard.call(h, tdoc, false)) {
        throw new ContestScoreboardHiddenError(tdoc.docId);
    }
}

async function restrictPrivateContestFileBeforeEnd(h: Handler) {
    const handler: any = h;
    const tdoc = handler.tdoc;
    if (!shouldUseOrdinaryContestPermissions(h, tdoc) || (h.args?.type || 'private') !== 'private') return;
    if (!handler.tsdoc?.attend) throw new ContestNotAttendedError(h.domain._id, tdoc.docId);
    if (!ContestModel.isOngoing(tdoc, handler.tsdoc)) throw new ContestNotLiveError(h.domain._id, tdoc.docId);
}

async function restrictContestPrintBeforeEnd(h: Handler) {
    const handler: any = h;
    const tdoc = handler.tdoc;
    if (!shouldUseOrdinaryContestPermissions(h, tdoc)) return;
    if (['allocatePrintTask', 'updatePrintTask'].includes(h.args?.operation)) {
        throw new ContestNotEndedError(h.domain._id, tdoc.docId);
    }
    if (!handler.tsdoc?.attend) throw new ContestNotAttendedError(h.domain._id, tdoc.docId);
}

function protectContestRecordConnections(connection: ConnectionHandler) {
    const h = connection as unknown as Handler;
    const handler: any = h;
    // The same row template serves ordinary lists and contest lists over WebSocket.
    handler.ratingBoardRecords = String(h.args?.ratingBoard || h.request?.query?.ratingBoard || '') === '1';
    const name = handler.constructor?.name;
    if (!['RecordMainConnectionHandler', 'RecordDetailConnectionHandler'].includes(name)
        || typeof handler.prepare !== 'function') return;
    const originalPrepare = handler.prepare.bind(handler);
    handler.prepare = async (...args: any[]) => {
        if (name === 'RecordDetailConnectionHandler') {
            const rid = handler.args?.rid;
            if (rid && ObjectId.isValid(String(rid))) {
                const rdoc: any = await RecordModel.get(h.domain._id, new ObjectId(String(rid)));
                if (rdoc?.contest && !String(rdoc.contest).startsWith('0'.repeat(23))) {
                    const tdoc = await ContestModel.get(h.domain._id, rdoc.contest);
                    if (shouldUseOrdinaryContestPermissions(h, tdoc)) useOrdinaryContestPermissions(h);
                    if (shouldUseOrdinaryContestPermissions(h, tdoc) && rdoc.uid !== h.user._id) {
                        throw new ContestNotEndedError(h.domain._id, tdoc.docId);
                    }
                }
            }
        } else {
            const tdoc = await handlerContest(h);
            if (shouldUseOrdinaryContestPermissions(h, tdoc)) useOrdinaryContestPermissions(h);
            if (shouldUseOrdinaryContestPermissions(h, tdoc) && !selfRecordFilter(h)) {
                throw new ContestNotEndedError(h.domain._id, tdoc.docId);
            }
        }
        const result = await originalPrepare(...args);
        if (name === 'RecordMainConnectionHandler' && handler.ratingBoardRecords && handler.tdoc && handler.tdoc.rule !== 'homework') {
            await prepareRecordConnection(handler, { ProblemModel, RecordModel, UserModel });
        }
        return result;
    };
}

async function restrictContestDiscussionBeforeEnd(h: Handler) {
    const vnode: any = (h as any).vnode;
    if (!vnode || vnode.type !== DocumentModel.TYPE_CONTEST
        || ContestModel.isDone(vnode) || h.user.own(vnode)) return;
    throw new ContestNotEndedError(h.domain._id, vnode.docId || vnode.id);
}

async function restrictContestDiscussionCreation(payload: any) {
    if (payload?.parentType !== DocumentModel.TYPE_CONTEST || !payload.parentId) return;
    const tdoc = await ContestModel.get(payload.domainId, payload.parentId);
    const privileged = tdoc && [tdoc.owner, ...(tdoc.maintainer || [])].includes(payload.owner);
    if (tdoc && !ContestModel.isDone(tdoc) && !privileged) {
        throw new ContestNotEndedError(payload.domainId, payload.parentId);
    }
}

function hideRestrictedContestDiscussions(h: Handler, body: any) {
    const filter = (ddocs: any[], vndict: any) => ddocs.filter((ddoc: any) => {
        if (ddoc?.parentType !== DocumentModel.TYPE_CONTEST) return true;
        const vnode = vndict?.[DocumentModel.TYPE_CONTEST]?.[String(ddoc.parentId)];
        if (!vnode) return false;
        return ContestModel.isDone(vnode) || h.user.own(vnode);
    });
    if (Array.isArray(body?.ddocs) && body?.vndict) body.ddocs = filter(body.ddocs, body.vndict);
    for (const column of body?.contents || []) {
        for (const section of column?.sections || []) {
            if (section?.[0] !== 'discussion' || !Array.isArray(section?.[1])) continue;
            const [ddocs, vndict] = section[1];
            if (Array.isArray(ddocs) && vndict) section[1][0] = filter(ddocs, vndict);
        }
    }
}

function contestRated(tdoc: any): boolean {
    const expected = scoreRuleFor(tdoc?.rule);
    const contestType = tdoc?.contestType ?? tdoc?.contest_type;
    const scoreRule = tdoc?.scoreRule ?? tdoc?.score_rule;
    const version = tdoc?.ratingVersion ?? tdoc?.rating_version;
    if (!expected || tdoc?.rated !== true || tdoc?.ratingUnrated === true) return false;
    return version === RATING_VERSION
        && tdoc.is_rated === true && !!organizerIds(tdoc)
        && contestType === expected.contestType
        && validScoreRule(contestType, scoreRule)
        && contestStart(tdoc).getTime() < contestEnd(tdoc).getTime();
}

/** Settlement order of a contest: its start time, with the document id as a stable tie breaker. */
function contestOrder(tdoc: any): [number, string] {
    return [contestStart(tdoc).getTime(), String(tdoc?.docId ?? tdoc?._id ?? '')];
}

function orderBefore(a: [number, string], b: [number, string]): boolean {
    if (a[0] !== b[0]) return a[0] < b[0];
    return a[1] < b[1];
}

function sameSettledValue(key: string, next: any, current: any): boolean {
    if (SETTLED_TIME_FIELDS.has(key)) {
        const nextTime = new Date(next).getTime();
        const currentTime = new Date(current).getTime();
        if (Number.isFinite(nextTime) && Number.isFinite(currentTime)) {
            return Math.abs(nextTime - currentTime) < 1000;
        }
    }
    return sameRule(next, current);
}

/**
 * Contests whose Rating has already been calculated. The marker on the contest document is the
 * primary source; settlement records written before the marker existed (or by a run that was
 * interrupted between committing the records and writing the marker) are accepted as well.
 */
async function activeSettledContests(domainId: string): Promise<{ ids: Set<string>, docIds: any[] }> {
    const docs = await settlements.find({
        domainId, recordType: 'contest', active: true, reason: 'settled',
    }).project({ contestId: 1 }).toArray();
    const docIds = docs.map((doc: any) => doc.contestId).filter((id: any) => !!id);
    return { ids: new Set(docIds.map((id: any) => String(id))), docIds };
}

function contestSettled(tdoc: any, settledIds: Set<string>): boolean {
    if (!tdoc?.docId) return false;
    if (tdoc.ratingSettled === true || tdoc.rating_settled === true) return true;
    return settledIds.has(String(tdoc.docId));
}

async function contestRatingSettled(tdoc: any): Promise<boolean> {
    if (!tdoc?.docId) return false;
    if (tdoc.ratingSettled === true || tdoc.rating_settled === true) return true;
    if (!tdoc.domainId) return false;
    const summary = await settlements.findOne({
        domainId: tdoc.domainId, contestId: tdoc.docId, recordType: 'contest', active: true, reason: 'settled',
    }, { projection: { _id: 1 } });
    return !!summary;
}

async function updateSettledContestDescription(h: Handler) {
    const tdoc = await handlerContest(h);
    if (!tdoc || !await contestRatingSettled(tdoc)) return undefined;
    if (shouldUseOrdinaryContestPermissions(h, tdoc)) {
        throw new ContestNotEndedError(h.domain._id, tdoc.docId);
    }
    if (h.user.own(tdoc)) h.checkPerm(PERM.PERM_EDIT_CONTEST_SELF);
    else h.checkPerm(PERM.PERM_EDIT_CONTEST);
    const raw: any = { ...(h.args || {}), ...(h.request?.body || {}) };
    if (raw.operation !== 'update') {
        throw new ValidationError('operation', null, 'Rating 已结算的比赛只允许修改比赛说明');
    }
    if (typeof raw.content !== 'string') throw new ValidationError('content');
    const content = Types.Content[0](raw.content);
    if (!Types.Content[1](content)) throw new ValidationError('content');
    await ContestModel.edit(h.domain._id, tdoc.docId, { content });
    (h as any).tdoc = tdoc;
    h.response.redirect = h.url('contest_detail', { tid: tdoc.docId });
    return 'after';
}

async function rejectSettledContestManagementMutation(h: Handler) {
    const tdoc: any = (h as any).tdoc || await handlerContest(h);
    if (tdoc && await contestRatingSettled(tdoc)) {
        throw new ValidationError('operation', null, 'Rating 已结算的比赛只允许修改比赛说明');
    }
}

async function markSettledContests(domainId: string, docIds: any[], clearOthers: boolean, batchId?: ObjectId) {
    const filter: any = { domainId, docType: DocumentModel.TYPE_CONTEST };
    if (docIds.length) {
        const now = new Date();
        await DocumentModel.coll.updateMany(
            { ...filter, docId: { $in: docIds } },
            {
                $set: {
                    ...SETTLED_MARKER_SET,
                    ratingSettledAt: now, rating_settled_at: now,
                    ratingSettledBatch: batchId, rating_settled_batch: batchId,
                },
            },
        );
    }
    if (clearOthers) {
        const cleared = { ...filter, docId: { $nin: docIds }, ratingSettled: { $exists: true } };
        await DocumentModel.coll.updateMany(cleared, { $unset: SETTLED_MARKER_UNSET });
        await DocumentModel.coll.updateMany(
            { ...filter, docId: { $nin: docIds }, rating_settled: { $exists: true } },
            { $unset: SETTLED_MARKER_UNSET },
        );
    }
}

/** Apply the Rating of a settled contest to the running state, so later contests see it. */
function applySettlement(states: Map<number, RatingState>, records: any[], touched: Set<number>) {
    for (const record of records) {
        states.set(record.uid, { ...record.after });
        touched.add(record.uid);
    }
}

function ratingNotificationId(domainId: string, contestId: any, uid: number, runId?: string): string {
    const id = contestId?.toHexString?.() || String(contestId || '');
    return `${domainId}:${id}:${uid}${runId ? `:${runId}` : ''}`;
}

/** Identifies the result within one notification run, including retry/recovery attempts. */
function ratingNotificationSignature(record: any): string {
    return [
        record?.before?.rating, record?.ratingDelta, record?.after?.rating,
        record?.after?.maxRating, record?.after?.ratedCount,
    ].map((value) => String(asNumber(value, 0))).join('|') + (record.recalculationRemoved ? '|removed' : '');
}

function ratingNotificationMessage(record: any): string {
    const delta = Number(record.ratingDelta) || 0;
    const change = delta > 0 ? `Rating 增加 ${delta} 分` : delta < 0 ? `Rating 减少 ${Math.abs(delta)} 分` : 'Rating 没有变化';
    const contestTitle = String(record.contestTitle || record.contestId || 'Rated contest');
    if (record.recalculationRemoved) return [
        'Rating 重算通知', `比赛：${contestTitle}`,
        '本次重算后，该场比赛不再计入你的 Rating。',
        `当前 Rating：${record.after.rating}`, `历史最高 Rating：${record.after.maxRating}`,
    ].join('\n');
    return [
        record.recalculation ? 'Rating 重算通知' : 'Rating 结算通知',
        `比赛：${contestTitle}`,
        `${change}（${record.before.rating} → ${record.after.rating}）`,
        `历史最高 Rating：${record.after.maxRating}`,
    ].join('\n');
}

/**
 * Retries of the same run share claims. An explicitly requested recalculation gets a new run ID
 * and therefore notifies again even when the Rating numbers are unchanged.
 */
async function queueRatingNotifications(
    domainId: string, contestRecords: any[], options: any = {},
) {
    const userRecords = contestRecords.filter((record) => record.recordType === 'user');
    if (!userRecords.length) return;
    // One lookup for the whole batch: a replayed settlement that already sent its notification
    // must not queue anything again.
    const claims = new Map<string, any>();
    for (let index = 0; index < userRecords.length; index += RATING_NOTIFICATION_BATCH) {
        const ids = userRecords.slice(index, index + RATING_NOTIFICATION_BATCH)
            .map((record) => ratingNotificationId(domainId, record.contestId, record.uid, record.recalculation?.notificationRunId));
        // eslint-disable-next-line no-await-in-loop
        const docs = await ratingNotifications.find({ _id: { $in: ids } }, {
            ...options, projection: { signature: 1, status: 1 },
        }).toArray();
        for (const doc of docs) claims.set(doc._id, doc);
    }
    for (const record of userRecords) {
        const _id = ratingNotificationId(domainId, record.contestId, record.uid, record.recalculation?.notificationRunId);
        const signature = ratingNotificationSignature(record);
        const existing = claims.get(_id);
        // Already queued, in flight or delivered for this very result: nothing to do.
        if (existing && existing.signature === signature && RATING_NOTIFICATION_OPEN.includes(existing.status)) {
            if (existing.status === RATING_NOTIFICATION_QUEUED) {
                // Recovery commits the same result in a new storage batch. Move the unsent
                // claim to that batch so delivery can verify the committed settlement.
                // eslint-disable-next-line no-await-in-loop
                await ratingNotifications.updateOne({ _id, signature, status: RATING_NOTIFICATION_QUEUED }, {
                    $set: { batchId: record.batchId },
                }, options);
            }
            continue;
        }
        // eslint-disable-next-line no-await-in-loop
        await ratingNotifications.updateOne({ _id }, {
            $set: {
                domainId, contestId: record.contestId, uid: record.uid, signature,
                message: ratingNotificationMessage(record), status: RATING_NOTIFICATION_QUEUED,
                queuedAt: new Date(), batchId: record.batchId,
                notificationRunId: record.recalculation?.notificationRunId || null,
                recalculationRemoved: !!record.recalculationRemoved,
            },
            $setOnInsert: { createdAt: new Date() },
        }, { ...options, upsert: true });
    }
}

async function deliverRatingNotification(claim: any, report: (payload: any) => any) {
    // Claiming the delivery is the transition queued/failed -> pending: only one run ever sends it.
    const owned = await ratingNotifications.findOneAndUpdate({
        _id: claim._id, status: { $in: [RATING_NOTIFICATION_QUEUED, RATING_NOTIFICATION_FAILED] },
    }, { $set: { status: RATING_NOTIFICATION_PENDING, claimedAt: new Date() } }, { returnDocument: 'after' });
    if (!owned) return false;
    try {
        await MessageModel.send(1, owned.uid, owned.message, MessageModel.FLAG_UNREAD);
        await ratingNotifications.updateOne({ _id: owned._id }, {
            $set: { status: RATING_NOTIFICATION_SENT, sentAt: new Date() }, $unset: { lastError: '' },
        });
        await settlements.updateOne(
            {
                domainId: owned.domainId, contestId: owned.contestId, batchId: owned.batchId,
                recordType: 'user', uid: owned.uid, active: true,
            },
            { $set: { notificationSent: true, notificationSentAt: new Date() } },
        );
        return true;
    } catch (error: any) {
        await ratingNotifications.updateOne({ _id: owned._id }, {
            $set: { status: RATING_NOTIFICATION_FAILED, lastError: String(error?.message || error) },
        });
        await report({ message: `Rating notification failed for user ${owned.uid}: ${error?.message || String(error)}` });
        return false;
    }
}

/**
 * Deliver every notification that was queued with a settlement but not sent yet. Queued claims are
 * committed with the settlement itself, so an interrupted run only delays its notifications, and a
 * claim that is already pending (its sender died mid-flight) is never sent twice.
 */
async function deliverRatingNotifications(domainId: string, report: (payload: any) => any) {
    const pending = await ratingNotifications.find({
        domainId, status: { $in: [RATING_NOTIFICATION_QUEUED, RATING_NOTIFICATION_FAILED] },
    });
    for await (const claim of pending) {
        if (!claim?.message || !Number.isSafeInteger(claim.uid)) {
            // eslint-disable-next-line no-await-in-loop
            await ratingNotifications.deleteOne({ _id: claim._id, status: RATING_NOTIFICATION_FAILED });
            continue;
        }
        // Staged/abandoned writes must never send a result that was not committed.
        // Removal notices belong to the committed contest summary rather than a rated user row.
        // eslint-disable-next-line no-await-in-loop
        const committed = await settlements.findOne({
            domainId, contestId: claim.contestId, batchId: claim.batchId, active: true,
            ...claim.recalculationRemoved ? { recordType: 'contest' } : { recordType: 'user', uid: claim.uid },
        }, { projection: { _id: 1 } });
        if (!committed) continue;
        // eslint-disable-next-line no-await-in-loop
        await deliverRatingNotification(claim, report);
    }
}

function compareByRule(sort: Record<string, number>, a: any, b: any): number {
    for (const [key, direction] of Object.entries(sort)) {
        const av = Number(a?.[key] || 0);
        const bv = Number(b?.[key] || 0);
        if (!Number.isFinite(av) || !Number.isFinite(bv)) throw new Error(`Invalid contest score ${key}`);
        if (av !== bv) return direction === -1 ? (av > bv ? -1 : 1) : (av < bv ? -1 : 1);
    }
    return 0;
}

function isValidRatingSubmission(tdoc: any, submission: any): boolean {
    if (!tdoc.pids?.includes(submission?.pid) || !finalStatuses.has(submission?.status)) return false;
    if (tdoc.langs?.length && !tdoc.langs.includes(submission.lang)) return false;
    const time = submission.rid?.getTimestamp?.()?.getTime?.();
    return Number.isFinite(time)
        && time >= contestStart(tdoc).getTime()
        && time <= contestEnd(tdoc).getTime();
}

function ratingJournal(tdoc: any, tsdoc: any): any[] {
    if (!Array.isArray(tsdoc?.journal)) return [];
    return tsdoc.journal.filter((submission: any) => isValidRatingSubmission(tdoc, submission));
}

function hasValidSubmission(tdoc: any, tsdoc: any): boolean {
    return ratingJournal(tdoc, tsdoc).length > 0;
}

function rangeEligible(tdoc: any, rating: number): boolean {
    const min = Number(tdoc.ratedMin ?? tdoc.rated_min ?? DEFAULT_RATING_MIN);
    const rawMax = tdoc.ratedMax ?? tdoc.rated_max;
    const max = rawMax === null || rawMax === undefined || rawMax === '' ? DEFAULT_RATING_MAX : Number(rawMax);
    if (!Number.isSafeInteger(min) || min < 0 || (max !== null && (!Number.isSafeInteger(max) || max <= min))) {
        throw new ValidationError('ratingParticipation', null, '比赛 Rating 范围配置无效');
    }
    return rating >= min && (max === null || rating < max);
}

function formalRankEligible(tsdoc: any): boolean {
    return Number.isSafeInteger(tsdoc?.uid) && tsdoc.uid > 1 && !!tsdoc.attend
        && !tsdoc.unrank && !tsdoc.virtual && !tsdoc.practice && !tsdoc.observer
        && !tsdoc.ratingDisqualified && !tsdoc.disqualified;
}

function identityEligible(tsdoc: any): boolean {
    return formalRankEligible(tsdoc);
}

function parseRatingChoice(value: any): boolean | undefined {
    if (value === undefined || value === null || value === '') return undefined;
    if (value === true || value === 1 || value === '1' || value === 'true') return true;
    if (value === false || value === 0 || value === '0' || value === 'false') return false;
    return undefined;
}

function clearOwnLoadWarning() {
    const ui = (global as any).Hydro?.ui;
    const notifications = ui?.nodes?.Notification;
    if (!Array.isArray(notifications)) return;
    ui.nodes.Notification = notifications.filter((item: any) => {
        if (item?.name !== 'Addon load fail: {0}') return true;
        const failedPath = item.args?.args?.[0];
        if (!failedPath) return true;
        try {
            return path.resolve(String(failedPath)) !== path.resolve(__dirname);
        } catch {
            return true;
        }
    });
}

async function attendWithRatingChoice(h: Handler) {
    const handler: any = h;
    if (h.args?.operation !== 'attend' || !handler.tdoc || handler.tdoc.rule === 'homework') return undefined;
    h.checkPerm(PERM.PERM_ATTEND_CONTEST);
    const raw: any = { ...(h.args || {}), ...(h.request?.body || {}) };
    const action = raw.ratingAction || 'attend';
    const latest = await ContestModel.getStatus(h.domain._id, handler.tdoc.docId, h.user._id);
    const beforeStart = contestStart(handler.tdoc).getTime() > Date.now();

    if (action === 'cancel') {
        if (!latest?.attend) throw new ContestNotAttendedError(h.domain._id, handler.tdoc.docId);
        if (!beforeStart) throw new ValidationError('operation', null, '比赛开始后不能取消报名');
        const removed = await DocumentModel.collStatus.deleteOne({
            domainId: h.domain._id,
            docType: DocumentModel.TYPE_CONTEST,
            docId: handler.tdoc.docId,
            uid: h.user._id,
            attend: { $gt: 0 },
        });
        if (!removed.deletedCount) throw new ContestNotAttendedError(h.domain._id, handler.tdoc.docId);
        await DocumentModel.coll.updateOne({
            domainId: h.domain._id,
            docType: DocumentModel.TYPE_CONTEST,
            docId: handler.tdoc.docId,
            attend: { $gt: 0 },
        }, { $inc: { attend: -1 } });
        h.back();
        return 'after';
    }

    const ratingEnabled = contestRated(handler.tdoc);
    if (action === 'update' && !ratingEnabled) throw new ValidationError('ratingAction');
    const participatesInRanking = ratingEnabled ? parseRatingChoice(raw.ratingParticipation) : true;
    if (participatesInRanking === undefined) throw new ValidationError('ratingParticipation');
    const currentRating = getRatingState(h.user).rating;
    if (ratingEnabled && participatesInRanking && !rangeEligible(handler.tdoc, currentRating)) {
        throw new ValidationError('ratingParticipation', null, '当前 Rating 不符合正式资格');
    }
    if (action === 'update') {
        if (!latest?.attend) throw new ContestNotAttendedError(h.domain._id, handler.tdoc.docId);
        if (!beforeStart) throw new ValidationError('operation', null, '比赛开始后不能修改参赛身份');
        await ContestModel.setStatus(h.domain._id, handler.tdoc.docId, h.user._id, {
            unrank: participatesInRanking ? 0 : 1,
        });
        h.back();
        return 'after';
    }
    if (action !== 'attend') throw new ValidationError('ratingAction');
    if (ContestModel.isDone(handler.tdoc)) throw new ContestNotLiveError(handler.tdoc.docId);
    const code = raw.code || '';
    if (handler.tdoc._code && code !== handler.tdoc._code) throw new InvalidTokenError('Contest Invitation', code);
    await ContestModel.attend(h.domain._id, handler.tdoc.docId, h.user._id, {
        subscribe: 1,
        unrank: participatesInRanking ? 0 : 1,
    });
    h.back();
    return 'after';
}

function statusWithStats(tdoc: any, tsdoc: any): any {
    const rule = ContestModel.RULES[tdoc.rule];
    const journal = ratingJournal(tdoc, tsdoc)
        .sort((a, b) => a.rid.getTimestamp().getTime() - b.rid.getTimestamp().getTime());
    return { ...tsdoc, ...rule.stat(tdoc, journal) };
}

function averageRanks(users: any[], sort: Record<string, number>): any[] {
    const result = [];
    for (let index = 0; index < users.length;) {
        let end = index + 1;
        while (end < users.length && compareByRule(sort, users[index].stat, users[end].stat) === 0) end++;
        const rank = (index + 1 + end) / 2;
        for (let i = index; i < end; i++) result.push({ ...users[i], rank });
        index = end;
    }
    return result;
}

async function settleContest(
    domainId: string, tdoc: any, states: Map<number, RatingState>, batchId: ObjectId,
) {
    const base = {
        domainId, contestId: tdoc.docId, contestTitle: tdoc.title, ratingPoolId: domainId,
        contestType: tdoc.contestType ?? tdoc.contest_type,
        scoreRule: tdoc.scoreRule ?? tdoc.score_rule,
        ruleVersion: tdoc.ratingVersion ?? tdoc.rating_version,
        startTime: contestStart(tdoc), endTime: contestEnd(tdoc),
        organizerSnapshot: organizerIds(tdoc),
        batchId, settledAt: new Date(), recordType: 'contest', active: true,
    };
    if (!contestRated(tdoc)) return { summary: { ...base, reason: 'unrated', participantCount: 0 }, records: [] };
    if (contestEnd(tdoc).getTime() > Date.now()) return null;
    const pending = await RecordModel.getMulti(domainId, {
        contest: tdoc.docId,
        status: { $in: [STATUS.STATUS_WAITING, STATUS.STATUS_FETCHED, STATUS.STATUS_COMPILING, STATUS.STATUS_JUDGING] },
    }).limit(1).toArray();
    if (pending.length) throw new Error(`Contest ${tdoc.docId} has pending judgments`);
    const storedStatusDocs = await ContestModel.getMultiStatus(domainId, { docId: tdoc.docId }).toArray();
    const currentOrganizers = base.ruleVersion === RATING_VERSION ? organizerIds(tdoc) : null;
    const organizers = currentOrganizers;
    if (base.ruleVersion === RATING_VERSION && !organizers) {
        throw new Error(`Contest ${tdoc.docId} has invalid organizer configuration`);
    }
    // A contest maintainer is rated as first place even when they never attended and therefore
    // have no contest status document. Synthetic documents exist only in this replay in memory.
    const statusByUid = new Map<number, any>(storedStatusDocs.map((doc: any) => [doc.uid, doc]));
    for (const uid of organizers?.contestAdminIds || []) {
        if (uid === organizers?.publisherId || statusByUid.has(uid)) continue;
        statusByUid.set(uid, { uid, attend: false, unrank: false, journal: [], ratingSyntheticAdmin: true });
    }
    const statusDocs = [...statusByUid.values()];
    const ignoredSystemErrorSubmissions = statusDocs.reduce((count: number, doc: any) => count
        + (doc.journal || []).filter((item: any) => item.status === STATUS.STATUS_SYSTEM_ERROR).length, 0);
    const sort = (tdoc.scoreRule ?? tdoc.score_rule) as Record<string, number>;
    if (statusDocs.some((doc: any) => (doc.journal || []).some((item: any) =>
        !item.rid?.getTimestamp || !Number.isFinite(item.rid.getTimestamp().getTime())))) {
        throw new Error(`Contest ${tdoc.docId} has invalid submission timestamps`);
    }
    const frozen: any = {};
    const identityAudit: any = {};
    const qualified = statusDocs.map((doc: any) => {
        const state = states.get(doc.uid) || initialState();
        const role = organizerRole(doc.uid, organizers);
        const adminAutoIncluded = role === 'contest_admin';
        identityAudit[doc.uid] = {
            uid: doc.uid,
            organizerRole: role,
            unrank: !!doc.unrank,
            formalRankEligible: role !== 'publisher' && formalRankEligible(doc),
            rankOverride: role === 'contest_admin' ? 'first_place' : null,
            adminAutoIncluded,
            syntheticStatus: !!doc.ratingSyntheticAdmin,
        };
        const entry = {
            ratingAtStart: state.rating,
            rangeEligible: adminAutoIncluded || rangeEligible(tdoc, state.rating),
            identityEligible: adminAutoIncluded || identityEligible(doc),
            unrank: !!doc.unrank,
            organizerRole: role,
            adminAutoIncluded,
        };
        frozen[doc.uid] = { ...entry, organizerRole: role };
        return { doc, state, stat: statusWithStats(tdoc, doc), entry, role };
    }).filter(({ doc, entry, role }) => role !== 'publisher' && (role === 'contest_admin'
        || (entry.rangeEligible && entry.identityEligible && identityEligible(doc) && hasValidSubmission(tdoc, doc))));
    const inputScores = qualified.map(({ doc, stat }) => ({
        uid: doc.uid,
        accept: stat.accept ?? null,
        time: stat.time ?? null,
        score: stat.score ?? null,
        journal: ratingJournal(tdoc, doc).map((item: any) => ({
            rid: item.rid, pid: item.pid, status: item.status, score: item.score, lang: item.lang,
        })),
    }));
    const summary = {
        ...base, organizerSnapshot: organizers, identityAudit, eligibilitySnapshot: frozen, inputScores,
        ignoredSystemErrorSubmissions,
        stateSnapshot: Object.fromEntries(qualified.map(({ doc, state }) => [doc.uid, { ...state }])),
        finalRatedUids: qualified.map(({ doc }) => doc.uid),
        ratedContestAdminUids: qualified
            .filter(({ doc }) => organizerRole(doc.uid, organizers) === 'contest_admin')
            .map(({ doc }) => doc.uid),
        participantCount: qualified.length,
        reason: qualified.length < 5 ? 'fewer_than_5' : 'settled',
        snapshotVersion: batchId.toHexString(),
    };
    if (qualified.length < 5) return { summary, records: [] };
    const contestAdmins = qualified.filter(({ doc }) => organizerRole(doc.uid, organizers) === 'contest_admin');
    const rankedParticipants = qualified.filter(({ doc }) => organizerRole(doc.uid, organizers) !== 'contest_admin');
    rankedParticipants.sort((a, b) => compareByRule(sort, a.stat, b.stat));
    if (!rankedParticipants.length) return { summary: { ...summary, reason: 'all_users_tied' }, records: [] };
    const ranked = [
        ...averageRanks(rankedParticipants, sort),
        ...contestAdmins.map((item) => ({ ...item, rank: 1, rankOverride: 'contest_admin_first_place' })),
    ];
    if (ranked.every((item) => item.rank === ranked[0].rank)) {
        return { summary: { ...summary, reason: 'all_users_tied' }, records: [] };
    }
    const inputs: RatedInput[] = ranked.map((item) => ({
        uid: item.doc.uid,
        internalRating: item.state.internalRating,
        rating: item.state.rating,
        ratedCount: item.state.ratedCount,
        maxRating: item.state.maxRating,
        rank: item.rank,
    }));
    const results = calculate(inputs);
    const referenceCount = Math.min(results.length, Math.floor(4 * Math.sqrt(results.length)));
    const ordered = [...results].sort((a, b) => b.internalRating - a.internalRating);
    const referenceThreshold = ordered[referenceCount - 1].internalRating;
    const referenceGroupSize = ordered.filter((item) => item.internalRating >= referenceThreshold).length;
    const records = results.map((result) => {
        const rankedUser = ranked.find((item) => item.doc.uid === result.uid);
        const score = rankedUser.stat;
        const role = organizerRole(result.uid, organizers);
        const record = {
            ...base, recordType: 'user', uid: result.uid, valid: true,
            qualified: true, formalRankEligible: true, organizerRole: role,
            rankOverride: rankedUser.rankOverride || null,
            organizerSnapshot: organizers, snapshotVersion: summary.snapshotVersion,
            notificationSent: false,
            finalScore: Object.fromEntries(Object.keys(sort).map((key) => [key, score[key] ?? 0])),
            before: {
                internalRating: result.internalRating, rating: result.rating,
                ratedCount: result.ratedCount, maxRating: result.maxRating,
            },
            actualRank: result.rank, participantCount: results.length,
            expectedRank: result.expectedRank, targetRank: result.targetRank,
            targetInternalRating: result.targetInternalRating,
            rawDelta: result.rawDelta, firstCorrection: result.firstCorrection,
            secondCorrection: result.secondCorrection, referenceThreshold,
            referenceGroupSize, delta: result.delta, bonus: result.bonus,
            ratingDelta: result.ratingDelta,
            after: {
                internalRating: result.nextInternalRating, rating: result.nextRating,
                ratedCount: result.nextRatedCount, maxRating: result.nextMaxRating,
            },
        };
        return record;
    });
    return { summary, records };
}

async function acquireLock(domainId: string, batchId: ObjectId) {
    const now = new Date();
    const expiresAt = new Date(Date.now() + 2 * 60 * 60 * 1000);
    try {
        await locks.insertOne({ _id: domainId, owner: batchId, expiresAt, startedAt: now });
    } catch (error: any) {
        if (error?.code !== 11000) throw error;
        const prior = await locks.findOneAndUpdate(
            { _id: domainId, expiresAt: { $lt: now } },
            { $set: { owner: batchId, expiresAt, startedAt: now } },
            { returnDocument: 'after' },
        );
        if (!prior) throw new Error(`Rating pool ${domainId} is already settling`);
    }
}

function transactionUnsupported(error: any): boolean {
    // MongoDB's driver rewrites code 20 from a transaction write into a retryable-writes
    // error, moving the server's actual reason to originalError. Inspect that reason:
    // the wrapper alone also occurs for unrelated illegal operations.
    const pending = [error];
    const seen = new Set<any>();
    while (pending.length) {
        const current = pending.pop();
        if (!current || typeof current !== 'object' || seen.has(current)) continue;
        seen.add(current);
        const message = String(current.message || current.errmsg || '');
        if (message.includes('Transaction numbers are only allowed on a replica set member or mongos')
            || ((current.code === 20 || current.codeName === 'IllegalOperation')
                && TRANSACTION_SUPPORT_ERROR.test(message))) return true;
        pending.push(current.originalError, current.cause, current.errorResponse);
    }
    return false;
}

interface PersistOptions {
    /** Full replay: rebuild every user state from the initial Rating instead of continuing it. */
    full?: boolean;
    /** MongoDB without transactions: write records as a recoverable staged batch. */
    staged?: boolean;
    /** Users whose state changed in this batch (incremental writes only touch them). */
    touched?: Set<number>;
    /** Contests settled in this batch; only their previous records are replaced. */
    contestIds?: any[];
    /** Notices for users whose previous Rating participation was removed by a recalculation. */
    notificationRecords?: any[];
}

async function persistDomainRating(
    domainId: string, states: Map<number, RatingState>, records: any[], batchId: ObjectId,
    session?: any, persistOptions: PersistOptions = {},
) {
    const {
        full = false, staged = false, touched, contestIds = [],
    } = persistOptions;
    const options = session ? { session } : {};
    const lock = await locks.updateOne({ _id: domainId, owner: batchId }, {
        $set: { expiresAt: new Date(Date.now() + 2 * 60 * 60 * 1000) },
    }, options);
    if (!lock.matchedCount) throw new Error(`Rating pool ${domainId} lock was lost`);

    // Queue the notifications together with the settlement: the claims are committed with the
    // records (same transaction, or before the staged batch is activated) so no result can be
    // written without a notification pending for it.
    await queueRatingNotifications(domainId, [...records, ...(persistOptions.notificationRecords || [])], options);

    if (staged && records.length) {
        await settlements.insertMany(records.map((record) => ({
            ...record, active: false, pendingCommit: true,
        })), options);
    }

    if (full) {
        await ratingUsers.updateMany({ domainId }, { $set: {
            ...stateFields(initialState()), rp: 0, rpInfo: {}, rank: null, level: 0,
        } }, options);
    }
    // Incremental batches keep the states of untouched users, so the history of the already
    // settled contests stays exactly as it was written.
    const updated = full ? [...states.entries()] : [...states.entries()].filter(([uid]) => touched?.has(uid));
    for (const [uid, state] of updated) {
        // eslint-disable-next-line no-await-in-loop
        await ratingUsers.updateOne({ domainId, uid }, {
            $set: {
                ...stateFields(state), rp: 0, rpInfo: {}, rank: null, level: 0,
                ratingUpdatedAt: new Date(), ratingBatchId: batchId,
            },
        }, { ...options, upsert: true });
    }

    // Only the contests settled in this batch replace their previous records; every other contest
    // keeps the history that was written when it was settled.
    const superseded: any = { domainId, active: true, batchId: { $ne: batchId }, contestId: { $in: contestIds } };
    if (contestIds.length) {
        await settlements.updateMany(superseded, {
            $set: { active: false, valid: false, supersededAt: new Date(), supersededBy: batchId },
        }, options);
    }
    if (staged) {
        await settlements.updateMany({ domainId, batchId, pendingCommit: true }, {
            $set: { active: true, committedAt: new Date() },
            $unset: { pendingCommit: '' },
        }, options);
        await settlements.deleteMany({
            domainId, active: false, pendingCommit: true, batchId: { $ne: batchId },
        }, options);
    } else if (records.length) {
        await settlements.insertMany(records, options);
    }
}

interface RatingCalculationOptions {
    full?: boolean;
    contestId?: string;
}

interface ContestRecalculation {
    contestId: string;
    notificationRunId: string;
    prefixContestIds: any[];
    contestIds: any[];
    touchedUids?: number[];
    removedParticipants?: any[];
}

/** Reuse immutable earlier settlements, then rebuild only the selected contest and its dependents. */
async function recalculateContestLocked(
    domainId: string, contestId: string, report: (payload: any) => any, batchId: ObjectId,
    recovery?: ContestRecalculation,
) {
    const contests = await ContestModel.getMulti(domainId).toArray();
    const byId = new Map<string, any>(contests.map((tdoc: any) => [String(tdoc.docId), tdoc]));
    const target = byId.get(contestId);
    if (!target) throw new Error(`找不到指定比赛 ${contestId}（域 ${domainId}）`);
    if (contestEnd(target).getTime() > Date.now()) throw new Error('指定比赛尚未结束，不能重算 Rating');
    const history = await settlements.find({
        domainId, active: true, recordType: 'contest', reason: 'settled',
    }).sort({ settledAt: 1, _id: 1 }).toArray();
    let plan = recovery;
    if (!plan) {
        const ids = new Set<string>(history.map((record: any) => String(record.contestId)));
        if (history.length !== ids.size || contests.some((tdoc: any) => contestSettled(tdoc, ids) && !ids.has(String(tdoc.docId)))) {
            throw new Error('比赛结算流水不完整，请先使用 full=true 重建 Rating 历史');
        }
        const index = history.findIndex((record: any) => String(record.contestId) === contestId);
        if (index < 0 && !contestRated(target)) throw new Error('指定比赛不是有效的 Rated 比赛');
        // The stored order also handles a contest that became eligible and was settled late.
        plan = {
            contestId, notificationRunId: batchId.toHexString(),
            prefixContestIds: (index < 0 ? history : history.slice(0, index)).map((record: any) => record.contestId),
            contestIds: index < 0 ? [target.docId] : history.slice(index).map((record: any) => record.contestId),
        };
    }
    const states = new Map<number, RatingState>();
    const prefixRows = await settlements.find({
        domainId, active: true, recordType: 'user', contestId: { $in: plan.prefixContestIds },
    }).toArray();
    for (const id of plan.prefixContestIds) {
        const summary = history.find((record: any) => String(record.contestId) === String(id));
        const rows = prefixRows.filter((record: any) => String(record.contestId) === String(id));
        if (!summary || rows.length !== summary.participantCount) {
            throw new Error(`比赛 ${id} 的赛前流水不完整，请使用 full=true 重建 Rating 历史`);
        }
        applySettlement(states, rows, new Set());
    }
    const previous = await settlements.find({
        domainId, active: true, recordType: 'user', contestId: { $in: plan.contestIds },
    }).toArray();
    const touched = new Set<number>([...(plan.touchedUids || []), ...previous.map((record: any) => record.uid)]);
    await report({
        message: `Rating domain ${domainId}: 指定重算 ${target.title}（${contestId}），`
            + `接续重算 ${plan.contestIds.length - 1} 场已结算比赛；其他未结算比赛保持不变`,
    });
    const records: any[] = [];
    const settledNow: any[] = [];
    for (const id of plan.contestIds) {
        const tdoc = byId.get(String(id));
        if (!tdoc) throw new Error(`后续已结算比赛 ${id} 不存在，不能完成接续重算`);
        if (contestEnd(tdoc).getTime() > Date.now()) throw new Error(`比赛 ${id} 尚未结束，不能重算 Rating`);
        // Unlike an incremental run, any failure aborts the entire dependent replay.
        // eslint-disable-next-line no-await-in-loop
        const outcome = await settleContest(domainId, tdoc, states, batchId);
        if (!outcome) throw new Error(`比赛 ${id} 暂时不能结算，未保存本次重算结果`);
        records.push(outcome.summary, ...outcome.records);
        if (outcome.records.length) {
            settledNow.push(tdoc.docId);
            applySettlement(states, outcome.records, touched);
        }
        // eslint-disable-next-line no-await-in-loop
        await report({ message: `Rating contest ${tdoc.title}: 重算 ${outcome.summary.reason}` });
        // eslint-disable-next-line no-await-in-loop
        const renewed = await locks.updateOne({ _id: domainId, owner: batchId }, {
            $set: { expiresAt: new Date(Date.now() + 2 * 60 * 60 * 1000) },
        });
        if (!renewed.matchedCount) throw new Error(`Rating pool ${domainId} lock was lost`);
    }
    for (const uid of touched) if (!states.has(uid)) states.set(uid, initialState());
    const removedParticipants = [...(plan.removedParticipants || []), ...previous]
        .filter((old: any, index, all) => all.findIndex((item: any) => String(item.contestId) === String(old.contestId) && item.uid === old.uid) === index)
        .filter((old: any) => !records.some((record: any) => record.recordType === 'user'
            && String(record.contestId) === String(old.contestId) && record.uid === old.uid))
        .map((old: any) => ({ contestId: old.contestId, contestTitle: old.contestTitle, uid: old.uid, before: old.before }));
    plan = { ...plan, touchedUids: [...touched], removedParticipants };
    for (const record of records) record.recalculation = plan;
    const removed = removedParticipants.map((old: any) => ({
        ...old, recordType: 'user', batchId, recalculation: plan, recalculationRemoved: true, after: states.get(old.uid),
    }));
    await commitRatingBatch(domainId, states, records, batchId, {
        touched, contestIds: plan.contestIds, notificationRecords: removed,
    }, report);
    await markSettledContests(domainId, settledNow, false, batchId);
    const ratedIds = new Set(settledNow.map(String));
    await DocumentModel.coll.updateMany({
        domainId, docType: DocumentModel.TYPE_CONTEST,
        docId: { $in: plan.contestIds.filter((id) => !ratedIds.has(String(id))) },
    }, { $unset: SETTLED_MARKER_UNSET });
    (UserModel as any)._deleteUserCache(domainId);
    await deliverRatingNotifications(domainId, report);
    await report({
        message: `Rating domain ${domainId} finished (重算 ${plan.contestIds.length} contests, `
            + `${records.filter((record) => record.recordType === 'user').length} user records)`,
    });
}

async function commitRatingBatch(
    domainId: string, states: Map<number, RatingState>, records: any[], batchId: ObjectId,
    persistOptions: PersistOptions, report: (payload: any) => any,
) {
    const session = db.client.startSession();
    try {
        await session.withTransaction(
            () => persistDomainRating(domainId, states, records, batchId, session, persistOptions),
        );
    } catch (error: any) {
        if (!transactionUnsupported(error)) throw error;
        await report({ message: `Rating domain ${domainId}: MongoDB 单机模式，使用可恢复提交` });
        await persistDomainRating(
            domainId, states, records, batchId, undefined, { ...persistOptions, staged: true },
        );
    } finally {
        await session.endSession();
    }
    await settlements.deleteMany({
        domainId, active: false, pendingCommit: true, batchId: { $ne: batchId },
    });
}

async function calculateDomainIncrementalLocked(
    domainId: string, report: (payload: any) => any, options: RatingCalculationOptions,
    batchId: ObjectId, interruptedBatch: any,
) {
    const contests = await ContestModel.getMulti(domainId).sort({ beginAt: 1, _id: 1 }).toArray();
    const settled = await activeSettledContests(domainId);
    const existing = await ratingUsers.find({ domainId }).project({
        uid: 1, internalRating: 1, internal_rating: 1, rating: 1,
        ratedCount: 1, rated_count: 1, maxRating: 1, max_rating: 1,
    }).toArray();
    // A staged batch left behind by an interrupted non-transactional run may have written user
    // states without activating its records: rebuild everything instead of trusting them.
    const interrupted = !!interruptedBatch;
    let full = options.full === true || interrupted;
    if (interrupted) {
        await report({ message: `Rating domain ${domainId}: 检测到未完成的提交流水，本次改为完整重算` });
    } else if (!full && !settled.ids.size
        && !contests.some((tdoc: any) => tdoc.ratingSettled === true)
        && existing.some((doc: any) => getRatingState(doc).ratedCount > 0)) {
        // Rating results exist but no contest is marked as settled, so the stored states cannot
        // be trusted as the base of an incremental calculation.
        full = true;
        await report({ message: `Rating domain ${domainId}: 缺少比赛结算标记，本次改为完整重算以重建历史` });
    }
    const states = new Map<number, RatingState>();
    for (const doc of existing) states.set(doc.uid, full ? initialState() : getRatingState(doc));
    const pendingCount = contests.filter((tdoc: any) => !contestSettled(tdoc, settled.ids)).length;
    await report({
        message: full
            ? `Rating domain ${domainId}: full replay of ${contests.length} contests`
            : `Rating domain ${domainId}: incremental calculation, ${pendingCount} contests without Rating`,
    });
    // The newest settlement so far. A contest that is rated after it was passed is settled on
    // top of the current Rating instead of its historical one, which is reported to the admin.
    let frontier = contests
        .filter((tdoc: any) => contestSettled(tdoc, settled.ids))
        .map((tdoc: any) => contestOrder(tdoc))
        .reduce((a: [number, string] | null, b: [number, string]) => (!a || orderBefore(a, b) ? b : a), null);
    const records: any[] = [];
    const touched = new Set<number>();
    const batchContestIds: any[] = [];
    const settledNow: any[] = [];
    for (const tdoc of contests) {
        // Rating is calculated once per contest: settled contests are never recalculated.
        if (!full && contestSettled(tdoc, settled.ids)) continue;
        if (!contestRated(tdoc)) {
            // A full replay also records why a contest does not take part in Rating.
            if (!full) continue;
            // eslint-disable-next-line no-await-in-loop
            const unrated = await settleContest(domainId, tdoc, states, batchId);
            if (unrated) {
                records.push(unrated.summary);
                batchContestIds.push(tdoc.docId);
            }
            continue;
        }
        if (contestEnd(tdoc).getTime() > Date.now()) {
            // eslint-disable-next-line no-await-in-loop
            await report({ message: `Rating contest ${tdoc.title}: 比赛尚未结束，等待下次计算` });
            continue;
        }
        let outcome: { summary: any, records: any[] } | null = null;
        try {
            // eslint-disable-next-line no-await-in-loop
            outcome = await settleContest(domainId, tdoc, states, batchId);
        } catch (error: any) {
            // Judgments still running or a configuration that cannot be settled: leave this
            // contest for a later run instead of stopping the whole pool. A full replay has to
            // rebuild every contest it already rated, so it fails instead of persisting a
            // history that is missing one of its settlements.
            // eslint-disable-next-line no-await-in-loop
            await report({ message: `Rating contest ${tdoc.title}: 结算暂缓（${error?.message || String(error)}）` });
            if (full) throw error;
            continue;
        }
        if (!outcome) continue;
        if (!outcome.records.length) {
            // Nothing to apply: the refreshed evaluation summary is all that is recorded.
            records.push(outcome.summary);
            batchContestIds.push(tdoc.docId);
            // eslint-disable-next-line no-await-in-loop
            await report({ message: `Rating contest ${tdoc.title}: ${outcome.summary.reason}, 未产生 Rating 变化` });
            continue;
        }
        if (!full && frontier && orderBefore(contestOrder(tdoc), frontier)) {
            // eslint-disable-next-line no-await-in-loop
            await report({
                message: `Rating contest ${tdoc.title}: 晚于已结算比赛才具备结算条件，按当前 Rating 顺序计算`,
            });
        }
        records.push(outcome.summary, ...outcome.records);
        batchContestIds.push(tdoc.docId);
        settledNow.push(tdoc.docId);
        applySettlement(states, outcome.records, touched);
        frontier = contestOrder(tdoc);
        const ratedAdmins = outcome.summary.ratedContestAdminUids || [];
        // eslint-disable-next-line no-await-in-loop
        await report({
            message: `Rating contest ${tdoc.title}: ${outcome.summary.reason}; `
                + `contest-admin-first-place=${ratedAdmins.length ? ratedAdmins.join(',') : 'none'}`,
        });
        // eslint-disable-next-line no-await-in-loop
        const renewed = await locks.updateOne({ _id: domainId, owner: batchId }, {
            $set: { expiresAt: new Date(Date.now() + 2 * 60 * 60 * 1000) },
        });
        if (!renewed.matchedCount) throw new Error(`Rating pool ${domainId} lock was lost`);
    }
    const recalculation = interruptedBatch?.recalculation || (options.full ? {
        full: true, notificationRunId: batchId.toHexString(),
    } : null);
    if (recalculation) for (const record of records) record.recalculation = recalculation;
    const persist = full || settledNow.length > 0;
    if (persist) {
        const persistOptions: PersistOptions = {
            full, touched, contestIds: batchContestIds,
        };
        await commitRatingBatch(domainId, states, records, batchId, persistOptions, report);
        // The marker is written after the records: a contest marked as settled always has its
        // settlement stored. Records written before the marker existed are marked on the way,
        // while a full replay recomputes the markers from its own result.
        const marked: any[] = [];
        const seen = new Set<string>();
        for (const id of (full ? settledNow : [...settled.docIds, ...settledNow])) {
            if (!id || seen.has(String(id))) continue;
            seen.add(String(id));
            marked.push(id);
        }
        await markSettledContests(domainId, marked, full, batchId);
    }
    (UserModel as any)._deleteUserCache(domainId);
    // Notifications are queued with the settlement and delivered afterwards: claims left over
    // by an interrupted run are picked up here as well.
    await deliverRatingNotifications(domainId, report);
    await report({
        message: `Rating domain ${domainId} finished (${settledNow.length} contests, `
            + `${records.filter((i) => i.recordType === 'user').length} user records)`,
    });
}

async function calculateDomain(
    domainId: string, report: (payload: any) => any = () => { }, options: RatingCalculationOptions = {},
) {
    const batchId = new ObjectId();
    await acquireLock(domainId, batchId);
    let followup: RatingCalculationOptions | undefined;
    try {
        const interrupted = await settlements.findOne({ domainId, active: false, pendingCommit: true });
        const recovery = interrupted?.recalculation;
        if (recovery?.contestId) {
            await report({ message: `Rating domain ${domainId}: 恢复未完成的指定比赛重算 ${recovery.contestId}` });
            await recalculateContestLocked(domainId, recovery.contestId, report, batchId, recovery);
            if (options.contestId !== recovery.contestId || options.full) followup = options;
        } else if (options.contestId) {
            if (interrupted) {
                await calculateDomainIncrementalLocked(domainId, report, {}, batchId, interrupted);
                followup = options;
            } else await recalculateContestLocked(domainId, options.contestId, report, batchId);
        } else await calculateDomainIncrementalLocked(domainId, report, options, batchId, interrupted);
    } finally {
        await locks.deleteOne({ _id: domainId, owner: batchId });
    }
    if (followup) await calculateDomain(domainId, report, followup);
    else if (problemRatings) await problemRatings.refresh(domainId, report);
}

async function calculateAll(report: (payload: any) => any = () => { }, options: { full?: boolean } = {}) {
    const domains = await DomainModel.getMulti().project({ _id: 1 }).toArray();
    let success = true;
    for (const domain of domains) {
        try {
            // eslint-disable-next-line no-await-in-loop
            await calculateDomain(domain._id, report, options);
        } catch (error: any) {
            // One broken pool must not stop the other domains from settling.
            success = false;
            // eslint-disable-next-line no-await-in-loop
            await report({ message: `Rating domain ${domain._id} failed: ${error?.message || String(error)}` });
        }
    }
    return success;
}

function patchContestConfig(ctx: Context) {
    ctx.on('contest/before-add', (data: any) => {
        const config = scoreRuleFor(data.rule);
        if (!config) return;
        const publisherId = data.owner;
        Object.assign(data, {
            contestType: config.contestType,
            contest_type: config.contestType,
            publisherId,
            publisher_id: publisherId,
            contestAdminIds: [],
            contest_admin_ids: [],
            scoreRule: config.sort,
            score_rule: config.sort,
            ratingVersion: RATING_VERSION,
            rating_version: RATING_VERSION,
            is_rated: true,
            start_time: data.beginAt,
            end_time: data.endAt,
            ratedMin: DEFAULT_RATING_MIN,
            rated_min: DEFAULT_RATING_MIN,
            ratedMax: DEFAULT_RATING_MAX,
            rated_max: DEFAULT_RATING_MAX,
        });
    });
    ctx.on('contest/before-edit', async (tdoc: any, set: any) => {
        // A settled contest only accepts description edits. Unchanged values from legacy/full edit
        // forms are removed, while any attempted configuration change uses Hydro's native error page.
        if (await contestRatingSettled(tdoc)) {
            for (const key of Object.keys(set)) {
                if (SETTLED_MUTABLE_FIELDS.has(key)) continue;
                if (!sameSettledValue(key, set[key], tdoc[key])) {
                    throw new ValidationError(key, null, 'Rating 已结算的比赛只允许修改比赛说明');
                }
                delete set[key];
            }
            return;
        }
        const config = scoreRuleFor(set.rule || tdoc.rule);
        if (!config) return;
        const publisherId = set.owner ?? tdoc.owner;
        const contestAdminIds = [...new Set<number>(set.maintainer ?? tdoc.maintainer ?? [])].sort((a, b) => a - b);
        Object.assign(set, {
            contestType: config.contestType, contest_type: config.contestType,
            scoreRule: config.sort, score_rule: config.sort,
            ratingVersion: RATING_VERSION, rating_version: RATING_VERSION,
            publisherId, publisher_id: publisherId,
            contestAdminIds, contest_admin_ids: contestAdminIds,
            is_rated: set.rated ?? tdoc.rated ?? false,
            start_time: set.beginAt ?? tdoc.beginAt,
            end_time: set.endAt ?? tdoc.endAt,
        });
        if ('ratedMin' in set) set.rated_min = set.ratedMin;
        if ('ratedMax' in set) set.rated_max = set.ratedMax;
        if (!('ratedMin' in set) && set.rated_min === undefined && tdoc.ratedMin === undefined && tdoc.rated_min === undefined) {
            set.ratedMin = DEFAULT_RATING_MIN;
            set.rated_min = DEFAULT_RATING_MIN;
        }
        if (!('ratedMax' in set) && set.rated_max === undefined && tdoc.ratedMax === undefined && tdoc.rated_max === undefined) {
            set.ratedMax = DEFAULT_RATING_MAX;
            set.rated_max = DEFAULT_RATING_MAX;
        }
    });
}

async function patchRankingResponse(h: Handler, body: any) {
    if (!body || h.request?.path?.includes('/ranking') !== true || !Array.isArray(body.udocs)) return;
    const page = Math.max(1, Number(h.args?.page || 1));
    const cursor = DomainModel.getMultiUserInDomain(h.domain._id, {
        uid: { $gt: 1 }, join: true, rating: { $gt: 0 },
    }).sort({ rating: -1, uid: 1 });
    const [dudocs, pageCount, userCount] = await h.paginate(cursor, page, 'ranking');
    const udict = await UserModel.getList(h.domain._id, dudocs.map((doc: any) => doc.uid));
    body.udocs = dudocs
        .map((doc: any) => decorate(udict[doc.uid]))
        .filter((udoc: any) => getRatingState(udoc?._dudoc || udoc).rating > 0);
    body.upcount = pageCount;
    body.ucount = userCount;
    body.ratingRanking = true;
    body.ratingCurrentRank = h.user?._id > 1 && getRatingState(h.user._dudoc).rating > 0
        ? (await DomainModel.getMultiUserInDomain(h.domain._id, {
            uid: { $gt: 1 }, join: true, rating: { $gt: getRatingState(h.user._dudoc).rating },
        }).count()) + 1
        : null;
}

async function replaceHomeRanking(h: Handler, body: any) {
    if (!Array.isArray(body?.contents) || !body.udict) return;
    const sections = body.contents.flatMap((column: any) => column.sections || [])
        .filter((section: any) => section?.[0] === 'ranking' && Array.isArray(section[1]));
    if (!sections.length) return;
    const limit = Math.max(1, ...sections.map((section: any) => section[1].length), 50);
    const docs = await DomainModel.getMultiUserInDomain(h.domain._id, {
        uid: { $gt: 1 }, join: true, rating: { $gt: 0 },
    })
        .sort({ rating: -1, uid: 1 }).limit(limit).project({ uid: 1 }).toArray();
    const uids = docs.map((doc: any) => doc.uid);
    body.ratingVisibleUids = uids;
    Object.assign(body.udict, await UserModel.getList(h.domain._id, uids));
    for (const section of sections) section[1] = uids.slice(0, section[1].length || 50);
}

async function decorateMessageBody(h: Handler, body: any) {
    const messages = body?.messages;
    if (!messages || typeof messages !== 'object') return;
    const uids = Object.values<any>(messages).map((entry) => entry?._id).filter((uid) => Number.isSafeInteger(uid));
    if (!uids.length) return;
    const users = await UserModel.getList(h.domain._id, uids);
    for (const entry of Object.values<any>(messages)) {
        if (!entry?.udoc || !users[entry._id]) continue;
        Object.assign(entry.udoc, users[entry._id].serialize(h));
        decorate(entry.udoc);
    }
}

async function exposeRatingParticipation(h: Handler, body: any) {
    const handler: any = h;
    if (!body || !handler.tdoc) return;
    if (h.response.template === 'contest_edit.html') {
        body.ratingContestSettled = await contestRatingSettled(handler.tdoc);
    }
    const uid = handler.user?._id;
    const latest = uid ? await ContestModel.getStatus(h.domain._id, handler.tdoc.docId, uid) : null;
    // Keep the public contest detail state aligned with Hydro's native ranking flag.
    if (body.tsdoc) body.tsdoc.unrank = !!latest?.unrank;
    else {
        const status = handler.tsdoc || latest || {};
        body.tsdoc = {
            attend: status.attend,
            subscribe: status.subscribe,
            startAt: status.startAt,
            endAt: status.endAt,
            unrank: !!latest?.unrank,
        };
    }
    if (h.response.template) {
        const contestOwner = body.udict?.[handler.tdoc.owner]
            || await UserModel.getById(h.domain._id, handler.tdoc.owner);
        if (contestOwner) {
            decorate(contestOwner);
            body.ratingContestOwner = contestOwner;
        }
    }
    const state = getRatingState(handler.user);
    const min = Number(handler.tdoc.ratedMin ?? handler.tdoc.rated_min ?? DEFAULT_RATING_MIN);
    const rawMax = handler.tdoc.ratedMax ?? handler.tdoc.rated_max;
    const max = rawMax === null || rawMax === undefined || rawMax === '' ? DEFAULT_RATING_MAX : Number(rawMax);
    const beginAt = contestStart(handler.tdoc);
    const effectiveEndAt = handler.tsdoc?.endAt ? new Date(handler.tsdoc.endAt) : contestEnd(handler.tdoc);
    body.ratingContestUi = {
        isRated: contestRated(handler.tdoc),
        currentRating: state.rating,
        ratedMin: min,
        ratedMax: max,
        formalEligible: Number.isFinite(min) && Number.isFinite(max)
            && state.rating >= min && state.rating < max,
        beginAt: beginAt.getTime(),
        endAt: effectiveEndAt.getTime(),
        beforeStart: beginAt.getTime() > Date.now(),
        canChangeRegistration: !!latest?.attend && beginAt.getTime() > Date.now(),
        participation: latest?.attend ? (latest.unrank ? 'star' : 'formal') : null,
        invitationRequired: !!handler.tdoc._code,
    };
}

function removeDuplicateContestTopNavigation(h: Handler, body: any) {
    const template = h.response.template;
    if (![
        'contest_detail.html',
        'contest_problemlist.html',
        'contest_scoreboard.html',
        'record_main.html',
    ].includes(template) || !Array.isArray(body?.overrideNav)) return;
    body.overrideNav = body.overrideNav.filter((item: any) => ![
        'contest_problemlist',
        'contest_scoreboard',
    ].includes(item?.name));
}

function patchContestScoreboard(ctx: Context) {
    ctx.on('contest/scoreboard', (tdoc: any, rows: any[]) => {
        if (!scoreRuleFor(tdoc?.rule) || !Array.isArray(rows) || rows.length < 2) return;
        const organizers = organizerIds(tdoc);
        if (!organizers) return;
        const filtered = rows.slice(0, 1);
        for (const row of rows.slice(1)) {
            const userCell = row?.find?.((cell: any) => cell?.type === 'user');
            if (userCell && organizerRole(userCell.raw, organizers) === 'publisher') continue;
            filtered.push(row);
        }
        let position = 1;
        let lastRank = 0;
        let lastOriginalRank: string | null = null;
        for (const row of filtered.slice(1)) {
            const rankCell = row?.find?.((cell: any) => cell?.type === 'rank');
            if (!rankCell || rankCell.value === '0') continue;
            const originalRank = String(rankCell.value);
            if (originalRank !== lastOriginalRank) {
                lastRank = position;
            }
            rankCell.value = String(lastRank);
            lastOriginalRank = originalRank;
            position += 1;
        }
        rows.splice(0, rows.length, ...filtered);
    });
}

async function exposeContestBoard(h: Handler, body: any) {
    if (h.response.template !== 'contest_scoreboard.html' || !body?.tdoc
        || !['acm', 'oi', 'ioi', 'strictioi', 'cf'].includes(body.tdoc.rule) || !Array.isArray(body.rows)) return;
    const tdoc = body.tdoc;
    const frozen = !!tdoc.lockAt && !tdoc.unlocked && !h.args?.realtime;
    const uids = body.rows.slice(1).map((row: any[]) => row.find((cell) => cell.type === 'user')?.raw)
        .filter((uid: any) => Number.isSafeInteger(uid));
    const [statuses, profiles, domainProfiles] = uids.length ? await Promise.all([
        ContestModel.getMultiStatus(h.domain._id, { docId: tdoc.docId, uid: { $in: uids } })
            .project({ uid: 1, startAt: 1, detail: 1, display: 1, journal: 1 }).toArray(),
        // The requested search uses these two profile fields for every participant.
        // Project only those fields, keeping other private user data out of the board response.
        UserModel.getMulti({ _id: { $in: uids } }, ['_id', 'school', 'displayName']).toArray(),
        DomainModel.getDomainUserMulti(h.domain._id, uids).project({ uid: 1, school: 1, displayName: 1 }).toArray(),
    ]) : [[], [], []];
    body.ratingBoard = buildContestBoard(tdoc, body.rows, body.pdict || {}, statuses,
        frozen && (tdoc.rule === 'acm' || ContestModel.isLocked(tdoc)), STATUS.STATUS_ACCEPTED);
    const profileByUid = Object.fromEntries(profiles.map((profile) => [profile._id, profile]));
    const domainByUid = Object.fromEntries(domainProfiles.map((profile) => [profile.uid, profile]));
    for (const participant of body.ratingBoard.participants) {
        const profile = profileByUid[participant.uid] || body.udict?.[participant.uid] || {};
        const domainProfile = domainByUid[participant.uid] || {};
        participant.school = String(domainProfile.school ?? profile.school ?? '');
        participant.displayName = String(domainProfile.displayName ?? profile.displayName ?? '');
    }
}

function parseOptionalNumber(raw: any): number | null | undefined {
    if (raw === undefined) return undefined;
    if (raw === '' || raw === null) return null;
    const value = Number(raw);
    return Number.isFinite(value) ? Math.trunc(value) : undefined;
}

async function saveContestRatingConfig(h: Handler) {
    const raw: any = { ...(h.args || {}), ...(h.request?.body || {}) };
    const tid = h.args?.tid || raw.tid || h.response?.body?.tid;
    if (!tid || h.args?.operation !== 'update') return;
    const tdoc: any = (h as any).tdoc;
    // A settled contest is frozen: its Rating configuration can no longer be rewritten.
    if (tdoc && await contestRatingSettled(tdoc)) return;
    if (!scoreRuleFor(tdoc?.rule || raw.rule)) return;
    const set: any = {};
    const min = parseOptionalNumber(raw.ratedMin ?? raw.rated_min);
    const max = parseOptionalNumber(raw.ratedMax ?? raw.rated_max);
    set.ratedMin = min === undefined || min === null ? DEFAULT_RATING_MIN : Math.max(0, min);
    set.ratedMax = max === undefined || max === null ? DEFAULT_RATING_MAX : Math.max(0, max);
    if (set.ratedMax <= set.ratedMin) {
        throw new ValidationError('ratedMax', 'ratedMin', 'Rating 最高值必须大于最低值');
    }
    if (raw.rated !== undefined) {
        set.ratingVersion = RATING_VERSION;
        set.rating_version = RATING_VERSION;
        set.is_rated = raw.rated === true || raw.rated === 'true' || raw.rated === 1 || raw.rated === '1';
    }
    if (tdoc?.maintainer) {
        const ids = [...new Set<number>(tdoc.maintainer)].sort((a, b) => a - b);
        set.contestAdminIds = ids;
        set.contest_admin_ids = ids;
    }
    if (Object.keys(set).length) await ContestModel.edit(h.domain._id, tid, set);
}

export async function apply(ctx: Context, config: BotApiConfig = {}) {
    clearOwnLoadWarning();
    await installBotApi(ctx, { db, Handler, ContestModel, DocumentModel, RecordModel, UserModel, SystemModel, STATUS, ratingVisual }, config);
    problemRatings = await installProblemRatingService(ctx, {
        db, DocumentModel, ProblemModel, ContestModel, RecordModel, DomainModel, ObjectId, STATUS, Schema, ScheduleModel,
        settlements, identityEligible,
        acquire: acquireLock,
        renew: async (domainId: string, owner: ObjectId) => {
            const renewed = await locks.updateOne({ _id: domainId, owner }, {
                $set: { expiresAt: new Date(Date.now() + 2 * 60 * 60 * 1000) },
            });
            if (!renewed.matchedCount) throw new Error(`Rating pool ${domainId} lock was lost`);
        },
        release: (domainId: string, owner: ObjectId) => locks.deleteOne({ _id: domainId, owner }),
    });
    await ctx.inject(['db'], async () => {
        await db.ensureIndexes(
            settlements,
            { key: { domainId: 1, contestId: 1, recordType: 1, uid: 1, active: 1 }, name: 'rating_settlement_lookup' },
            { key: { domainId: 1, active: 1 }, name: 'rating_settlement_active' },
            // Looking up the contests that already have a settlement.
            { key: { domainId: 1, recordType: 1, active: 1, reason: 1 }, name: 'rating_settlement_settled' },
        );
        await db.ensureIndexes(
            locks,
            { key: { expiresAt: 1 }, name: 'rating_lock_expiry', expireAfterSeconds: 0 },
        );
        await db.ensureIndexes(
            ratingNotifications,
            { key: { domainId: 1, status: 1 }, name: 'rating_notification_status' },
        );
    });
    installUserFields();
    patchUserRender();
    patchContestConfig(ctx);
    patchContestScoreboard(ctx);
    ctx.on('handler/before-prepare/ContestEdit#post', updateSettledContestDescription);
    ctx.on('handler/before-operation/ContestManagement', rejectSettledContestManagementMutation);
    ctx.on('handler/before-prepare', restrictContestPermissionsBeforeEnd);
    ctx.on('handler/before-operation/ContestDetail', attendWithRatingChoice);
    ctx.on('handler/before/ContestEdit', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestManagement', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestUser', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestBalloon', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestClarification', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestCode', restrictElevatedContestAccessBeforeEnd);
    ctx.on('handler/before/ContestScoreboard', restrictHiddenScoreboardBeforeEnd);
    ctx.on('handler/before/ContestFileDownload', restrictPrivateContestFileBeforeEnd);
    ctx.on('handler/before/ContestPrint', restrictContestPrintBeforeEnd);
    ctx.on('handler/before/RecordList', restrictContestRecordListBeforeEnd);
    ctx.on('handler/before/RecordDetail', restrictContestRecordDetailBeforeEnd);
    ctx.on('handler/create/ws', protectContestRecordConnections);
    ctx.on('handler/before/DiscussionNode', restrictContestDiscussionBeforeEnd);
    ctx.on('handler/before/DiscussionCreate', restrictContestDiscussionBeforeEnd);
    ctx.on('handler/before/DiscussionDetail', restrictContestDiscussionBeforeEnd);
    ctx.on('handler/before/DiscussionEdit', restrictContestDiscussionBeforeEnd);
    ctx.on('handler/before/DiscussionRaw', restrictContestDiscussionBeforeEnd);
    ctx.on('discussion/before-add', restrictContestDiscussionCreation);
    ctx.on('task/daily', async () => {
        await ratingUsers.updateMany({}, { $set: { rp: 0, rpInfo: {}, rank: null, level: 0 } });
    });
    ctx.on('handler/after', async (h: Handler) => {
        const body: any = h.response.body;
        await saveContestRatingConfig(h);
        hideRestrictedContestDiscussions(h, body);
        await patchRankingResponse(h, body);
        if (body?.template === 'main.html' || h.response.template === 'main.html') await replaceHomeRanking(h, body);
        if (h.response.template === 'home_messages.html') await decorateMessageBody(h, body);
        await exposeRatingParticipation(h, body);
        await exposeContestBoard(h, body);
        if (h.response.template === 'record_main.html' && body?.tdoc && body.tdoc.rule !== 'homework'
            && !h.args.fullStatus) {
            await prepareRecordSearch(h, body, {
                ContestModel, ProblemModel, RecordModel, UserModel, ObjectId,
                recordLimit: SystemModel.get('pagination.record'), editContestPermission: PERM.PERM_EDIT_CONTEST,
            }, selfRecordFilter(h));
        }
        removeDuplicateContestTopNavigation(h, body);
        if (body?.udoc) decorate(body.udoc);
        if (h.user) decorate(h.user);
        if (body?.ratingDynamicRecords && String(h.args.ratingDynamic) === '1') {
            const html = await h.renderHTML('partials/rating_record_list.html', { ...body, ratingContestPage: true });
            h.response.template = null;
            h.response.type = 'application/json';
            h.response.body = {
                html, page: body.page, rids: body.rdocs.map((record: any) => String(record._id)),
                socketUrl: body.ratingRecordsSocketUrl, limit: body.ratingRecordsLimit,
            };
        }
    });
    await ctx.inject(['worker'], async ({ worker }) => {
        worker.addHandler(RATING_SCHEDULE, async (task: any) => {
            const success = await calculateAll((payload) => ctx.logger.info('[rating-system] %s', payload.message));
            if (!success) throw new Error('Rating daily calculation failed for one or more domains');
            ctx.logger.info('[rating-system] daily calculation finished (%s)', task.executeAfter?.toISOString?.() || 'manual');
        });
        if (process.env.NODE_APP_INSTANCE === '0' || !process.env.NODE_APP_INSTANCE) {
            if (!await ScheduleModel.count({ type: 'schedule', subType: RATING_SCHEDULE })) {
                const next = new Date();
                next.setHours(24, 0, 0, 0);
                await ScheduleModel.add({
                    type: 'schedule', subType: RATING_SCHEDULE, executeAfter: next, interval: [1, 'day'],
                });
            }
        }
    });
    ctx.addScript(
        'rating', 'Calculate unsettled Rating, or recalculate a contest with contestId (tid) / all history with full=true',
        Schema.object({
            domainId: Schema.string().default(''),
            full: Schema.boolean().default(false).description('Rebuild every contest from the initial Rating'),
            contestId: Schema.string().default('').description('Recalculate this contest and subsequent settled contests'),
            tid: Schema.string().default('').description('Alias of contestId'),
        }),
        async ({ domainId, full, contestId = '', tid = '' }, report) => {
            contestId = contestId.trim();
            tid = tid.trim();
            if (contestId && tid && contestId.toLowerCase() !== tid.toLowerCase()) throw new Error('contestId 与 tid 不能指定不同的比赛');
            const target = contestId || tid;
            if (target) {
                if (full) throw new Error('指定比赛时请勿同时设置 full=true');
                if (!/^[\da-f]{24}$/i.test(target)) throw new Error('contestId 必须是比赛链接中的 24 位比赛 ID');
                const matches = await DocumentModel.coll.find({
                    docType: DocumentModel.TYPE_CONTEST, docId: new ObjectId(target),
                    ...domainId ? { domainId } : {},
                }).project({ domainId: 1, docId: 1 }).limit(2).toArray();
                if (!matches.length) throw new Error(`找不到指定比赛 ${target}${domainId ? `（域 ${domainId}）` : ''}`);
                if (matches.length > 1) throw new Error('多个域存在该比赛 ID，请同时指定 domainId');
                await calculateDomain(matches[0].domainId, report, { contestId: target.toLowerCase() });
                return true;
            }
            if (!domainId) return calculateAll(report, { full });
            await calculateDomain(domainId, report, { full });
            return true;
        },
    );
}
