#!/bin/bash
set -eu

echo "[main-app] Preparing persistent application data"
mkdir -p /data /data/logs
chown -R ntu-user:ntu-user /data

echo "[main-app] Starting XRDP"
if ! service xrdp start; then
    echo "[main-app] ERROR: XRDP failed to start" >&2
    exit 1
fi

echo "[main-app] Desktop services started; keeping container alive"
exec tail -f /dev/null
