#!/bin/bash
set -eu

lock_dir="/tmp/file-manager-javafx.lock"
if ! mkdir "$lock_dir" 2>/dev/null; then
    exit 0
fi

cleanup_lock() {
    rmdir "$lock_dir" 2>/dev/null || true
}

trap cleanup_lock EXIT
trap 'exit 143' HUP INT TERM

data_dir="${APP_DATA_DIR:-/data}"
db_path="${SQLITE_DB_PATH:-${data_dir}/fileapp.db}"
log_dir="${data_dir}/logs"
log_file="${log_dir}/main-app.log"

mkdir -p "$log_dir" 2>/dev/null || true

if [ -z "${DISPLAY:-}" ]; then
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        sleep 2
        if [ -n "${DISPLAY:-}" ]; then
            break
        fi
    done
fi

if [ -z "${DISPLAY:-}" ]; then
    printf '%s\n' "Unable to start JavaFX: DISPLAY is not available" >> "$log_file" 2>&1 || true
    exit 1
fi

{
    printf '%s\n' "Starting Main App with DISPLAY=$DISPLAY SQLITE_DB_PATH=$db_path"
    /usr/local/bin/run-main-app.sh
} >> "$log_file" 2>&1
