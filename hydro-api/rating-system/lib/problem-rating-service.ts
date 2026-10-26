import { calculateProblemRatings } from './problem-rating-data';
import {
    estimateProblemRating, problemRatingColor, problemRatingValue, PROBLEM_RATING_VERSION, selectedProblemRating,
} from './problem-rating';

const JOB = 'rating-system.problem';
const protectedFields = ['difficulty', 'problemRating', 'problemRatingSources', 'legacyDifficulty'];
const pendingRating = () => {
    const { contestIds, ...rating } = estimateProblemRating([]);
    return { ...rating, computedAt: null, calibration: 'internalRating' };
};

export function createProblemRatingService(models: any) {
    const { db, DocumentModel, ObjectId } = models;
    const queue = db.collection('rating.problem.queue');
    async function mark(domainId: string, pids?: number[]) {
        const update: any = { $set: { revision: new ObjectId(), queuedAt: new Date() } };
        if (!pids) update.$set.full = true;
        else if (pids.length) update.$addToSet = { pids: { $each: pids } };
        await queue.updateOne({ _id: domainId }, update, { upsert: true });
    }
    async function drain(domainId: string, report: (payload: any) => any = () => {}) {
        const task = await queue.findOne({ _id: domainId });
        if (!task) return null;
        const owner = new ObjectId();
        try {
            await models.acquire(domainId, owner);
        } catch (error: any) {
            if (String(error.message).includes('is already settling')) {
                await report({ message: `Problem Rating domain ${domainId}: Rating 正在结算，题目更新已保留在队列中` });
                return null;
            }
            throw error;
        }
        try {
            if (await models.settlements.findOne({ domainId, active: false, pendingCommit: true }, { projection: { _id: 1 } })) {
                await report({ message: `Problem Rating domain ${domainId}: 选手 Rating 提交尚待恢复，题目更新继续排队` });
                return null;
            }
            const result = await calculateProblemRatings(models, domainId, task.full ? undefined : task.pids || [], report,
                () => models.renew(domainId, owner));
            // A judge/new edit arriving while this job runs changes revision, so no event is lost.
            await queue.deleteOne({ _id: domainId, revision: task.revision });
            return result;
        } finally {
            await models.release(domainId, owner);
        }
    }
    async function refresh(domainId: string, report: (payload: any) => any = () => {}, pids?: number[]) {
        await mark(domainId, pids);
        return drain(domainId, report);
    }
    async function migrate() {
        const domains = await models.DomainModel.getMulti().project({ _id: 1 }).toArray();
        for (const domain of domains) {
            // Keep the old manual/algorithm value for inspection; it is no longer used for scoring.
            // eslint-disable-next-line no-await-in-loop
            const updated = await DocumentModel.coll.updateMany({
                domainId: domain._id, docType: DocumentModel.TYPE_PROBLEM, 'problemRating.version': { $ne: PROBLEM_RATING_VERSION },
            }, [{ $set: {
                legacyDifficulty: { $ifNull: ['$legacyDifficulty', '$difficulty'] }, difficulty: 0,
                problemRating: pendingRating(), problemRatingSources: [],
            } }]);
            // eslint-disable-next-line no-await-in-loop
            if (updated.modifiedCount) await mark(domain._id);
        }
    }
    async function drainAll(report: (payload: any) => any) {
        const tasks = await queue.find({}).project({ _id: 1 }).toArray();
        let success = true;
        for (const task of tasks) {
            try {
                // eslint-disable-next-line no-await-in-loop
                await drain(task._id, report);
            } catch (error: any) {
                success = false;
                // eslint-disable-next-line no-await-in-loop
                await report({ message: `Problem Rating domain ${task._id} failed: ${error.message}` });
            }
        }
        return success;
    }
    return { mark, drain, refresh, migrate, drainAll };
}

