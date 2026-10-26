#!/usr/bin/env bash
set -euo pipefail
audit_root="$(cd "$(dirname "$0")/.." && pwd)"
audit_mvn="$(command -v mvn || true)"
if [ -z "$audit_mvn" ]; then
  audit_mvn="$(find "$HOME/.m2/wrapper/dists" -path '*/bin/mvn' -type f -print -quit 2>/dev/null || true)"
fi
if [ -z "$audit_mvn" ]; then echo 'Java 21, Maven and Docker are required.' >&2; exit 1; fi
audit_container="paaweb-qqbot-audit-$$"
cleanup() { docker rm -f "$audit_container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run --rm -d --name "$audit_container" -e POSTGRES_DB=qqbot_audit -e POSTGRES_USER=qqbot_audit \
  -e POSTGRES_PASSWORD=qqbot_audit_only -p 127.0.0.1::5432 postgres:15-alpine >/dev/null
audit_ready=false
for audit_try in $(seq 1 30); do
  if docker exec "$audit_container" pg_isready -U qqbot_audit >/dev/null 2>&1; then audit_ready=true; break; fi
  sleep 1
done
if [ "$audit_ready" != true ]; then echo 'Temporary PostgreSQL did not become ready.' >&2; exit 1; fi
audit_port="$(docker port "$audit_container" 5432/tcp | sed -n 's/^127\.0\.0\.1://p')"
cd "$audit_root/backend"
"$audit_mvn" -q -Dtest='*QqBot*,Bot*Tests,HydroBotClientTests,ContestDurationParsingTests,OneBotConnectionTests' \
  -Dqqbot.audit=true "-Dqqbot.audit.jdbcUrl=jdbc:postgresql://127.0.0.1:$audit_port/qqbot_audit" \
  -Dqqbot.audit.username=qqbot_audit -Dqqbot.audit.password=qqbot_audit_only test
