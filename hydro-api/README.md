# HydroOJ 可选集成

这些插件由独立 HydroOJ 实例加载，主站 Docker 部署无需安装。仓库不包含 Hydro 数据库、OJ 账号、测试数据、登录 Cookie 或集成密钥。

## 插件目录

| 目录 | 用途 | 默认状态及必要配置 |
| --- | --- | --- |
| `rating-system/` | 比赛 Rating、题目难度估计、榜单和 QQ 机器人只读 API | 机器人 API 默认关闭；启用后设置独立 `botApiKey` |
| `paa-external-login/` | 用主站用户名/邮箱和密码、邮箱验证码登录 Hydro | `appKey` 与 `endpoint` 默认为空，需与主站外部应用配置对应 |
| `contest-api/` | 将有权限访问的比赛题目及提交记录提供给外榜同步工具 | 通过 Hydro 登录会话验证比赛管理权限 |
| `paa-proctor-auth/` | 为外部监考服务签发一次性登录票据 | `sharedSecret` 默认为空，未配置时拒绝签发 |
| `disable-scoreboard/` | 可选的比赛成绩表访问限制 | 仅允许 Hydro 超级管理员访问比赛成绩表；见下方说明 |
| `algospark-hydro-bridge-v0.2.0/` | AlgoSpark 系统与 Hydro 的题目、提交和结果桥接 | 独立可选集成，配置见目录内 README |

`rating-system/` 的模板和静态资源需要完整复制。图标许可保存在 `rating-system/public/ant-design-icons-LICENSE.txt`，请随发布保留。AlgoSpark 桥接保留原有包标识、MIT 许可声明和集成说明。`rating-system` 与 `paa-external-login` 因包含 Hydro 衍生模板而独立适用 AGPL-3.0-only；它们的完整 LICENSE 与 NOTICE 随各插件保存，见 [第三方说明](../THIRD_PARTY_NOTICES.md)。

## 安装

在独立 Hydro 环境中确认插件兼容版本，将需要的整个目录复制到该实例实际使用的插件目录，并按该实例的插件管理方式加载、配置和重启。各目录内的 `package.json` 定义入口和 Hydro 依赖，主站根目录无需安装 Hydro 依赖。

```bash
# 示例：将实际启用目录替换为自己的 Hydro 插件目录。
rsync -av --exclude='node_modules/' hydro-api/rating-system/ /path/to/hydro/plugins/rating-system/
```

更新前备份原插件目录及数据库；更新时同步完整目录。回滚时恢复同一版本的完整目录并重启 Hydro。实例配置和真实密钥应只保存在运行环境中。

## 主站外部登录

在主站管理员后台创建外部应用，再在 Hydro 的 `paa-external-login` 插件配置中填写：

```yaml
appKey: "由主站外部应用管理生成的 App Key"
endpoint: "https://www.example.com/api"
disableBuiltinRegister: true
```

也可通过 Hydro 进程环境变量 `PAA_EXTERNAL_APP_KEY` 与 `PAA_EXTERNAL_API_ENDPOINT` 配置；非空插件配置优先。`endpoint` 是主站 API 根地址，插件调用 `/user/external/login` 和 `/user/external/send-code`。使用邮箱验证码前，需要先在主站配置邮件服务。

首次外部登录会按邮箱匹配 Hydro 用户，并为新用户创建随机本地密码；密码不会从主站同步。Hydro 本地管理员仍可通过本地用户名/邮箱和本地密码登录。`disableBuiltinRegister: true` 会关闭 Hydro 自带注册与找回密码页面，请在外部登录完成配置并验证后启用。

## QQ 机器人 API

在 Hydro 的 `rating-system` 配置中启用 `botApiEnabled`，生成至少 32 个随机字符的独立 `botApiKey`，并在主站 `/admin/qq-bot` 填入相同密钥及 Hydro 地址。`botApiSiteUrl` 用于生成比赛链接，留空时采用 Hydro 的 `server.url`。详细接口、权限与限流规则见 [Rating 插件说明](rating-system/README.md)。

```yaml
botApiEnabled: true
botApiSiteUrl: "https://oj.example.com"
botApiKey: "填写独立生成的随机密钥"
```

请求只通过 `X-API-Key` 请求头传递密钥。接口默认关闭时返回 `503`，密钥错误返回 `401`。主站 QQ 机器人配置见 [根目录 README](../README.md#qq-机器人)。新部署所需数据结构已包含在初始化 SQL 中。

## 成绩表限制插件

`disable-scoreboard` 是限制严格的可选策略：除超级管理员外，用户不能访问比赛成绩表（作业不受影响）。它还会在检测到比赛结束时清除全部 Hydro 登录会话及登录 IP 绑定。若仅需要常规成绩表和 Rating，请使用 `rating-system`，无需加载本插件；根据自己的赛事规则明确选择后再部署。

## 验证

在已安装 Hydro 开发依赖的工作区中运行：

```bash
node -r @hydrooj/register --test plugins/rating-system/tests/*.spec.ts
```

测试通过临时 MongoDB 验证结算、榜单、查询、机器人认证和限流，使用合成记录，不访问实例配置的正式数据库；首次运行可能下载测试用 MongoDB。外部系统间的实际连通性仍需在各自部署环境中验证。
