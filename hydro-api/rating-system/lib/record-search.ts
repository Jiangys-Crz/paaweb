export function searchText(value: any): string {
    return (typeof value === 'string' || typeof value === 'number' ? String(value) : '')
        .trim().slice(0, 200).normalize('NFKC').toLocaleLowerCase();
}

export function matchProblems(problems: any[], value: any): number[] {
    const keyword = searchText(value);
    if (!keyword) return problems.map((problem) => problem.docId);
    const exact = problems.filter((problem) => [problem.label, problem.docId, problem.pid, `${problem.label} - ${problem.title}`]
        .some((identifier) => searchText(identifier) === keyword));
    return (exact.length ? exact : problems.filter((problem) => searchText(problem.title).includes(keyword)))
        .map((problem) => problem.docId);
}

export function matchUsers(users: any[], value: any): number[] {
    const keyword = searchText(value);
    return users.filter((user) => !keyword || String(user._id) === keyword || searchText(user.uname).includes(keyword))
        .map((user) => user._id);
}

// Hydro submits corrections from the problem set, without a contest ID. Only include
// ordinary submissions to this contest's problems after its end, never another contest's records.
export function recordQuery(tdoc: any, filters: { pids: number[]; uids?: number[]; selfUid?: number; status?: number; lang?: string }, ObjectId: any, now = Date.now()) {
    const end = new Date(tdoc.endAt).getTime();
    const query: any = { pid: { $in: filters.pids } };
    if (Number.isFinite(end) && end <= now) {
        query.$or = [
            { contest: tdoc.docId },
            { contest: null, _id: { $gte: ObjectId.createFromTime(Math.ceil(end / 1000)) } },
        ];
    } else query.contest = tdoc.docId;
    if (filters.selfUid !== undefined) {
        query.uid = !filters.uids || filters.uids.includes(filters.selfUid) ? filters.selfUid : { $in: [] };
    } else if (filters.uids) query.uid = { $in: filters.uids };
    if (typeof filters.status === 'number') query.status = filters.status;
    if (filters.lang) query.lang = filters.lang;
    return query;
}

async function contestProblems(h: any, tdoc: any, ProblemModel: any) {
    const pdict = await ProblemModel.getList(h.domain._id, tdoc.pids, true, false, ProblemModel.PROJECTION_CONTEST_LIST);
    return { pdict, problems: tdoc.pids.map((pid: number, index: number) => ({
        docId: pid, pid: pdict[pid]?.pid || '', title: pdict[pid]?.title || '', label: alphabeticId(index),
    })) };
}

async function matchingAuthors(h: any, tdoc: any, models: any, keyword: string) {
    if (!searchText(keyword)) return undefined;
    const ids = await models.RecordModel.coll.distinct('uid', {
        domainId: h.domain._id, ...recordQuery(tdoc, { pids: tdoc.pids }, models.ObjectId),
    });
    const users = await models.UserModel.getListForRender(h.domain._id, ids);
    return matchUsers(Object.values(users), keyword);
}

// Called only after Hydro's native RecordList handler has authorized the contest request.
export async function prepareRecordSearch(h: any, body: any, models: any, self: boolean) {
    const { ContestModel, ProblemModel, RecordModel, UserModel } = models;
    const tdoc = body.tdoc;
    const { pdict, problems } = await contestProblems(h, tdoc, ProblemModel);
    const problemKeyword = typeof h.args.problemKeyword === 'string' ? h.args.problemKeyword.slice(0, 200) : String(body.filterPid || '');
    const userKeyword = typeof h.args.userKeyword === 'string' ? h.args.userKeyword.slice(0, 200)
        : self ? '' : String(body.filterUidOrName || '');
    const query = recordQuery(tdoc, {
        pids: matchProblems(problems, problemKeyword), uids: await matchingAuthors(h, tdoc, models, userKeyword),
        selfUid: self ? h.user._id : undefined, status: body.filterStatus, lang: body.filterLang,
    }, models.ObjectId);
    const limit = Math.max(1, Number(models.recordLimit) || 20);
    const page = Math.max(1, Number(body.page) || 1);
    const projection = Object.fromEntries(RecordModel.PROJECTION_LIST.map((field: string) => [field, 1]));
    const records = await RecordModel.getMulti(h.domain._id, query).sort('_id', -1)
        .project(projection).skip((page - 1) * limit).limit(limit + 1).toArray();
    body.ratingRecordsHasNext = records.length > limit;
    body.rdocs = records.slice(0, limit).map((record: any) => (
        !h.user.own(tdoc) && !h.user.hasPerm(models.editContestPermission)
            ? ContestModel.applyProjection(tdoc, record, h.user) : record
    ));
    body.pdict = pdict;
    body.udict = await UserModel.getList(h.domain._id, body.rdocs.map((record: any) => record.uid));
    body.ratingDynamicRecords = true;
    body.ratingRecordProblems = problems;
    body.ratingRecordProblemKeyword = problemKeyword;
    body.ratingRecordUserKeyword = userKeyword;
    body.ratingRecordsSelfUid = self ? h.user._id : null;
    body.ratingRecordsLimit = limit;
    const queryArgs: any = { tid: tdoc.docId, problemKeyword, userKeyword };
    if (self) queryArgs.uidOrName = h.user._id;
    if (body.filterLang) queryArgs.lang = body.filterLang;
    if (typeof body.filterStatus === 'number') queryArgs.status = body.filterStatus;
    body.ratingRecordsPreviousUrl = page > 1 ? h.url('record_main', { query: { ...queryArgs, page: page - 1 } }) : null;
    body.ratingRecordsNextUrl = body.ratingRecordsHasNext ? h.url('record_main', { query: { ...queryArgs, page: page + 1 } }) : null;
    body.ratingRecordsSocketUrl = `record-conn?${new URLSearchParams({
        ...Object.fromEntries(Object.entries(queryArgs).map(([key, value]) => [key, String(value)])),
        ratingBoard: '1', domainId: h.domain._id,
    })}`;
}

