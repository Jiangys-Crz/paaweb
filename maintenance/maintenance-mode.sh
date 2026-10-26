#!/usr/bin/env bash
# 网站维护模式开关（维护页就在本文件夹里，直接运行即可，不需要安装）
#
#   bash maintenance/maintenance-mode.sh on      开启维护模式：整站（含所有子路由）显示维护页
#   bash maintenance/maintenance-mode.sh off     关闭维护模式：恢复正常访问
#   bash maintenance/maintenance-mode.sh status  查看状态（开关、维护页、后端端口）
#
# 原理：Caddy 配置里的 file 匹配器每次请求都会检查
#       maintenance/maintenance.on 是否存在，所以 on/off 立即生效，不需要 caddy reload。
#
# 注意：
#   · on/off 直接在本项目目录里创建、删除开关文件，属于计划内维护的推荐做法（返回 200）。
#   · 如果只是把 docker compose 停掉、没开开关，Caddy 也会自动兜底显示维护页（状态码 502）。
#   · maintenance.on 已加入 .gitignore，git pull 不会冲突。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAINT_DIR="${MAINT_DIR:-${HERE}}"
FLAG="${MAINT_DIR}/maintenance.on"
PAGE="${MAINT_DIR}/index.html"

usage() {
  cat <<EOF
用法: $(basename "$0") {on|off|status}

  on      开启维护模式（创建开关文件 ${FLAG}）
  off     关闭维护模式（删除开关文件）
  status  查看状态

环境变量:
  MAINT_DIR  维护页目录，默认脚本所在目录（${HERE}）
EOF
}

require_page() {
  if [ ! -s "${PAGE}" ]; then
    echo "❌ 维护页不存在或为空：${PAGE}" >&2
    echo "   请确认项目已完整更新（git pull），或在本地执行 maintenance/build.sh 重新生成。" >&2
    exit 1
  fi
}

case "${1:-}" in
  on)
    require_page
    touch "${FLAG}"
    echo "✅ 维护模式已开启：整个站点（所有子路由）现在显示维护页"
    echo "   开关文件：${FLAG}"
    echo "   验证：curl -s https://example.com/member/profile | grep -c 网站维护中"
    ;;
  off)
    rm -f "${FLAG}"
    echo "✅ 维护模式已关闭：网站已恢复（若后端仍未启动，会看到 502 兜底维护页）"
    ;;
  status)
    if [ -f "${FLAG}" ]; then
      echo "状态：🛠  维护模式已开启（${FLAG}）"
    else
      echo "状态：🟢 正常模式（无 ${FLAG}）"
    fi
    if [ -s "${PAGE}" ]; then
      echo "维护页：已就绪 ${PAGE}（$(wc -c < "${PAGE}" | tr -d ' ') 字节）"
    else
      echo "维护页：❌ 缺失 ${PAGE}"
    fi
    # 顺带看一下后端网关端口是否在监听
    if command -v ss >/dev/null 2>&1; then
      if ss -ltn 2>/dev/null | grep -q ':3403'; then
        echo "后端网关 127.0.0.1:3403：🟢 在监听"
      else
        echo "后端网关 127.0.0.1:3403：🔴 未监听（docker 服务未运行）"
      fi
    elif command -v lsof >/dev/null 2>&1; then
      if lsof -iTCP:3403 -sTCP:LISTEN >/dev/null 2>&1; then
        echo "后端网关 127.0.0.1:3403：🟢 在监听"
      else
        echo "后端网关 127.0.0.1:3403：🔴 未监听（docker 服务未运行）"
      fi
    fi
    ;;
  ""|-h|--help|help)
    usage
    ;;
  *)
    echo "未知参数：$1" >&2
    usage >&2
    exit 2
    ;;
esac
