import crypto from 'node:crypto';
import {
    Context, Handler, PRIV, Schema, Service, UserModel,
    BadRequestError, ForbiddenError, NotFoundError,
} from 'hydrooj';

const DEFAULT_SECRET = '';

interface TicketRecord {
    uid: number;
    redirect: string;
    expiresAt: number;
}

const tickets = new Map<string, TicketRecord>();

let runtimeConfig = {
    sharedSecret: DEFAULT_SECRET,
    ticketTtlSeconds: 120,
    allowedRedirectPrefix: '/contest/',
};

function timingSafeEqual(left: string, right: string) {
    const leftBuffer = Buffer.from(left);
    const rightBuffer = Buffer.from(right);
    if (leftBuffer.length !== rightBuffer.length) return false;
    return crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function signatureFor(secret: string, timestamp: string, bodyText: string) {
    return crypto.createHmac('sha256', secret).update(`${timestamp}.${bodyText}`).digest('hex');
}

function assertConfigured() {
    if (!runtimeConfig.sharedSecret || runtimeConfig.sharedSecret === DEFAULT_SECRET) {
        throw new ForbiddenError('Hydro proctor shared secret is not configured.');
    }
}

function consumeToken(token: string) {
    const record = tickets.get(token);
    if (!record) return null;
    tickets.delete(token);
    if (record.expiresAt < Date.now()) return null;
    return record;
}

async function findUserByAccount(domainId: string, account: string) {
    let udoc = await UserModel.getByUname(domainId, account);
    udoc ||= await UserModel.getByEmail(domainId, account);
    if (!udoc) {
        const raw = await UserModel.getMulti({ studentId: account }).limit(1).toArray();
        if (raw[0]) udoc = await UserModel.getById(domainId, raw[0]._id);
    }
    return udoc;
}

async function loginAs(handler: Handler, uid: number) {
    const udoc = await UserModel.getById(handler.args.domainId || 'system', uid);
    if (!udoc) throw new NotFoundError('user');
    await UserModel.setById(udoc._id, { loginat: new Date(), loginip: handler.request.ip });
    handler.context.HydroContext.user = udoc;
    handler.user = udoc;
    handler.session.viewLang = '';
    handler.session.uid = udoc._id;
    handler.session.sudo = null;
    handler.session.sudoUid = null;
    handler.session.scope = '0';
    handler.session.oauthBind = null;
    handler.session.recreate = true;
    handler.session.save = false;
}

class ProctorLoginTicketHandler extends Handler {
    noCheckPermView = true;

    async post({ domainId }) {
        assertConfigured();
        const timestamp = `${this.request.headers['x-proctor-timestamp'] || ''}`;
        const signature = `${this.request.headers['x-proctor-signature'] || ''}`;
        const now = Date.now();
        const time = Number(timestamp);
        if (!timestamp || !signature || !Number.isFinite(time) || Math.abs(now - time) > 5 * 60 * 1000) {
            throw new ForbiddenError('invalid proctor signature timestamp');
        }

        const body = this.request.body || {};
        const bodyText = JSON.stringify(body);
        const expected = signatureFor(runtimeConfig.sharedSecret, timestamp, bodyText);
        if (!timingSafeEqual(signature, expected)) throw new ForbiddenError('invalid proctor signature');

        const account = `${body.account || ''}`.trim();
        const redirect = `${body.redirect || `/contest/${body.contestId || ''}`}`.trim();
        if (!account) throw new BadRequestError('account');
        if (!redirect.startsWith(runtimeConfig.allowedRedirectPrefix)) throw new BadRequestError('redirect');

        const udoc = await findUserByAccount(domainId || 'system', account);
        if (!udoc) throw new NotFoundError('user');

        const token = crypto.randomBytes(32).toString('base64url');
        tickets.set(token, {
            uid: udoc._id,
            redirect,
            expiresAt: Date.now() + runtimeConfig.ticketTtlSeconds * 1000,
        });
        this.response.body = {
            ok: true,
            loginUrl: `/proctor/consume-ticket?token=${encodeURIComponent(token)}`,
            expiresAt: new Date(Date.now() + runtimeConfig.ticketTtlSeconds * 1000).toISOString(),
        };
    }
}

class ProctorConsumeTicketHandler extends Handler {
    noCheckPermView = true;

    async get() {
        const token = `${this.request.query.token || ''}`;
        const record = consumeToken(token);
        if (!record) throw new ForbiddenError('ticket expired or consumed');
        await loginAs(this, record.uid);
        this.response.redirect = record.redirect;
    }
}

export default class PaaProctorAuthService extends Service {
    static Config = Schema.object({
        sharedSecret: Schema.string().description('Shared secret used by the proctoring server').role('secret').default(DEFAULT_SECRET),
        ticketTtlSeconds: Schema.number().description('One-time login ticket TTL').default(120),
        allowedRedirectPrefix: Schema.string().description('Allowed redirect path prefix').default('/contest/'),
    });

    constructor(ctx: Context, config: ReturnType<typeof PaaProctorAuthService.Config>) {
        super(ctx, 'paa.proctorAuth');
        runtimeConfig = {
            sharedSecret: config.sharedSecret || process.env.HYDRO_PROCTOR_SHARED_SECRET || '',
            ticketTtlSeconds: config.ticketTtlSeconds,
            allowedRedirectPrefix: config.allowedRedirectPrefix,
        };
        ctx.Route('paa_proctor_login_ticket', '/api/proctor/login-ticket', ProctorLoginTicketHandler);
        ctx.Route('paa_proctor_consume_ticket', '/proctor/consume-ticket', ProctorConsumeTicketHandler, PRIV.PRIV_NONE);
    }
}
