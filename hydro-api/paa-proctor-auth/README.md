# Hydro 监考登录票据

可选 HydroOJ 插件，供独立监考服务使用。复制整个目录至 Hydro 插件目录后按实例的插件管理方式启用；主站基础部署无需此插件。

## 接口

- `POST /api/proctor/login-ticket`：服务端使用 HMAC 签名，按 Hydro 用户名、邮箱或已有 `studentId` 匹配账号，签发短时一次性登录 URL。
- `GET /proctor/consume-ticket?token=...`：消费票据，写入 Hydro 会话并跳转到比赛页面。

请求头 `x-proctor-timestamp` 为毫秒时间戳，允许与服务端相差最多 5 分钟。`x-proctor-signature` 为 `hmac_sha256(sharedSecret, timestamp + "." + JSON.stringify(body))` 的十六进制摘要。

```json
{
  "contestId": "hydroContestId",
  "account": "exampleCandidate",
  "redirect": "/contest/hydroContestId"
}
```

## 配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `sharedSecret` | 空 | 与监考服务使用同一独立随机密钥；为空时拒绝签发 |
| `ticketTtlSeconds` | `120` | 一次性登录票据的有效秒数 |
| `allowedRedirectPrefix` | `/contest/` | 只允许跳转到该路径前缀 |

`sharedSecret` 配置为空时读取 Hydro 进程的 `HYDRO_PROCTOR_SHARED_SECRET`。密钥具有代用户登录的权限，仅交给可信监考服务并通过 HTTPS 传输。票据只存在当前 Hydro 进程内存中，消费一次后删除；多进程部署需要保持请求落到同一进程。
