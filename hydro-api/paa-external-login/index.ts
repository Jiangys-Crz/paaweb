import {
    BlacklistedError, BlackListModel, Context, DomainModel, ForbiddenError, Handler,
    OplogModel, PERM, PRIV, Schema, Service, superagent,
    UserModel, ValidationError, randomstring,
} from 'hydrooj';

const PROVIDER_ID = 'paa';
const APP_KEY = '';
const DEFAULT_ENDPOINT = '';
let runtimeConfig = {
    appKey: APP_KEY,
    endpoint: DEFAULT_ENDPOINT,
};

interface ExternalUserInfo {
    userId: number | string;
    userAccount?: string;
    userName?: string;
    realName?: string;
    numberId?: string;
    email: string;
}

interface ExternalLoginResponse {
    code: number;
    msg: string;
    data: ExternalUserInfo | null;
}

interface ExternalSendCodeResponse {
    code: number;
    msg: string;
    data: string | null;
}

async function successfulAuth(this: Handler, udoc: any, rememberme = false) {
    if (!udoc.hasPriv(PRIV.PRIV_USER_PROFILE)) throw new BlacklistedError(udoc.uname, udoc.banReason);
    await UserModel.setById(udoc._id, { loginat: new Date(), loginip: this.request.ip });
    this.context.HydroContext.user = udoc;
    this.user = udoc;
    this.session.viewLang = '';
    this.session.uid = udoc._id;
    this.session.sudo = null;
    this.session.sudoUid = null;
    this.session.scope = PERM.PERM_ALL.toString();
    this.session.oauthBind = null;
    this.session.recreate = true;
    this.session.save = rememberme;
}

async function findLocalUser(domainId: string, account: string) {
    let udoc = await UserModel.getByEmail(domainId, account);
    udoc ||= await UserModel.getByUname(domainId, account);
    return udoc;
}

async function ensureUser(this: Handler, external: ExternalUserInfo) {
    const mail = external.email?.trim();
    if (!mail) throw new ValidationError('email');

    let udoc = await UserModel.getByEmail('system', mail);
    if (!udoc) {
        const mailDomain = mail.split('@')[1];
        if (await BlackListModel.get(`mail::${mailDomain}`)) throw new ForbiddenError('Email domain is blacklisted');
        // Hydro's uname is the login identifier. Never use the website's
        // display name here: it is not guaranteed to be unique and cannot be
        // used for the requested username/email-only login flow.
        const username = external.userAccount?.trim();
        if (!username) throw new Error('网站账号信息缺失，无法创建 Hydro 用户');
        const uid = await UserModel.create(
            mail,
            username,
            randomstring(32),
            undefined,
            this.request.ip,
        );
        udoc = await UserModel.getById('system', uid);
    }

    const $set: any = {};
    if (external.numberId) $set.studentId = external.numberId;
    if (Object.keys($set).length) await UserModel.setById(udoc._id, $set);

    const setInDomain: any = { join: true };
    if (external.realName) setInDomain.displayName = external.realName;
    await DomainModel.setUserInDomain(this.args.domainId || 'system', udoc._id, setInDomain);

    await this.ctx.oauth.set(PROVIDER_ID, String(external.userId), udoc._id);
    return await UserModel.getById(this.args.domainId || 'system', udoc._id);
}

function isEmail(value: string) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isUsername(value: string) {
    return /^(?=.*[A-Za-z])[A-Za-z0-9]{3,20}$/.test(value);
}

function assertLoginIdentifier(account: string) {
    if (!isEmail(account) && !isUsername(account)) {
        throw new Error('只能使用网站官网用户名或邮箱登录');
    }
}

function readRememberMe(value: unknown) {
    return value === true || value === 1 || value === '1' || value === 'on' || value === 'true';
}

function assertAppKey(appKey: string) {
    if (!appKey) {
        throw new Error('请先在 Hydro 插件配置中设置外部登录 App Key');
    }
}

