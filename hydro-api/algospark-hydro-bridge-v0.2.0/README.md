# AlgoSpark Hydro Bridge

插件版本 `0.2.0`，适配 Hydro `5.0.0-beta.17` 至 `5.x`。插件让 AlgoSpark 网站以已绑定学生的 Hydro 身份创建真实评测记录，并在判题完成后把结果回传给网站。

## 安装

1. 解压 ZIP 到 Hydro 服务器上的固定目录，例如 `/opt/algospark-hydro-bridge`。
2. 在 Hydro 运行账号下执行：

   ```bash
   hydrooj addon add /opt/algospark-hydro-bridge
   pm2 restart hydrooj
   ```

3. 进入 Hydro 后台的系统设置，搜索 `AlgoSpark`，填写：

   - `启用 AlgoSpark 判题桥`：开启
   - `AlgoSpark 网站地址`：公网 HTTPS 地址，不要以 `/` 结尾
   - `AlgoSpark 共享密钥`：与网站 `.env` 的 `HYDRO_WEBHOOK_SECRET` 完全一致

   也可以通过 Hydro 进程环境变量 `ALGOSPARK_SITE_URL`、`ALGOSPARK_SECRET` 和 `ALGOSPARK_ENABLED` 配置；后台设置更直观，二者同时存在时环境变量优先。

4. 访问 `https://OJ域名/d/域ID/algospark/health`，确认 `ok` 和 `configured` 都为 `true`。

## 网站配置

```env
HYDRO_BASE_URL=https://oj.example.edu.cn/d/training
HYDRO_BRIDGE_URL=https://oj.example.edu.cn/d/training/algospark
HYDRO_WEBHOOK_SECRET=至少32位的随机字符串
HYDRO_DEMO_MODE=false
```

网站与 Hydro 必须都使用 HTTPS。不要把共享密钥发给学生，也不要写进前端 JavaScript。

## 同步一道题到 AlgoSpark

题目需先在 Hydro 中创建并配好测试数据。然后由网站服务器或管理员工具调用：

```bash
curl -X POST "https://oj.example.edu.cn/d/training/algospark/sync-problem" \
  -H "Authorization: Bearer 你的共享密钥" \
  -H "Content-Type: application/json" \
  -d '{"problemId":"P1001","points":20,"track":"grammar","active":true}'
```

`track` 可填 `grammar` 或 `algorithm`。同步成功后，题目会出现在 AlgoSpark 对应题单中。

## 工作流程

1. 学生在 AlgoSpark 生成 6 位绑定码。
2. 学生登录 Hydro 后打开 AlgoSpark 给出的绑定链接，确认绑定。
3. 学生在 AlgoSpark 编辑代码并点击提交。
4. 插件以学生自己的 Hydro UID 创建评测记录。
5. Hydro 判题完成，插件把 AC/WA、得分、耗时和内存回传；AlgoSpark 只在首次 AC 时发放积分。
