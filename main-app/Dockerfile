# The desktop runtime intentionally remains the existing JavaFX/VNC-compatible image.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/build/lib

FROM pedrombmachado/ntu_lubuntu:soft40051
# The desktop/VNC image does not include Java. Reuse the Java 21 runtime from
# the Maven build stage so the JavaFX 21 application matches its bytecode.
COPY --from=build /opt/java/openjdk /opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}" \
    JAVA_HOME="/opt/java/openjdk"
WORKDIR /app
COPY --from=build /build/target/main-app-1.0-SNAPSHOT.jar /app/main-app.jar
COPY --from=build /build/lib /app/lib
RUN mkdir -p /etc/xdg/autostart /data /data/logs \
    && chown -R ntu-user:ntu-user /data
COPY docker/start-javafx.sh /usr/local/bin/start-javafx.sh
COPY docker/startup-container.sh /usr/local/bin/startup-container.sh
COPY docker/run-main-app.sh /usr/local/bin/run-main-app.sh
COPY docker/file-manager-javafx.desktop /etc/xdg/autostart/file-manager-javafx.desktop
RUN sed -i 's/\r$//' \
      /usr/local/bin/start-javafx.sh \
      /usr/local/bin/startup-container.sh \
      /usr/local/bin/run-main-app.sh \
    && chmod +x \
      /usr/local/bin/start-javafx.sh \
      /usr/local/bin/startup-container.sh \
      /usr/local/bin/run-main-app.sh
# The base image creates DISPLAY only when an XRDP/VNC user session starts.
# Keep its entrypoint and launch JavaFX from that session's desktop autostart.
CMD ["/usr/local/bin/startup-container.sh"]
