#!/bin/bash
set -eu

export MYSQL_HOST="${MYSQL_HOST:-mysql}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-filemanager}"
export MYSQL_USER="${MYSQL_USER:-fileapp}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-fileapp123}"
export SFTP_USER="${SFTP_USER:-sftpuser}"
export SFTP_PASSWORD="${SFTP_PASSWORD:-sftppass123}"
export APP_DATA_DIR="${APP_DATA_DIR:-/data}"
export SQLITE_DB_PATH="${SQLITE_DB_PATH:-${APP_DATA_DIR}/fileapp.db}"

exec /opt/java/openjdk/bin/java \
  --module-path "/app/lib" \
  --add-modules javafx.controls,javafx.fxml \
  -cp "/app/main-app.jar:/app/lib/*" \
  com.example.mainapp.App
