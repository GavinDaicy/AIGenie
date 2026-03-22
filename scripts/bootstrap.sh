#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_FILE="${ROOT_DIR}/query-genie-server/src/main/resources/sql/init.sql"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USERNAME:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-genie}"

echo "[1/3] Starting dependencies with docker compose..."
echo "    (Elasticsearch image includes IK analysis plugin; first run may build the image.)"
docker compose -f "${ROOT_DIR}/docker-compose.yml" up -d --build

echo "[2/3] Waiting for MySQL to be ready..."
for _ in $(seq 1 60); do
  if mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[3/3] Initializing database schema..."
mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" < "${SQL_FILE}"

echo "Bootstrap completed."
echo "Next steps:"
echo "  1) export DASHSCOPE_API_KEY=your-key"
echo "  2) cd query-genie-server && mvn spring-boot:run"
echo "  3) cd query-genie-front && npm install && npm run serve"
