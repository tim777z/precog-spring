# syntax=docker/dockerfile:1
#
# Two-stage build: the Maven toolchain and the local repository it fills never reach the
# published image, which keeps the runtime free of a compiler, a shell history of source
# and ~150 MB of build tooling that only widens the attack surface.

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy the POM first and resolve dependencies in their own layer. Source-only changes then
# reuse the cached dependency layer, which is the difference between a 5 second and a
# 90 second rebuild.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY config ./config
COPY src ./src

# Tests are skipped here because CI already ran `mvn verify` including the coverage gate;
# the image build should not be the only place the suite ever runs, nor the slowest.
RUN mvn -B -ntp package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime

# curl is needed by the HEALTHCHECK below. In Kubernetes prefer the actuator liveness and
# readiness probes (management.endpoint.health.probes.enabled=true) and drop this layer.
RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/*

# Unprivileged, fixed uid/gid, no login shell and no home directory. A container escape
# starts from a compromised process, so the process that gets there should own nothing.
RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid 1001 --no-create-home --shell /usr/sbin/nologin spring

WORKDIR /app

# Read-only: the application writes nothing to disk, so the jar and the extracted Spring
# Boot classes are the only things the image needs.
COPY --from=build --chown=1001:1001 /build/target/*.jar /app/app.jar

USER 1001:1001

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM reads the container memory limit, so
# the heap cannot outgrow the cgroup and get the process OOM-killed mid-request.
# ExitOnOutOfMemoryError makes a genuine heap exhaustion a loud crash to restart rather
# than a service limping along on a corrupted heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# /actuator/health is unauthenticated on purpose: a liveness probe has nowhere to put
# credentials, which is exactly why show-details is set to never.
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Exec form, no shell: the JVM is PID 1 and receives SIGTERM directly, which is what makes
# server.shutdown=graceful actually drain in-flight requests on a rolling deploy. JAVA_TOOL_OPTIONS
# is read by the JVM itself, so no shell is needed to interpolate options.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
