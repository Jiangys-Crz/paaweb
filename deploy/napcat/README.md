# 官网 QQ 机器人 NapCat 模板

此模板将 NapCat 加入官网已有的 `paaweb_app_network`，官网后台连接地址为 **`ws://napcat:3001`**。OneBot 端口不发布到宿主机；WebUI 仅监听宿主机 `127.0.0.1:6099`。先启动主站 Compose，确保该外部网络已经存在；网络名不同可设置 `PAAWEB_NETWORK`。

部署前执行 `cp .env.example .env`，在本目录的本机 `.env` 中设置强随机 `NAPCAT_WEBUI_TOKEN`，可通过 `NAPCAT_IMAGE` 覆盖默认固定镜像版本。`.env` 和 `state/` 已被忽略，不提交实际管理口令、QQ 登录状态或 OneBot Token。

首次启动前，部署人员应在 `state/config/onebot11.json` 预置正向 WebSocket 配置：`network.websocketServers` 中启用 `0.0.0.0:3001`，`messagePostFormat` 为 `array`，`token` 使用独立强随机值；该 Token 也填写到官网 `/admin/qq-bot` 的 OneBot Access Token。**WebUI 管理口令和 OneBot Access Token 分别配置。**默认运行配置不会作为源码提交。

```bash
cd deploy/napcat
docker compose -f compose.yml up -d
```

通过 SSH 转发访问管理页面：

```bash
ssh -L 6099:127.0.0.1:6099 <服务器用户>@<服务器地址>
```

浏览器打开 `http://127.0.0.1:6099/webui`，输入 WebUI 管理口令，再用机器人 QQ 在手机上扫码并手动确认登录。模板不设置 `ACCOUNT`，不保存 QQ 密码。登录后在官网后台保存 `ws://napcat:3001` 和独立 OneBot Token，检查连接与机器人身份。QQ 登录状态、配置和插件分别持久保存在 `state/qq`、`state/config`、`state/plugins`；更新镜像时保留这三个目录。

`MODE=ws` 与 `WEBUI_TOKEN` 只初始化缺失配置，已有配置应在 WebUI 中修改。本模板不使用特权模式或宿主机网络，并限制内存 768 MiB、CPU 0.75、共享内存 128 MiB 和轮转日志（每份 10 MiB，最多 3 份）。

变量与持久化路径依据 [NapCat-Docker 官方说明](https://github.com/NapNeko/NapCat-Docker/blob/main/README.md)，默认 OneBot 配置字段依据 [官方 WebSocket 模板](https://github.com/NapNeko/NapCat-Docker/blob/main/templates/templates/ws.json)。官网的查询群、推送、定时任务配置见 [QQ 机器人与 Hydro 联动](../../README.md#qq-机器人)。
