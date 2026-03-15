# syntax=docker/dockerfile:1

# ---------------------------
# 1️⃣ Build Stage
# ---------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Gradle wrapper 먼저 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# 의존성 다운로드
RUN ./gradlew dependencies --no-daemon

# 소스 복사
COPY src src

# jar 빌드
RUN ./gradlew bootJar --no-daemon -x test


# ---------------------------
# 2️⃣ Runtime Stage
# ---------------------------
FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

# jar 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# non-root user 생성
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup

USER appuser

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT}"]