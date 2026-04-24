FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package && \
    cp target/mock-brevo-*.jar target/mock-brevo.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# OCI labels — GitHub Packages links the image back to the repo when `source` matches
ARG IMAGE_VERSION=0.0.0-snapshot
ARG SOURCE_URL=https://github.com/OWNER/mock-brevo
LABEL org.opencontainers.image.title="mock-brevo" \
      org.opencontainers.image.description="Local mock of the Brevo (ex-Sendinblue) transactional API for dev and integration tests. Not affiliated with Brevo SAS." \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="$SOURCE_URL" \
      org.opencontainers.image.url="$SOURCE_URL" \
      org.opencontainers.image.documentation="$SOURCE_URL#readme" \
      org.opencontainers.image.version="$IMAGE_VERSION" \
      org.opencontainers.image.vendor="mock-brevo contributors"

# curl for HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/data && \
    groupadd --system app && useradd --system --gid app --home /app app && \
    chown -R app:app /app
USER app

COPY --from=build /build/target/mock-brevo.jar /app/mock-brevo.jar

ENV MOCK_BREVO_DB_PATH=/app/data/brevo \
    SERVER_PORT=8080 \
    JAVA_OPTS=""

EXPOSE 8080
VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/mock-status" -o /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/mock-brevo.jar"]
