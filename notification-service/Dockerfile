# ── Stage 1: Build ────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies trước (tận dụng Docker layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run (image nhỏ gọn) ─────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Bảo mật: chạy bằng user không phải root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]