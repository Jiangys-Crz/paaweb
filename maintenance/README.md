# 可选维护页

`index.html` 是自包含维护页，使用中性标识且不请求外部资源。独立反向代理可在计划维护或网站网关不可用时返回此页。默认 Docker 部署不依赖 Caddy；已有 Nginx、CDN 或其他代理时，也可按其维护页机制使用该文件。

## 构建页面

编辑 `index.template.html` 中的网站名称、文案和联系方式后执行：

```bash
bash maintenance/build.sh
```

默认仅依赖 Python 3，内嵌 PAA SVG 标识。如需自定义 PNG/JPEG 等图片，安装 Pillow 并传入图片路径；图片会在构建时内联，不依赖服务器上的原始图片文件：

```bash
python3 -m pip install Pillow
bash maintenance/build.sh /path/to/site-logo.png
```

生成后可直接在浏览器打开 `maintenance/index.html` 预览。`index.html` 和模板应一起提交。

## Caddy 示例

参考 `Caddyfile.snippet`，将示例域名 `example.com` 改为自己的域名，将 `root * /srv/paaweb/maintenance` 改为实际维护页目录。`reverse_proxy` 指向主站对宿主机暴露的网关端口，示例为 `127.0.0.1:3403`。Caddy 进程需要能进入路径上各级目录并读取 `index.html`。

把片段合并到自己的 Caddyfile，避免为同一域名重复定义站点块；检查配置后再加载：

```bash
caddy fmt --overwrite /path/to/Caddyfile
caddy validate --config /path/to/Caddyfile
caddy reload --config /path/to/Caddyfile
```

示例将所有子路由转发到网站；出现 502/503/504 时用维护页替代响应正文，保留故障状态码。该片段不负责其他独立服务或 OJ 的代理规则。

## 计划维护开关

完成代理配置后，在项目根目录运行：

```bash
bash maintenance/maintenance-mode.sh on
bash maintenance/maintenance-mode.sh status
# 完成数据库备份、更新与检查后恢复访问
bash maintenance/maintenance-mode.sh off
```

`on` 创建 `maintenance/maintenance.on` 文件，所有请求立即显示维护页并返回 200；`off` 删除开关文件，恢复转发，无需重新加载 Caddy。开关已被 Git 忽略。将脚本放在其他目录时可通过 `MAINT_DIR` 指定维护页目录。

部署后使用自己的域名检查首页、登录页等子路由，并确认关闭维护模式且服务正常时能够恢复访问。若持续显示维护页，检查开关文件、网关端口和容器状态；若页面本身无法读取，检查目录权限与代理日志。