export async function installProblemRatingService(ctx: any, models: any) {
    // Some non-web Hydro runtimes load a limited model facade (e.g. maintenance tools).
    if (!models.ProblemModel || !Number.isInteger(models.DocumentModel.TYPE_PROBLEM)) return null;
    const { ProblemModel, DomainModel, DocumentModel, STATUS, Schema, ScheduleModel } = models;
    const service = createProblemRatingService(models);
    const fields = ['problemRating'];
    for (const key of ['PROJECTION_LIST', 'PROJECTION_PUBLIC']) {
        for (const field of fields) if (!ProblemModel[key].includes(field)) ProblemModel[key].push(field);
    }
    Object.assign(ProblemModel, { ratingValue: problemRatingValue, ratingColor: problemRatingColor, selectedRating: selectedProblemRating });
    // Prevent third-party templates from invoking Hydro's acceptance-ratio fallback as well.
    ctx.on('handler/create/http', (handler: any) => {
        const render = handler.renderHTML.bind(handler);
        handler.renderHTML = (name: string, args: any = {}) => render(name, {
            ...args, lib: { ...(args.lib || {}), difficulty: () => null },
        });
    });
    ctx.on('problem/list', (query: any, handler: any, order: string[]) => {
        // Hydro's fallback text search searches the whole q string, including difficulty:1600.
        // Recover combined text/rating searches while retaining its existing permission filters.
        const raw = String(handler?.args?.q || '');
        const providers = (global as any).Hydro?.module?.problemSearch || {};
        if (!handler?.queryContext?.fail || !selectedProblemRating(raw) || Object.keys(providers).length
            || !Array.isArray(query.docId?.$in) || query.docId.$in.length) return;
        const text = raw.replace(/(^|\s)(?:category|difficulty|namespace):(?:"[^"]*"|'[^']*'|[^\s]+)/g, ' ')
            .replace(/"([^"]*)"|'([^']*)'/g, (_match, double, single) => double ?? single).trim();
        if (!text) return;
        delete query.docId;
        const literal = text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const pattern = new RegExp(literal, 'i');
        query.$and ||= [];
        query.$and.push({ $or: [{ title: pattern }, { pid: pattern }, { tag: text }] });
        handler.queryContext.fail = false;
        order.splice(0);
    });
    await ctx.inject(['db'], async () => { await service.migrate(); });
    ctx.on('problem/before-add', (_domainId: string, _content: any, _owner: number, _pid: number, args: any) => {
        if (typeof args.difficulty === 'number') args.legacyDifficulty = args.difficulty;
        args.difficulty = 0;
        args.problemRating = pendingRating();
        args.problemRatingSources = [];
    });
    ctx.on('problem/before-edit', (set: any, unset: any) => {
        for (const field of protectedFields) { delete set[field]; delete unset[field]; }
    });
    ctx.on('problem/add', (problem: any, pid: number) => service.mark(problem.domainId, [pid]));
    ctx.on('problem/edit', (problem: any) => problem?.domainId && Number.isInteger(problem.docId)
        ? service.mark(problem.domainId, [problem.docId]) : undefined);
    ctx.on('record/judge', (record: any) => {
        if (!record || !Number.isSafeInteger(record.pid) || ![STATUS.STATUS_ACCEPTED, STATUS.STATUS_WRONG_ANSWER,
            STATUS.STATUS_TIME_LIMIT_EXCEEDED, STATUS.STATUS_MEMORY_LIMIT_EXCEEDED, STATUS.STATUS_OUTPUT_LIMIT_EXCEEDED,
            STATUS.STATUS_RUNTIME_ERROR, STATUS.STATUS_COMPILE_ERROR, STATUS.STATUS_HACKED, STATUS.STATUS_CANCELED,
            STATUS.STATUS_SYSTEM_ERROR].includes(record.status)) return;
        return service.mark(record.domainId, [record.pid]);
    });
    // Closing practice windows and ending/unlocking contests are covered even without new judgments.
    ctx.on('task/daily', async () => {
        const domains = await DomainModel.getMulti().project({ _id: 1 }).toArray();
        for (const domain of domains) await service.mark(domain._id);
    });
    ctx.on('contest/edit', (contest: any) => contest?.domainId ? service.mark(contest.domainId, contest.pids) : undefined);
    await ctx.inject(['worker'], async ({ worker }: any) => {
        worker.addHandler(JOB, async () => {
            const success = await service.drainAll((payload) => ctx.logger.info('[rating-system] %s', payload.message));
            if (!success) throw new Error('Problem Rating update failed; queue retained for retry');
        });
        if (!process.env.NODE_APP_INSTANCE || process.env.NODE_APP_INSTANCE === '0') {
            if (!await ScheduleModel.count({ type: 'schedule', subType: JOB })) {
                await ScheduleModel.add({ type: 'schedule', subType: JOB, executeAfter: new Date(), interval: [1, 'minute'] });
            }
        }
    });
    ctx.addScript('problem-rating', 'Rebuild automatic site problem difficulty Ratings', Schema.object({
        domainId: Schema.string().default(''), pid: Schema.string().default(''),
    }), async ({ domainId, pid = '' }: any, report: any) => {
        if (pid && !domainId) throw new Error('指定题目时请同时填写 domainId');
        if (pid) {
            const problem = await ProblemModel.get(domainId, pid, ['docId']);
            if (!problem) throw new Error(`找不到题目 ${pid}`);
            await service.refresh(domainId, report, [problem.docId]);
            return true;
        }
        if (domainId) {
            await service.refresh(domainId, report);
            return true;
        }
        const domains = await DomainModel.getMulti().project({ _id: 1 }).toArray();
        for (const domain of domains) await service.mark(domain._id);
        return service.drainAll(report);
    });
    return service;
}
