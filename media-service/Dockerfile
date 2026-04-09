# Stage 1: Build ứng dụng
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
# Tải trước dependencies giúp tăng tốc độ build cho các lần sau
RUN mvn dependency:go-offline -B
COPY src ./src
# Build file jar, bỏ qua chạy test để quá trình build nhanh hơn
RUN mvn clean package -DskipTests

# Stage 2: Môi trường chạy
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]