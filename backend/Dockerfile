# syntax=docker/dockerfile:1.7
# ---------- build: compila y separa el jar en capas (dependencias casi nunca cambian → caché) ----------
FROM maven:3-eclipse-temurin-26-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline
COPY src ./src
# Los tests corren en CI (necesitan Docker para Testcontainers); aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q package -DskipTests -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/textrack-api.jar extract --layers --launcher --destination target/app

# ---------- runtime: solo JRE, usuario sin privilegios ----------
FROM eclipse-temurin:25.0.3_9-jre-alpine-3.23
RUN addgroup -S textrack && adduser -S -G textrack -u 10001 textrack
WORKDIR /app
COPY --from=build --chown=textrack:textrack /workspace/target/app/dependencies/ ./
COPY --from=build --chown=textrack:textrack /workspace/target/app/spring-boot-loader/ ./
COPY --from=build --chown=textrack:textrack /workspace/target/app/snapshot-dependencies/ ./
COPY --from=build --chown=textrack:textrack /workspace/target/app/application/ ./
USER 10001
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health/liveness" > /dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
