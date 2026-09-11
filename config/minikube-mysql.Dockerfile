FROM mysql:8.0
COPY mysql-init.sql /docker-entrypoint-initdb.d/init.sql
