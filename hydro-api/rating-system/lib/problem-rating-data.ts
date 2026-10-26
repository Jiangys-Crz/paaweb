import { INITIAL_INTERNAL_RATING } from './rating';
import { estimateProblemRating, PRACTICE_WINDOW, ProblemSample, problemRatingValue } from './problem-rating';

const timeOf = (value: any) => new Date(value).getTime();
const recordTime = (record: any) => record._id?.getTimestamp?.().getTime();
const validState = (state: any) => ({
    strength: Number.isFinite(state?.internalRating) ? state.internalRating : INITIAL_INTERNAL_RATING,
    ratedCount: Number.isSafeInteger(state?.ratedCount) && state.ratedCount >= 0 ? state.ratedCount : 0,
});

export async function calculateProblemRatings(
    models: any, domainId: string, pids?: number[], report: (payload: any) => any = () => {},
    renew: () => Promise<void> = async () => {}, now = Date.now(),
) {
    const { DocumentModel, RecordModel, ContestModel, settlements, STATUS } = models;
    const final = [
        STATUS.STATUS_ACCEPTED, STATUS.STATUS_WRONG_ANSWER, STATUS.STATUS_TIME_LIMIT_EXCEEDED,
        STATUS.STATUS_MEMORY_LIMIT_EXCEEDED, STATUS.STATUS_OUTPUT_LIMIT_EXCEEDED,
        STATUS.STATUS_RUNTIME_ERROR, STATUS.STATUS_COMPILE_ERROR, STATUS.STATUS_HACKED,
    ].filter(Number.isInteger);
    const pending = [STATUS.STATUS_WAITING, STATUS.STATUS_FETCHED, STATUS.STATUS_COMPILING, STATUS.STATUS_JUDGING]
        .filter(Number.isInteger);
    const allContests = await ContestModel.getMulti(domainId).project({
        docId: 1, pids: 1, rule: 1, beginAt: 1, endAt: 1, lockAt: 1, unlocked: 1, langs: 1,
        owner: 1, publisherId: 1, publisher_id: 1, maintainer: 1, contestAdminIds: 1, contest_admin_ids: 1,
    }).toArray();
    const summaries = await settlements.find({ domainId, active: true, recordType: 'contest', reason: 'settled' }, {
        projection: { contestId: 1, stateSnapshot: 1 },
    }).toArray();
    const snapshots = new Map(summaries.map((summary: any) => [String(summary.contestId), summary]));
    const eligible = allContests.filter((contest: any) => contest.rule !== 'homework'
        && timeOf(contest.endAt) <= now && !ContestModel.isLocked(contest)
        && snapshots.has(String(contest.docId)));
    const unfinished = await RecordModel.getMulti(domainId, {
        contest: { $in: eligible.map((contest: any) => contest.docId) }, status: { $in: pending },
    }).project({ contest: 1 }).toArray();
    const blocked = new Set(unfinished.map((record: any) => String(record.contest)));
    const contexts: any[] = [];
    for (const contest of eligible) {
        if (blocked.has(String(contest.docId))) continue;
        const snapshot: any = snapshots.get(String(contest.docId));
        // Fresh journals reflect rejudging; the frozen pre-contest strengths remain the calibration.
        // eslint-disable-next-line no-await-in-loop
        const statuses = await ContestModel.getMultiStatus(domainId, {
            docId: contest.docId, uid: { $in: Object.keys(snapshot.stateSnapshot || {}).map(Number) },
        }).project({ uid: 1, attend: 1, unrank: 1, virtual: 1, practice: 1, observer: 1,
            ratingDisqualified: 1, disqualified: 1, journal: 1 }).toArray();
        const excluded = new Set([contest.owner, contest.publisherId, contest.publisher_id,
            ...(contest.maintainer || contest.contestAdminIds || contest.contest_admin_ids || [])]);
        const participants = statuses.filter((status: any) => !excluded.has(status.uid)
            && snapshot.stateSnapshot?.[status.uid] && models.identityEligible(status))
            .map((status: any) => ({ ...status, validJournal: (status.journal || []).filter((entry: any) => {
                const at = entry.rid?.getTimestamp?.().getTime();
                return contest.pids.includes(entry.pid) && final.includes(entry.status)
                    && at >= timeOf(contest.beginAt) && at <= timeOf(contest.endAt)
                    && (!contest.langs?.length || contest.langs.includes(entry.lang));
            }) })).filter((status: any) => status.validJournal.length);
        contexts.push({ contest, participants, snapshot });
    }
    // Only historical strength is used for practice. A future contest must not improve an old attempt.
    const history = await settlements.find({ domainId, active: true, recordType: 'user' }, {
        projection: { uid: 1, after: 1, endTime: 1, settledAt: 1 },
    }).sort({ endTime: 1, settledAt: 1, _id: 1 }).toArray();
    const histories = new Map<number, any[]>();
    for (const entry of history) {
        if (!Number.isFinite(timeOf(entry.endTime))) continue;
        const list = histories.get(entry.uid) || [];
        list.push(entry);
        histories.set(entry.uid, list);
    }
    function strengthAt(uid: number, at: number) {
        const list = histories.get(uid) || [];
        let low = 0;
        let high = list.length;
        while (low < high) {
            const mid = (low + high) >>> 1;
            if (timeOf(list[mid].endTime) <= at) low = mid + 1;
            else high = mid;
        }
        return validState(list[low - 1]?.after);
    }
    const cursor = DocumentModel.coll.find({
        domainId, docType: DocumentModel.TYPE_PROBLEM, ...(pids ? { docId: { $in: pids } } : {}),
    }, { projection: { _id: 1, docId: 1, owner: 1, maintainer: 1, difficulty: 1, problemRating: 1, problemRatingSources: 1 } }).sort({ docId: 1 });
    let batch: any[] = [];
    let total = 0;
    let rated = 0;
    async function calculateBatch(problems: any[]) {
        const samples = new Map<number, ProblemSample[]>(problems.map((problem) => [problem.docId, []]));
        const byId = new Map(problems.map((problem) => [problem.docId, problem]));
        const excluded = (pid: number, uid: number) => uid <= 1 || byId.get(pid)?.owner === uid
            || byId.get(pid)?.maintainer?.includes(uid);
        for (const { contest, participants, snapshot } of contexts) {
            for (const pid of contest.pids) {
                if (!samples.has(pid)) continue;
                for (const participant of participants) {
                    if (excluded(pid, participant.uid)) continue;
                    samples.get(pid).push({
                        uid: participant.uid, ...validState(snapshot.stateSnapshot[participant.uid]),
                        at: timeOf(contest.beginAt), source: 'contest',
                        contestId: String(contest.docId),
                        solved: participant.validJournal.some((entry: any) => entry.pid === pid && entry.status === STATUS.STATUS_ACCEPTED),
                    });
                }
            }
        }
        const practice = new Map<string, any>();
        const records = RecordModel.getMulti(domainId, {
            contest: null, pid: { $in: problems.map((problem) => problem.docId) }, status: { $in: final },
            input: { $exists: false }, hackTarget: { $exists: false }, 'files.hack': { $exists: false },
        }).project({ _id: 1, uid: 1, pid: 1, status: 1 }).sort({ _id: 1 });
        for await (const record of records) {
            if (excluded(record.pid, record.uid)) continue;
            const at = recordTime(record);
            if (!Number.isFinite(at) || at > now) continue;
            // Disregard practice made while this problem is being used in a live contest.
            if (allContests.some((contest: any) => contest.rule !== 'homework' && contest.pids?.includes(record.pid)
                && at >= timeOf(contest.beginAt) && at <= timeOf(contest.endAt))) continue;
            const key = `${record.pid}:${record.uid}`;
            let sample = practice.get(key);
            if (!sample) {
                sample = { uid: record.uid, pid: record.pid, ...strengthAt(record.uid, at), at, solved: false, source: 'practice' };
                practice.set(key, sample);
            }
            if (at <= sample.at + PRACTICE_WINDOW && record.status === STATUS.STATUS_ACCEPTED) sample.solved = true;
        }
        for (const sample of practice.values()) {
            // Wait for the whole window for successes and failures alike to avoid early-AC bias.
            if (sample.at + PRACTICE_WINDOW <= now) samples.get(sample.pid).push(sample);
        }
        const operations = problems.flatMap((problem) => {
            // Retain a published estimate while one of its source contests is being rejudged.
            if (problemRatingValue(problem) !== null && problem.problemRatingSources?.some((id: string) => blocked.has(id))) {
                rated++;
                return [];
            }
            const { contestIds, ...result } = estimateProblemRating(samples.get(problem.docId));
            if (result.rating !== null) rated++;
            return [{ updateOne: { filter: { _id: problem._id, domainId, docType: DocumentModel.TYPE_PROBLEM }, update: { $set: {
                difficulty: result.rating || 0, problemRating: { ...result, computedAt: new Date(now), calibration: 'internalRating' },
                problemRatingSources: contestIds,
            } } } }];
        });
        await renew();
        if (operations.length) await DocumentModel.coll.bulkWrite(operations, { ordered: false });
        total += problems.length;
    }
    for await (const problem of cursor) {
        batch.push(problem);
        if (batch.length < 100) continue;
        // eslint-disable-next-line no-await-in-loop
        await calculateBatch(batch);
        batch = [];
    }
    if (batch.length) await calculateBatch(batch);
    await report({ message: `Problem Rating domain ${domainId} finished (${total} problems, ${rated} rated, ${total - rated} awaiting samples)` });
    return { total, rated, pending: total - rated };
}
