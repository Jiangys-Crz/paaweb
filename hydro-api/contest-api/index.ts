import { Context, Handler, param, Types, ContestModel, RecordModel, UserModel, ProblemModel, PERM, PRIV, moment, getAlphabeticId, STATUS_TEXTS, SettingModel } from 'hydrooj';
import { ObjectId } from 'mongodb';

class ContestApiBaseHandler extends Handler {
    @param('tid', Types.ObjectId)
    async prepare(domainId: string, tid: ObjectId) {
        if (!this.user.hasPerm(PERM.PERM_EDIT_CONTEST) && !this.user.hasPriv(PRIV.PRIV_ADMIN)) {
            throw new Error('Permission denied');
        }
    }
}

class ContestApiSubmissionsHandler extends ContestApiBaseHandler {
    @param('tid', Types.ObjectId)
    async get(domainId: string, tid: ObjectId) {
        const tdoc = await ContestModel.get(domainId, tid);
        if (!tdoc) throw new Error('Contest not found');

        const rdocs = await RecordModel.getMulti(domainId, { contest: tid }).toArray();
        const uids = Array.from(new Set(rdocs.map((i) => i.uid)));
        const udict = await UserModel.getList(domainId, uids);
        const pdict = await ProblemModel.getList(domainId, tdoc.pids, true, false, ProblemModel.PROJECTION_CONTEST_LIST);
        
        this.response.body = rdocs.map((item) => {
            return {
                rid: item._id.toString(),
                status: STATUS_TEXTS[item.status] || 'Unknown',
                score: (item.score || 0).toString(),
                problem: pdict[item.pid]?.title || '',
                username: udict[item.uid]?.uname || '',
                runtime: (item.time || 0).toString(),
                memory: (item.memory || 0).toString(),
                language: SettingModel.langs[item.lang]?.display || item.lang,
                submit_time: moment(item._id.getTimestamp()).format('YYYY-MM-DD HH:mm:ss:SSS'),
                code: item.code || '',
            };
        });
    }
}

class ContestApiProblemsHandler extends ContestApiBaseHandler {
    @param('tid', Types.ObjectId)
    async get(domainId: string, tid: ObjectId) {
        const tdoc = await ContestModel.get(domainId, tid);
        if (!tdoc) throw new Error('Contest not found');

        const pdict = await ProblemModel.getList(domainId, tdoc.pids, true, true, ProblemModel.PROJECTION_CONTEST_LIST);
        
        this.response.body = tdoc.pids.map((pid, index) => {
            const pdoc = pdict[pid];
            const config = pdoc?.config as any || {};
            return {
                problem_id: getAlphabeticId(index),
                problem_name: pdoc?.title || '',
                problem_score: (tdoc.score?.[pid] || 100).toString(),
                time: (config.timeMax || 0).toString(),
                memory: (config.memoryMax || 0).toString(),
            };
        });
    }
}

export async function apply(ctx: Context) {
    ctx.Route('contest_api_submissions', '/api/contest/:tid/submissions', ContestApiSubmissionsHandler);
    ctx.Route('contest_api_problems', '/api/contest/:tid/problems', ContestApiProblemsHandler);
}
