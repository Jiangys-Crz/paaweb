#!/usr/bin/env bash
# Reset an administrator password through the deployment's local PostgreSQL.
# Passwords are read from stdin, never passed as command-line arguments.
set -euo pipefail
set +x
cd "$(dirname "${BASH_SOURCE[0]}")/.."

admin_account=${1:-admin}
if [ "$#" -gt 1 ] || [[ ! "$admin_account" =~ ^[A-Za-z0-9]{3,20}$ ]]; then
  echo "用法：bash scripts/reset-admin-password.sh [管理员账号，默认 admin]" >&2
  exit 1
fi
command -v docker >/dev/null || { echo "请先安装 Docker。" >&2; exit 1; }
command -v openssl >/dev/null || { echo "请先安装 OpenSSL。" >&2; exit 1; }
docker compose config --quiet

echo "将重置管理员 $admin_account 的密码，并使该账号现有会话失效。"
IFS= read -r -s -p "请输入新密码（至少 8 个字符）：" admin_password
printf '\n'
IFS= read -r -s -p "请再次输入新密码：" admin_password_confirm
printf '\n'
if [ "${#admin_password}" -lt 8 ]; then
  echo "密码至少需要 8 个字符。" >&2
  exit 1
fi
if [ "$admin_password" != "$admin_password_confirm" ]; then
  echo "两次输入的密码不一致，未修改。" >&2
  exit 1
fi

# Match the existing browser MD5 -> backend MD5 compatibility flow.
first_digest=$(printf '%s' "$admin_password" | openssl dgst -md5 -r)
first_digest=${first_digest%% *}
second_digest=$(printf '%s' "$first_digest" | openssl dgst -md5 -r)
second_digest=${second_digest%% *}
second_digest=$(printf '%s' "$second_digest" | tr 'a-f' 'A-F')
unset admin_password admin_password_confirm first_digest

# Both interpolated values are constrained to alphanumeric account / hex digest.
changed=$(docker compose exec -T db sh -c \
  'exec psql -X -qAt --set=ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
WITH changed AS (
  UPDATE users
  SET password = '$second_digest', auth_version = auth_version + 1
  WHERE lower(user_account) = lower('$admin_account')
    AND role IN ('super_admin', 'supper_admin')
  RETURNING user_id
)
SELECT count(*) FROM changed;
SQL
)
unset second_digest
if [ "$changed" != "1" ]; then
  echo "未找到唯一的超级管理员账号，请检查账号和当前 Compose 项目。" >&2
  exit 1
fi
echo "密码已更新，原登录会话已失效。请使用新密码重新登录。"
