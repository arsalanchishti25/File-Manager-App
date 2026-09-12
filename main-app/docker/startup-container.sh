#!/bin/bash
set -eu

echo "[main-app] Preparing persistent application data"
mkdir -p /data /data/logs
chown -R ntu-user:ntu-user /data

echo "[main-app] Starting XRDP"

xrdp_port_listening() {
    if command -v ss >/dev/null 2>&1; then
        ss -lnt 2>/dev/null | awk '
            {
                for (i = 1; i <= NF; i++) {
                    if ($i ~ /(^|:)3389$/) {
                        found = 1
                    }
                }
            }
            END { exit(found ? 0 : 1) }
        '
    elif command -v netstat >/dev/null 2>&1; then
        netstat -lnt 2>/dev/null | awk '
            {
                for (i = 1; i <= NF; i++) {
                    if ($i ~ /(^|:)3389$/) {
                        found = 1
                    }
                }
            }
            END { exit(found ? 0 : 1) }
        '
    else
        echo "[main-app] ERROR: Neither ss nor netstat is available to check XRDP" >&2
        return 1
    fi
}

if xrdp_port_listening; then
    echo "[main-app] XRDP is already running on port 3389"
else
    if ! service xrdp start && ! xrdp_port_listening; then
        echo "[main-app] ERROR: XRDP failed to start" >&2
        exit 1
    fi

    xrdp_ready=false
    for i in 1 2 3 4 5 6 7 8 9 10; do
        if xrdp_port_listening; then
            xrdp_ready=true
            break
        fi
        sleep 1
    done

    if [ "$xrdp_ready" != true ]; then
        echo "[main-app] ERROR: XRDP did not begin listening on port 3389" >&2
        exit 1
    fi
fi

echo "[main-app] Desktop services started; keeping container alive"
exec tail -f /dev/null
