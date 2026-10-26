#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

command -v docker >/dev/null || { echo "请先安装 Docker 和 Docker Compose。" >&2; exit 1; }
command -v curl >/dev/null || { echo "部署检查需要 curl。" >&2; exit 1; }
docker compose version >/dev/null
bash scripts/init-env.sh
docker compose config --quiet

echo "正在构建并启动 PAA Web……"
docker compose up -d --build
# Recreate the gateway to refresh upstream container addresses after upgrades.
docker compose up -d --no-deps --force-recreate gateway
docker compose exec -T gateway nginx -t

# Read the published port from Compose, respecting .env and shell overrides.
binding=$(docker compose port gateway 80 | head -n 1)
port=${binding##*:}
check_host=${binding%:*}
case "$check_host" in
  0.0.0.0) check_host=127.0.0.1 ;;
  '::'|'[::]') check_host='[::1]' ;;
esac
if [[ "$check_host" == *:* && "$check_host" != \[*\] ]]; then
  check_host="[$check_host]"
fi
check_url="http://${check_host}:${port}"
echo "等待首页及后端接口就绪……"
for attempt in {1..90}; do
  if curl -fsS --connect-timeout 2 --max-time 5 "$check_url/" >/dev/null 2>&1 \
    && curl -fsS --connect-timeout 2 --max-time 5 "$check_url/api/intro/showcase_bg" 2>/dev/null \
      | grep -Eq '"code"[[:space:]]*:[[:space:]]*200([,}[:space:]])'; then
    echo "部署完成：$check_url"
    echo "首次登录：admin / admin123456，请运行 bash scripts/reset-admin-password.sh 修改初始密码。"
    exit 0
  fi
  sleep 2
done
echo "服务尚未就绪，请检查 docker compose ps 和 docker compose logs --tail=100。" >&2
exit 1