async function externalLogin(endpoint: string, appKey: string, loginType: 'password' | 'code', account: string, credential: string) {
    assertAppKey(appKey);
    if (!/^https?:\/\//i.test(endpoint)) throw new Error('请先配置外部登录 API 地址');
    const res = await superagent.post(`${endpoint.replace(/\/$/, '')}/user/external/login`)
        .set('Content-Type', 'application/json')
        .send({
            appKey,
            loginType,
            account,
            credential,
        })
        .catch((e) => {
            const body = (e as any)?.response?.body as ExternalLoginResponse;
            if (body?.msg) throw new Error(body.msg);
            throw e;
        });
    const body = res.body as ExternalLoginResponse;
    if (Number(body?.code) !== 200 || !body.data) throw new Error(body?.msg || '外部登录失败');
    return body.data;
}

async function externalSendCode(endpoint: string, appKey: string, email: string) {
    assertAppKey(appKey);
    if (!/^https?:\/\//i.test(endpoint)) throw new Error('请先配置外部登录 API 地址');
    const res = await superagent.post(`${endpoint.replace(/\/$/, '')}/user/external/send-code`)
        .set('Content-Type', 'application/json')
        .send({
            appKey,
            email,
        })
        .catch((e) => {
            const body = (e as any)?.response?.body as ExternalSendCodeResponse;
            if (body?.msg) throw new Error(body.msg);
            throw e;
        });
    const body = res.body as ExternalSendCodeResponse;
    if (Number(body?.code) !== 200) throw new Error(body?.msg || '验证码发送失败');
    return body?.msg || body?.data || '验证码已发送';
}

async function performPasswordLogin(this: Handler, account: string, password: string, rememberme: boolean, redirect: string) {
    if (!account) throw new Error('请输入用户名或邮箱');
    assertLoginIdentifier(account);
    if (!password) throw new Error('请输入密码');
    await Promise.all([
        this.limitRate('user_login', 60, 30),
        this.limitRate('user_login_id', 60, 5, account),
        OplogModel.log(this, 'user.login.external', { provider: PROVIDER_ID, redirect }),
    ]);
    const localUser = await findLocalUser(this.args.domainId || 'system', account);
    if (localUser) {
        try {
            await localUser.checkPassword(password);
        } catch (e) {
            // External users may already have a local Hydro account with a random password.
            const info = await externalLogin(runtimeConfig.endpoint, runtimeConfig.appKey, 'password', account, password);
            const udoc = await ensureUser.call(this, info);
            await successfulAuth.call(this, udoc, rememberme);
            this.response.redirect = redirect || '/';
            return;
        }
        await successfulAuth.call(this, localUser, rememberme);
        this.response.redirect = redirect || '/';
        return;
    }
    const info = await externalLogin(runtimeConfig.endpoint, runtimeConfig.appKey, 'password', account, password);
    const udoc = await ensureUser.call(this, info);
    await successfulAuth.call(this, udoc, rememberme);
    this.response.redirect = redirect || '/';
}

async function performCodeLogin(this: Handler, email: string, code: string, rememberme: boolean, redirect: string) {
    if (!email) throw new Error('请输入邮箱地址');
    if (!code) throw new Error('请输入邮箱验证码');
    await Promise.all([
        this.limitRate('user_login', 60, 30),
        this.limitRate('user_login_id', 60, 5, email),
        OplogModel.log(this, 'user.login.external_code', { provider: PROVIDER_ID, redirect }),
    ]);
    const info = await externalLogin(runtimeConfig.endpoint, runtimeConfig.appKey, 'code', email, code);
    const udoc = await ensureUser.call(this, info);
    await successfulAuth.call(this, udoc, rememberme);
    this.response.redirect = redirect || '/';
}

class ExternalSendCodeHandler extends Handler {
    noCheckPermView = true;

    async post({ domainId }) {
        const email = `${this.request.body.email || ''}`.trim();
        try {
            if (!email) throw new Error('请输入邮箱地址');
            await this.limitRate('send_mail', 60, 1, email);
            const msg = await externalSendCode(runtimeConfig.endpoint, runtimeConfig.appKey, email);
            this.response.body = { ok: true, msg };
        } catch (e) {
            this.response.status = 400;
            this.response.body = {
                ok: false,
                msg: e instanceof Error ? e.message : '验证码发送失败',
            };
        }
        void domainId;
    }
}

export default class PaaExternalLoginService extends Service {
    static inject = ['oauth', 'server'];
    static Config = Schema.object({
        appKey: Schema.string().description('PAA website external login App Key').role('secret').default(APP_KEY),
        endpoint: Schema.string().description('PAA website API base URL, e.g. https://www.example.com/api').default(DEFAULT_ENDPOINT),
        disableBuiltinRegister: Schema.boolean().description('Disable Hydro builtin registration and password reset pages').default(true),
    });

    config: ReturnType<typeof PaaExternalLoginService.Config>;

    constructor(ctx: Context, config: ReturnType<typeof PaaExternalLoginService.Config>) {
        super(ctx, 'oauth.paa');
        this.config = config;
        runtimeConfig = {
            appKey: config.appKey || process.env.PAA_EXTERNAL_APP_KEY || '',
            endpoint: config.endpoint || process.env.PAA_EXTERNAL_API_ENDPOINT || '',
        };

        ctx.Route('paa_send_code', '/paa/send-code', ExternalSendCodeHandler);
        ctx.on('handler/before/UserLogin#post', async (handler) => {
            const loginType = handler.request.body.loginType === 'code' ? 'code' : 'password';
            const rememberme = readRememberMe(handler.request.body.rememberme);
            const redirect = `${handler.request.body.redirect || ''}`;
            try {
                if (loginType === 'code') {
                    const email = `${handler.request.body.email || ''}`.trim();
                    const code = `${handler.request.body.code || ''}`.trim();
                    await performCodeLogin.call(handler, email, code, rememberme, redirect);
                } else {
                    const account = `${handler.request.body.uname || ''}`.trim();
                    const password = `${handler.request.body.password || ''}`;
                    await performPasswordLogin.call(handler, account, password, rememberme, redirect);
                }
            } catch (e) {
                const message = e instanceof Error ? e.message : '外部登录失败';
                handler.response.template = 'user_login.html';
                handler.response.status = 403;
                handler.response.body = {
                    loginError: message,
                    loginType,
                    account: handler.request.body.uname || '',
                    email: handler.request.body.email || '',
                    rememberme,
                    redirect,
                };
            }
            return 'cleanup';
        });

        if (config.disableBuiltinRegister) {
            const disabled = () => {
                throw new ForbiddenError('Builtin registration is disabled. Please use external login.');
            };
            ctx.on('handler/before-prepare/UserRegister', disabled);
            ctx.on('handler/before-prepare/UserRegisterWithCode', disabled);
            ctx.on('handler/before-prepare/UserLostPass', disabled);
            ctx.on('handler/create/http', (handler) => {
                if (!handler.user?.hasPriv?.(PRIV.PRIV_USER_PROFILE)) {
                    const user = Object.create(Object.getPrototypeOf(handler.user));
                    Object.assign(user, handler.user, { priv: handler.user.priv & ~PRIV.PRIV_REGISTER_USER });
                    handler.user = user;
                    handler.context.HydroContext.user = user;
                }
            });
            ctx.i18n.load('zh', {
                'Builtin registration is disabled. Please use external login.': '原有注册方式已禁用，请使用外部账号登录。',
            });
        }
    }
}
