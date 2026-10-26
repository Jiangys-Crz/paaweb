import { Context } from 'hydrooj';
import { ContestScoreboardHandler } from 'hydrooj/src/handler/contest';
import { PRIV } from 'hydrooj/src/model/builtin';
import { PrivilegeError } from 'hydrooj/src/error';

export function apply(ctx: Context) {
    const originalGet = ContestScoreboardHandler.prototype.get;
    
    ContestScoreboardHandler.prototype.get = async function (domainId: string, tid: any, viewId: string = 'default') {
        // "只有超级管理员可以打开比赛成绩表，其他用户都不能打开比赛成绩表"
        // 确保仅针对比赛（排除作业），并检查用户是否拥有 PRIV_ALL（超级管理员）
        if (this.tdoc && this.tdoc.rule !== 'homework' && !this.user.hasPriv(PRIV.PRIV_ALL)) {
            throw new PrivilegeError(PRIV.PRIV_ALL);
        }
        return await originalGet.call(this, domainId, tid, viewId);
    };

    // 比赛结束后刷新Cookie强制所有用户退出登录
    let lastCheck = new Date();
    ctx.setInterval(async () => {
        const now = new Date();
        const endedContests = await ctx.db.collection('document').find({
            docType: 30, // TYPE_CONTEST
            rule: { $ne: 'homework' },
            endAt: { $gte: lastCheck, $lte: now } // 改为 $gte 确保不会漏掉正好卡在边缘的结束时间
        }).toArray();

        if (endedContests.length > 0) {
            // TYPE_SESSION = 0，清除所有用户的登录会话状态，强制所有人下线
            await ctx.db.collection('token').deleteMany({ tokenType: 0 });
            // 清除所有的 contestMode loginip 绑定（如果启用了比赛模式IP绑定）
            await ctx.db.collection('user').updateMany({}, { $unset: { loginip: 1 } });
        }
        lastCheck = now;
    }, 10000); // 缩短轮询周期到10秒，更及时地响应比赛结束
}
