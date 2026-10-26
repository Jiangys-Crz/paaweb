# 部署与回归脚本

从项目根目录运行；实际密钥和本机 `.env` 不应提交到 Git。完整部署流程见根目录 README。

| 脚本 | 用途 | 依赖 |
| --- | --- | --- |
| `init-env.sh` | 从示例初始化本机环境配置和独立随机密钥 | 按根目录 README 使用 |
| `reset-admin-password.sh` | 交互式重置超级管理员密码并撤销旧会话，不依赖邮箱 | Docker Compose、OpenSSL |
| `check-public-release.py` | 检查开源发布目录中禁止提交的文件与已知私人标识 | Python 3 |
| `test-qq-bot.sh` | 在临时 PostgreSQL 中运行 QQ 机器人相关后端测试 | Java 21、Maven、Docker |

`test-qq-bot.sh` 创建临时数据库容器，退出时自动清理，不连接已有应用数据库。脚本中的固定口令只用于本机临时测试容器：

```bash
bash scripts/test-qq-bot.sh
```

初始化脚本与发布检查脚本的可用参数以脚本自身及根目录 README 为准。新部署直接使用完整初始化 SQL，不需要执行旧版本的逐次迁移或内部审计脚本。
