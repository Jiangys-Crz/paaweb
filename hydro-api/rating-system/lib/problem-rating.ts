// Site calibration inspired by Codeforces' published solve-probability model:
// https://codeforces.com/blog/entry/62865. This is our estimator, not CF's private implementation.
export const PROBLEM_RATING_VERSION = 'site_problem_rating_v1';
export const PROBLEM_RATING_MIN = 800;
export const PROBLEM_RATING_MAX = 3500;
export const PROBLEM_RATING_MIN_USERS = 5;
export const PRACTICE_WINDOW = 24 * 60 * 60 * 1000;

export interface ProblemSample {
    uid: number;
    strength: number;
    ratedCount: number;
    solved: boolean;
    at: number;
    source: 'contest' | 'practice';
    contestId?: string;
}

export function solveProbability(strength: number, difficulty: number) {
    const exponent = Math.max(-300, Math.min(300, (difficulty - strength) / 400));
    return 1 / (1 + 10 ** exponent);
}

/** Earliest independent exposure wins; later practice must not turn an in-contest failure into AC. */
export function independentSamples(samples: ProblemSample[]): ProblemSample[] {
    const users = new Map<number, ProblemSample>();
    for (const sample of samples) {
        if (!Number.isSafeInteger(sample.uid) || sample.uid <= 1 || !Number.isFinite(sample.strength)
            || !Number.isFinite(sample.at) || !Number.isSafeInteger(sample.ratedCount) || sample.ratedCount < 0) continue;
        const previous = users.get(sample.uid);
        if (!previous || sample.at < previous.at || (sample.at === previous.at && sample.source === 'contest')) {
            users.set(sample.uid, sample);
        }
    }
    return [...users.values()];
}

export function estimateProblemRating(input: ProblemSample[]) {
    const samples = independentSamples(input);
    const result = {
        version: PROBLEM_RATING_VERSION, rating: null as number | null,
        samples: samples.length, solved: samples.filter((sample) => sample.solved).length,
        contestSamples: samples.filter((sample) => sample.source === 'contest').length,
        practiceSamples: samples.filter((sample) => sample.source === 'practice').length,
        contestIds: [...new Set(samples.filter((sample) => sample.source === 'contest' && sample.contestId).map((sample) => sample.contestId))],
        provisional: samples.length < 20, effectiveSamples: 0,
    };
    // Internal strength is on the site's mature Rating scale. New-user display bonuses are
    // deliberately not used; downweight provisional players until their strength stabilizes.
    const observations = samples.map((sample) => ({
        ...sample, weight: Math.max(0.25, Math.min(1, sample.ratedCount / 6)),
    }));
    const weight = observations.reduce((sum, sample) => sum + sample.weight, 0);
    result.effectiveSamples = Math.round(weight * 100) / 100;
    if (samples.length < PROBLEM_RATING_MIN_USERS || !weight) return result;
    const center = observations.reduce((sum, sample) => sum + sample.weight * sample.strength, 0) / weight;
    const solved = observations.reduce((sum, sample) => sum + (sample.solved ? sample.weight : 0), 0);
    // Two neutral pseudo-observations keep all-AC/all-failure and small samples finite.
    const target = solved + 1;
    let low = PROBLEM_RATING_MIN;
    let high = PROBLEM_RATING_MAX;
    for (let i = 0; i < 48; i++) {
        const mid = (low + high) / 2;
        const expected = observations.reduce((sum, sample) => sum + sample.weight * solveProbability(sample.strength, mid), 0)
            + 2 * solveProbability(center, mid);
        if (expected > target) low = mid;
        else high = mid;
    }
    result.rating = Math.max(PROBLEM_RATING_MIN, Math.min(PROBLEM_RATING_MAX, Math.round((low + high) / 200) * 100));
    return result;
}

export function problemRatingValue(problem: any): number | null {
    const value = problem?.difficulty;
    return problem?.problemRating?.version === PROBLEM_RATING_VERSION && Number.isInteger(value)
        && value >= PROBLEM_RATING_MIN && value <= PROBLEM_RATING_MAX ? value : null;
}

export function problemRatingColor(problem: any) {
    const value = problemRatingValue(problem);
    if (value === null || value < 1200) return 'gray';
    if (value < 1400) return 'green';
    if (value < 1600) return 'cyan';
    if (value < 1900) return 'blue';
    if (value < 2100) return 'purple';
    if (value < 2400) return 'orange';
    return 'red';
}

export function selectedProblemRating(query: any): string {
    return String(query || '').match(/(?:^|\s)difficulty:(\d+)(?=\s|$)/)?.[1] || '';
}
