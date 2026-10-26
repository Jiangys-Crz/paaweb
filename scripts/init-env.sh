#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ -e .env ]; then
  if grep -q '^POSTGRES_PASSWORD=CHANGE_ME_GENERATE_WITH_INIT_ENV$' .env; then
    echo ".env 仍包含数据库密码占位符，请删除未使用的 .env 后重新初始化，或自行填写随机密码。" >&2
    exit 1
  fi
  exit 0
fi

command -v openssl >/dev/null || { echo "请先安装 OpenSSL，或手动复制 .env.example 并填写随机数据库密码。" >&2; exit 1; }
umask 077
env_tmp=$(mktemp .env.init.XXXXXX)
trap 'rm -f "$env_tmp"' EXIT
db_password=$(openssl rand -hex 32)
sed "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$db_password/" .env.example > "$env_tmp"
# A hard link creates the file atomically and does not overwrite another process.
if ln "$env_tmp" .env 2>/dev/null; then
  echo "已创建 .env 并生成独立数据库密码。可按 README 配置站点地址与可选服务。"
elif [ ! -e .env ]; then
  echo "无法创建 .env，请检查目录权限。" >&2
  exit 1
fi
