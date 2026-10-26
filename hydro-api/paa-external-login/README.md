# PAA 主站外部登录

为独立 HydroOJ 实例提供主站用户名/邮箱密码登录和邮箱验证码登录。安装及完整配置见 [Hydro 可选集成](../README.md#主站外部登录)。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `appKey` | 空 | 主站管理员后台生成的外部应用 App Key |
| `endpoint` | 空 | 主站 API 根地址，例如 `https://www.example.com/api` |
| `disableBuiltinRegister` | `true` | 禁用 Hydro 内建注册与密码找回，统一由主站管理 |

当插件配置为空时，分别读取 Hydro 进程环境变量 `PAA_EXTERNAL_APP_KEY` 和 `PAA_EXTERNAL_API_ENDPOINT`。真实值应保存在运行环境或 Hydro 配置中。

插件按邮箱匹配账号；新用户使用主站用户名创建 Hydro 本地账号，以随机本地密码初始化，并建立外部用户关联。已有 Hydro 本地用户仍可使用本地密码登录。邮箱验证码依赖主站邮件服务。首次使用前，先配置并验证主站外部应用、邮件和站点 HTTPS。

## 许可证

本插件包含 Hydro 衍生登录模板，按 AGPL-3.0-only 分发，见 [LICENSE](LICENSE) 和 [来源与修改说明](NOTICE.md)。
