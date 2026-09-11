#!/bin/bash
set -eu

export MYSQL_HOST="${MYSQL_HOST:-mysql}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-filemanager}"
export MYSQL_USER="${MYSQL_USER:-fileapp}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-fileapp123}"
export APP_DATA_DIR="${APP_DATA_DIR:-/data}"
export SQLITE_DB_PATH="${SQLITE_DB_PATH:-${APP_DATA_DIR}/fileapp.db}"

exec java \
  --module-path "/app/lib" \
  --add-modules javafx.controls,javafx.fxml \
  -cp "/app/main-app.jar:/app/lib/*" \
  com.example.mainapp.App
