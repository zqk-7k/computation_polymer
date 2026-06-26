#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$APP_DIR"

mkdir -p logs

if [ -f logs/backend.pid ] && kill -0 "$(cat logs/backend.pid)" 2>/dev/null; then
  echo "Backend is already running, pid=$(cat logs/backend.pid)"
  exit 0
fi

nohup java -jar backend/vasp-show-backend.jar \
  --server.port=8080 \
  --vasp.datasets.display-db-path=documents/data/frontend_template_data \
  > logs/backend.log 2>&1 &

echo $! > logs/backend.pid
echo "Backend started, pid=$(cat logs/backend.pid)"
