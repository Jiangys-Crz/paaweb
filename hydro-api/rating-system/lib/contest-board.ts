// Presentation data only: native rows remain the source of ranks, totals and record links.
// Do not attach contest journals or hidden record IDs to the response.
export function buildContestBoard(tdoc: any, rows: any[][], pdict: any, statuses: any[], frozen: boolean, acceptedStatus: number) {
    const acm = tdoc.rule === 'acm';
    const headers = rows[0] || [];
    const problems = headers.map((cell, index) => ({ cell, index }))
        .filter(({ cell }) => cell.type === 'problem')
        .map(({ cell, index }) => ({
            pid: cell.raw, label: cell.value, index, title: pdict[cell.raw]?.title || '',
            maxScore: Number(tdoc.score?.[cell.raw] ?? 100),
            submitted: 0, attempted: 0, accepted: 0, firstSolved: -1, lastSolved: -1,
            scores: [] as number[],
        }));
    const byUid = new Map(statuses.map((doc) => [doc.uid, doc]));
    const beginAt = new Date(tdoc.beginAt).getTime();
    const lockAt = frozen ? new Date(tdoc.lockAt).getTime() : Infinity;
    const participants = rows.slice(1).map((row, index) => {
        const uid = row.find((cell) => cell.type === 'user')?.raw;
        const rank = String(row.find((cell) => cell.type === 'rank')?.value ?? '0');
        const status = byUid.get(uid) || {};
        // ACM always uses display when lockAt is requested. Score rules switch at freeze time.
        const details = (frozen ? status.display : status.detail) || {};
        const total = row.find((cell) => cell.type === (acm ? 'time' : 'total_score'));
        const totals = String(total?.value ?? '0').split('\n');
        const visibleJournal = (status.journal || []).filter((entry: any) => (
            entry.rid?.getTimestamp?.().getTime() <= lockAt
        ));
        return {
            uid, index: index + 1, rank: rank === '0' ? '*' : rank, unrank: rank === '0',
            school: '', displayName: '',
            solved: acm ? totals[0] : undefined,
            // Hydro stores H:mm in its native total cell; the external board uses minutes.
            penalty: acm ? clockMinutes(totals[1]) : undefined,
            score: acm ? undefined : total?.value ?? 0,
            problems: problems.map((problem) => {
                const cell = row[problem.index] || {};
                const detail = details[problem.pid] || {};
                const journal = visibleJournal.filter((entry: any) => entry.pid === problem.pid);
                const accepted = journal.filter((entry: any) => entry.status === acceptedStatus);
                problem.submitted += journal.length;
                if (journal.length) problem.attempted++;
                if (accepted.length) {
                    problem.accepted++;
                    const start = new Date(tdoc.duration ? status.startAt || tdoc.beginAt : tdoc.beginAt).getTime();
                    const minutes = Math.floor((accepted[0].rid.getTimestamp().getTime() - start) / 60000);
                    problem.firstSolved = problem.firstSolved < 0 ? minutes : Math.min(problem.firstSolved, minutes);
                    problem.lastSolved = Math.max(problem.lastSolved, minutes);
                }
                const record = cell.type === 'records' ? cell.raw?.[0] : cell;
                const hasRecord = !!record?.raw;
                const pending = Number(detail.npending || 0);
                const score = Number(record?.score ?? detail.score ?? 0);
                const nativeValue = Number(String(record?.value ?? '').split('<')[0]);
                const value = hasRecord && Number.isFinite(nativeValue) ? nativeValue : '';
                if (journal.length) problem.scores.push(acm ? score : Number(value) || 0);
                const isAccepted = hasRecord && score >= 100;
                const className = acm
                    ? isAccepted ? (cell.style ? 'cell-problem-first-ac' : 'cell-problem-ac')
                        : pending ? 'cell-problem-frozen' : hasRecord ? 'cell-problem-wa' : 'cell-problem-empty'
                    : pending ? 'cell-problem-frozen' : hasRecord
                        ? `cell-gplt-level-${score <= 25 ? 1 : score <= 50 ? 2 : score <= 75 ? 3 : 4}`
                        : 'cell-problem-empty';
                const time = hasRecord ? Math.floor((record.raw.getTimestamp().getTime() - beginAt) / 60000) : 0;
                return {
                    className, rid: record?.raw, hover: cell.hover,
                    symbol: isAccepted ? '+' : pending ? `? ${pending}` : hasRecord ? '-' : '',
                    info: pending && !isAccepted ? `${detail.naccept || 0}/${time}`
                        : hasRecord ? `${Number(detail.naccept || 0) + (isAccepted ? 1 : 0)}/${time}` : '',
                    value,
                    // Preserve Hydro's separate post-contest practice score without changing the contest score.
                    practice: cell.type === 'records' ? cell.raw?.[1] : undefined,
                    pending,
                };
            }),
        };
    });
    const summaries = (acm
        ? [['Submitted', 'submitted'], ['Attempted', 'attempted'], ['Accepted', 'accepted'], ['First Solved', 'firstSolved'], ['Last Solved', 'lastSolved']]
        : [['Submitted', 'submitted'], ['Attempted', 'attempted'], ['Average Score', 'average'], ['Max Score', 'maximum'], ['Min Score', 'minimum']]
    ).map(([label, field]) => ({
        label,
        values: problems.map((problem) => {
            let value: number | string = (problem as any)[field];
            if (field === 'average') value = problem.scores.length
                ? (problem.scores.reduce((sum, score) => sum + score, 0) / problem.scores.length).toFixed(1) : '-';
            if (field === 'maximum') value = problem.scores.length ? Math.max(...problem.scores) : '-';
            if (field === 'minimum') value = problem.scores.length ? Math.min(...problem.scores) : '-';
            if ((field === 'firstSolved' || field === 'lastSolved') && value === -1) value = '-';
            return {
                value,
                percentage: acm && (field === 'attempted' || field === 'accepted')
                    ? `${problem.submitted ? Math.round(Number(value) / problem.submitted * 100) : 0}%` : null,
            };
        }),
    }));
    return {
        acm, problems: problems.map(({ scores, ...problem }) => problem), participants, summaries,
        width: Math.max(900, 40 + 48 + 130 + 220 + (acm ? 100 : 70) + problems.length * (acm ? 54 : 42)),
        prefixColumns: acm ? 6 : 5,
    };
}

function clockMinutes(value = '0:00') {
    const [hours, minutes] = value.split(':').map(Number);
    return Number.isFinite(hours) && Number.isFinite(minutes) ? hours * 60 + minutes : 0;
}
