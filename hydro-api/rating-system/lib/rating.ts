export const RATING_VERSION = 'cf_like_v1_4';
export const INITIAL_INTERNAL_RATING = 1400;
export const INITIAL_RATING = 0;
export const INITIAL_RATED_COUNT = 0;
export const NEWBIE_BONUSES = [500, 350, 250, 150, 100, 50];

export interface RatingState {
    internalRating: number;
    rating: number;
    ratedCount: number;
    maxRating: number;
}

export interface RatedInput {
    uid: number;
    internalRating: number;
    rating: number;
    ratedCount: number;
    maxRating: number;
    rank: number;
}

export interface RatingResult extends RatedInput {
    expectedRank: number;
    targetRank: number;
    targetInternalRating: number;
    rawDelta: number;
    firstCorrection: number;
    secondCorrection: number;
    delta: number;
    bonus: number;
    nextInternalRating: number;
    nextRating: number;
    ratingDelta: number;
    nextRatedCount: number;
    nextMaxRating: number;
}

export function initialState(): RatingState {
    return {
        internalRating: INITIAL_INTERNAL_RATING,
        rating: INITIAL_RATING,
        ratedCount: INITIAL_RATED_COUNT,
        maxRating: INITIAL_RATING,
    };
}

export function withheldDisplayPoints(ratedCount: number): number {
    return INITIAL_INTERNAL_RATING - NEWBIE_BONUSES
        .slice(0, Math.min(Math.max(0, ratedCount), NEWBIE_BONUSES.length))
        .reduce((sum, value) => sum + value, 0);
}

export function displayRating(internalRating: number, ratedCount: number): number {
    return Math.max(0, Math.trunc(internalRating - withheldDisplayPoints(ratedCount)));
}

function probability(a: number, b: number): number {
    const exponent = (b - a) / 400;
    if (exponent > 300) return 0;
    if (exponent < -300) return 1;
    return 1 / (1 + 10 ** exponent);
}

function expectedRank(users: RatedInput[], index: number): number {
    const own = users[index].internalRating;
    let result = 1;
    for (let j = 0; j < users.length; j++) {
        if (j !== index) result += probability(users[j].internalRating, own);
    }
    return result;
}

function targetInternalRating(users: RatedInput[], index: number, targetRank: number): number {
    const own = users[index].internalRating;
    const f = (x: number) => {
        let result = 1;
        for (let j = 0; j < users.length; j++) {
            if (j !== index) result += probability(users[j].internalRating, x);
        }
        return result;
    };
    let left = own - 400;
    let right = own + 400;
    let guard = 0;
    while (f(left) < targetRank && guard++ < 1000) left -= Math.max(400, Math.abs(left) + 400);
    guard = 0;
    while (f(right) >= targetRank && guard++ < 1000) right += Math.max(400, Math.abs(right) + 400);
    if (!(left < right) || !Number.isFinite(left) || !Number.isFinite(right)) {
        throw new Error('Unable to establish Rating binary search bounds');
    }
    while (right - left > 1) {
        const mid = Math.floor((left + right) / 2);
        if (f(mid) >= targetRank) left = mid;
        else right = mid;
    }
    if (!(f(left) >= targetRank) || !(f(left + 1) < targetRank)) {
        throw new Error('Rating binary search invariant failed');
    }
    return left;
}

function trunc(value: number): number {
    return value < 0 ? Math.ceil(value) : Math.floor(value);
}

export function calculate(users: RatedInput[]): RatingResult[] {
    if (users.length < 2) throw new Error('At least two users are required');
    for (const user of users) {
        if (![user.internalRating, user.rating, user.ratedCount, user.maxRating].every(Number.isSafeInteger)
            || user.ratedCount < 0 || user.rating < 0 || user.maxRating < user.rating
            || user.rating !== displayRating(user.internalRating, user.ratedCount)) {
            throw new Error(`Invalid Rating state for user ${user.uid}`);
        }
    }
    const expected = users.map((_, index) => expectedRank(users, index));
    const targets = users.map((user, index) => Math.sqrt(expected[index] * user.rank));
    if (expected.some((value) => !(value > 1) || !(value < users.length))
        || targets.some((value) => !(value > 1) || !(value < users.length))) {
        throw new Error('Rating calculation produced an invalid rank');
    }
    const targetInternal = users.map((_, index) => targetInternalRating(users, index, targets[index]));
    const raw = users.map((user, index) => trunc((targetInternal[index] - user.internalRating) / 2));
    const sum = raw.reduce((a, b) => a + b, 0);
    const c1 = -trunc(sum / users.length) - 1;
    const first = raw.map((value) => value + c1);
    if (first.reduce((a, b) => a + b, 0) >= 0) throw new Error('First Rating correction is not negative');
    const referenceCount = Math.min(users.length, Math.floor(4 * Math.sqrt(users.length)));
    const ordered = users.map((user, index) => ({ user, index })).sort((a, b) => b.user.internalRating - a.user.internalRating);
    const threshold = ordered[referenceCount - 1].user.internalRating;
    const group = ordered.filter((item) => item.user.internalRating >= threshold);
    const groupSum = group.reduce((sumValue, item) => sumValue + first[item.index], 0);
    const c2 = Math.min(0, Math.max(-10, -trunc(groupSum / group.length)));
    const output = users.map((user, index) => {
        const delta = first[index] + c2;
        const nextInternalRating = user.internalRating + delta;
        const nextRatedCount = user.ratedCount + 1;
        const bonus = NEWBIE_BONUSES[nextRatedCount - 1] || 0;
        const nextRating = displayRating(nextInternalRating, nextRatedCount);
        return {
            ...user,
            expectedRank: expected[index],
            targetRank: targets[index],
            targetInternalRating: targetInternal[index],
            rawDelta: raw[index],
            firstCorrection: c1,
            secondCorrection: c2,
            delta,
            bonus,
            nextInternalRating,
            nextRating,
            ratingDelta: nextRating - user.rating,
            nextRatedCount,
            nextMaxRating: Math.max(user.maxRating, nextRating),
        };
    });
    if (output.reduce((a, b) => a + b.delta, 0) >= 0) throw new Error('Final Rating delta sum is not negative');
    for (const item of output) {
        if (!Number.isFinite(item.rank) || !Number.isInteger(item.rank * 2)) {
            throw new TypeError('Invalid tied Rating rank');
        }
        for (const key of ['internalRating', 'rating', 'ratedCount', 'maxRating',
            'targetInternalRating', 'rawDelta', 'firstCorrection', 'secondCorrection', 'delta',
            'nextInternalRating', 'nextRating', 'nextRatedCount', 'nextMaxRating']) {
            if (!Number.isSafeInteger(item[key])) throw new Error(`Unsafe Rating integer: ${key}`);
        }
    }
    return output;
}
