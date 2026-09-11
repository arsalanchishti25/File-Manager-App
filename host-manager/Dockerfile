# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# Runtime stage with Docker CLI
FROM eclipse-temurin:21-jre

# Install Docker CLI in a single, cache-friendly layer
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        docker.io \
        ca-certificates \
        curl && \
    rm -rf /var/lib/apt/lists/* && \
    docker --version

WORKDIR /app

COPY --from=build /build/target/host-manager-1.0-SNAPSHOT.jar /app/app.jar

# The host Docker daemon must be exposed through /var/run/docker.sock at runtime.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]