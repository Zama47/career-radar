# Multi-stage build. Build context is the repository root:
#   docker build -f core-api/Dockerfile -t career-radar-core-api .
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

# Warm up dependency resolution with just the build descriptors.
COPY settings.gradle.kts gradle.properties ./
COPY core-api/build.gradle.kts core-api/build.gradle.kts
RUN gradle :core-api:dependencies --no-daemon >/dev/null 2>&1 || true

COPY core-api/src core-api/src
RUN gradle :core-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/core-api/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