export function recordInScope(tdoc: any, record: any, domainId: string, now = Date.now()) {
    if (record.domainId !== domainId || !tdoc.pids.includes(record.pid) || typeof record.input === 'string'
        || Array.isArray(record.input)) return false;
    if (String(record.contest) === String(tdoc.docId)) return true;
    const end = new Date(tdoc.endAt).getTime();
    return record.contest == null && end <= now && record._id?.getTimestamp?.().getTime() >= end;
}

export async function prepareRecordConnection(h: any, models: any) {
    const tdoc = h.tdoc;
    const { problems } = await contestProblems(h, tdoc, models.ProblemModel);
    const pids = new Set(matchProblems(problems, h.args.problemKeyword));
    // Resolve each arriving author's name as well, so a new post-contest author is searchable.
    const keyword = searchText(h.args.userKeyword);
    const status = h.args.status === '' || h.args.status === undefined ? undefined : Number(h.args.status);
    const original = h.onRecordChange.bind(h);
    const originalQueue = h.queueSend.bind(h);
    const visible = new Set<string>();
    let requested = new Set<string>();
    const latest = new Map<string, object>();
    const matched = new Map<string, boolean>();
    if (typeof h.message === 'function') {
        const originalMessage = h.message.bind(h);
        h.message = (message: any) => {
            if (Array.isArray(message?.rids)) requested = new Set(message.rids.map(String));
            return originalMessage(message);
        };
    }
    h.queueSend = (rid: string, render: any) => {
        if (!matched.get(rid)) {
            visible.delete(rid);
            originalQueue(rid, async () => ({ remove: rid }));
            return;
        }
        visible.add(rid);
        // Recheck the filter at queue flush time, in case a verdict changed while rendering.
        originalQueue(rid, async () => {
            const message = await render();
            return visible.has(rid) && matched.get(rid) ? message : { remove: rid };
        });
    };
    const filtered = async (record: any, ...args: any[]) => {
        if (!recordInScope(tdoc, record, h.domain._id)) return;
        const rid = String(record._id);
        const version = {};
        latest.set(rid, version);
        matched.set(rid, false);
        let matches = pids.has(record.pid) && (!Number.isFinite(status) || record.status === status)
            && (!h.args.lang || record.lang === h.args.lang);
        if (typeof h.uid === 'number') matches &&= record.uid === h.uid;
        if (matches && keyword) {
            const user = await models.UserModel.getById(h.domain._id, record.uid);
            matches = !!user && matchUsers([user], keyword).length > 0;
        }
        if (latest.get(rid) !== version) return;
        matched.set(rid, matches);
        if (!matches) {
            if (visible.delete(rid)) {
                originalQueue(rid, async () => ({ remove: rid }));
                return;
            }
            // A verdict may change between HTTP rendering and the initial socket
            // subscription. Let native authorization run before removing that initial row.
            if (!requested.has(rid)) return;
        }
        // Native authorization/projection still runs. A correction uses the contest's
        // display labels here; its stored contest field and judging behavior stay untouched.
        return original(record.contest ? record : { ...record, contest: tdoc.docId }, ...args);
    };
    h.onRecordChange = filtered;
    // Hydro subscribes the decorator's stored function rather than the instance method.
    h.__subscribe = (h.__subscribe || []).map((entry: any) => entry.name === 'record/change'
        ? { ...entry, target: filtered } : entry);
}

function alphabeticId(index: number) {
    let result = '';
    for (let value = index + 1; value > 0; value = Math.floor((value - 1) / 26)) {
        result = String.fromCharCode(65 + (value - 1) % 26) + result;
    }
    return result;
}
