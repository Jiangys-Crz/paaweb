# 外榜同步工具

带图形界面的可选 Python 工具，定期从 HydroOJ 或兼容 XCPC API 的比赛读取题目、提交记录，再写入本系统的外部榜单。基础网站部署不依赖本工具。

## 环境和启动

需要 Python 3.10+、图形桌面及 Tkinter。Windows 和 macOS 的标准 Python 安装通常带有 Tkinter；Linux 可通过系统包管理器安装 `python3-tk`。

```bash
cd Board-Tools
bash run.sh
```

Windows 双击 `run.bat`，macOS 也可双击 `start.command`。启动脚本在本目录创建 `venv`、安装依赖后打开界面。也可手动执行：

```bash
python3 -m venv venv
source venv/bin/activate
python3 -m pip install -r requirements.txt
python3 board_spider.py
```

## 配置

1. 在主站管理员后台创建外部榜单，记录外榜 ID 与独立写入 API Key。
2. 选择源系统并填写其根地址、比赛 ID。Hydro 需要先安装本仓库 `hydro-api/contest-api` 插件，并填写有比赛管理权限账号的 `sid`；兼容 XCPC API 的服务填写其 API Key。
3. 将目标地址填写为主站地址（例如 `http://localhost:3403`）；可填写以 `/api` 结尾的 API 根地址，也支持直接连接后端。
4. 填写目标外榜 ID、写入 API Key 和轮询间隔，点击“开始同步”；结束后点击“停止同步”。

Hydro 源调用 `/api/contest/{比赛ID}/problems`、`/submissions`，XCPC 源调用 `/api/contests/{比赛ID}/problems`、`/submissions`。仅 API 路径相同的服务可直接使用 XCPC 适配器。

凭证仅保存在当前程序内存，不写入配置文件；请使用 HTTPS 连接远程实例。源接口可能包含参赛者用户名和代码，只同步你有权限管理和公开的赛事数据。工具不会自带真实榜单数据、比赛 ID 或账号凭证。
