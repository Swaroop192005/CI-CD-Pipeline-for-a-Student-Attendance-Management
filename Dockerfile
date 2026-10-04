# Container image for the Student Attendance Management Portal.
#
# The image packages the WAR that Maven already produced; it does not
# rebuild it. That is deliberate, and it is the stronger guarantee:
#
#   - The pipeline builds the artefact once, runs the unit tests, the
#     integration tests and the Selenium quality gate against *that*
#     artefact, and only then builds this image around it. Rebuilding
#     inside the image would ship a binary nothing had tested - bit-for-bit
#     reproducible builds are hard, and "it was the same source" is not the
#     same claim as "it was the same artefact".
#   - It keeps the image free of a build toolchain, which is both wasted
#     space and a larger attack surface than the application itself.
#   - It builds in seconds rather than minutes, because there is no
#     dependency resolution.
#
# Build it with docker/build-image.sh, which runs the Maven package first
# if the artefact is missing and supplies the build metadata below.

FROM eclipse-temurin:21-jre-jammy

# Build metadata, supplied by the pipeline. OCI labels, so that
# `docker inspect` on a running container answers "which commit is this?"
# rather than leaving someone to guess from a tag.
ARG APP_VERSION=dev
ARG GIT_COMMIT=unknown
ARG BUILD_TIME=unknown

LABEL org.opencontainers.image.title="Student Attendance Management Portal" \
      org.opencontainers.image.description="Role-aware attendance system of record" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      org.opencontainers.image.source="https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management" \
      org.opencontainers.image.licenses="academic-project"

# Run as a non-root user. The application never writes outside its data
# directory, and a container compromise should not start out as root.
RUN groupadd --system --gid 1001 attendance \
 && useradd --system --uid 1001 --gid attendance \
            --create-home --home-dir /home/attendance attendance \
 && mkdir -p /app/data \
 && chown -R attendance:attendance /app

WORKDIR /app

COPY --chown=attendance:attendance app/target/attendance.war /app/attendance.war

USER attendance

# Defaults, every one overridable at run time without a rebuild
# (SRS NFR-08). That is what lets one image serve staging and production,
# and what Stage 13's Ansible playbook templates.
ENV SERVER_PORT=8080 \
    SERVER_SERVLET_CONTEXT_PATH=/attendance \
    SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/attendance;DB_CLOSE_ON_EXIT=FALSE" \
    ATTENDANCE_ENVIRONMENT=container \
    ATTENDANCE_ELIGIBILITY_THRESHOLD=75 \
    ATTENDANCE_SEED_DATA=true \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

EXPOSE 8080

# The datastore lives on a volume, so stopping and removing the container
# does not destroy the attendance records - which is exactly what the
# Stage 11 lifecycle demonstration sets out to show.
VOLUME ["/app/data"]

# Both the pipeline and the Ansible playbook gate on this. The start period
# is generous because a cold JVM plus schema creation takes a few seconds,
# and a healthcheck that reports unhealthy during normal startup trains
# people to ignore it.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider \
        "http://localhost:${SERVER_PORT}${SERVER_SERVLET_CONTEXT_PATH}/actuator/health" || exit 1

# exec, so the JVM is PID 1 and receives SIGTERM directly. Without it the
# signal reaches a shell that does not forward it, so `docker stop` always
# waits the full timeout and then kills - losing the clean shutdown and
# making every stop take ten seconds.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/attendance.war"]
