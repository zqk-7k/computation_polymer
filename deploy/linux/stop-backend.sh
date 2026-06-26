#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PID_FILE="$APP_DIR/logs/backend.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "Backend pid file not found."
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Backend stopped, pid=$PID"
else
  echo "Backend process is not running."
fi

rm -f "$PID_FILE"
