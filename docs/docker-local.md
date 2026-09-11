# Local Docker foundation

## Requirements

- Docker Engine or Docker Desktop with Compose v2.
- Maven 3.9+ and Java 21 for local packaging outside Docker.
- A working Docker build network for downloading Maven dependencies and base images.

Each service Dockerfile uses a Java 21 Maven build stage. The load balancer,
aggregator, and file-storage Maven Shade configurations produce runnable
dependency-inclusive JARs. HostManager produces a manifest JAR. The Main App
uses its packaged JAR plus runtime dependencies copied into `/app/lib`.

## Images

The Compose file builds these local tags:

- `file-manager/main-app:latest`
- `file-manager/load-balancer:latest`
- `file-manager/aggregator:latest`
- `file-manager/file-storage:latest`
- `file-manager/host-manager:latest`

Build all images:

```powershell
docker compose build
```

Build one image:

```powershell
docker compose build load-balancer
```

## Compose usage

Start the local integration stack:

```powershell
docker compose up -d
```

Inspect status and logs:

```powershell
docker compose ps
docker compose logs -f load-balancer
```

Stop the stack:

```powershell
docker compose down
```

MySQL data, MQTT data, the Main App SQLite data, aggregator data, and the four
storage data directories use named volumes and survive ordinary `down`.

## Configuration

The Compose values follow the repository environment contract, including
`MQTT_BROKER_URL`, MySQL connection variables, `MAIN_APP_ID`,
`SQLITE_DB_PATH`, `APP_DATA_DIR`, `AGGREGATOR_ID`, `AGGREGATOR_WORK_DIR`,
`ENCRYPTION_KEY`, `FS_CONTAINER_ID`, `VOLUME_GROUP`, `STORAGE_DATA_DIR`,
`SFTP_PORT`, `SFTP_USER`, and `SFTP_PASSWORD`. The values in the checked-in
Compose file are development placeholders only. Use an uncommitted override
file or environment substitution for non-development credentials.

## JavaFX and HostManager assumptions

Main App retains `pedrombmachado/ntu_lubuntu:soft40051` as its runtime base
so the existing desktop/VNC environment remains available. The base image's
desktop entrypoint is expected to preserve its display and VNC startup while
its command launches `com.example.mainapp.App`. The GUI is not converted to a
headless Java runtime. Access the desktop through RDP at `localhost:3390`. After login as the
desktop user, the JavaFX application starts from the session autostart entry.
Persistent Main App data is stored in `/data`, including the SQLite database at
`/data/fileapp.db` and startup logs at `/data/logs/main-app.log`.

Inspect the service and application log with:

```powershell
docker compose ps
docker compose logs --tail=200 main-app
docker exec filemanager-mainapp sh -c 'ls -ld /data /data/logs'
```

To reset only Main App data for troubleshooting, stop the stack and remove the
project-scoped named volume. This deletes the local SQLite database:

```powershell
docker compose down
docker volume rm <project>_main-app-data
```

HostManager invokes Docker CLI commands against the host daemon. Compose
therefore maps `/var/run/docker.sock` and sets `DOCKER_HOST`. This is a
privileged host integration, not Docker-in-Docker; it is available on Linux
and Docker Desktop environments that expose the socket. The service is not
safe to use without that socket.

## Known limitations

- This is a local integration foundation, not a production deployment.
- File Storage and Aggregator entries are static so basic file flow can be
  exercised; HostManager dynamic orchestration remains unchanged.
- Health checks gate startup for MySQL and MQTT only; application-level
  readiness and recovery are not implemented here.
- The Compose file uses development credentials and publishes SFTP, MySQL,
  MQTT, and desktop ports to the local host.
