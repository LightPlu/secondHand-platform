# syntax=docker/dockerfile:1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT}"]

COPY --from=builder /app/build/libs/*.jar /app/app.jar

USER appuser
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup
# Use a non-root user

EXPOSE 8080
ENV PORT=8080
# Fly.io 기본 포트(설정에 따라 변경 가능)

WORKDIR /app
FROM eclipse-temurin:21-jre AS runner
# 2) Runtime stage

RUN ./gradlew bootJar --no-daemon -x test
COPY src src
# Copy source code and build executable jar

RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew gradlew
# Copy Gradle wrapper and build scripts first for better layer caching

WORKDIR /app
FROM eclipse-temurin:21-jdk AS builder
# 1) Build stage

