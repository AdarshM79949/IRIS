# Multi-stage Dockerfile for Java Spring Boot Chat App
# Stage 1: Build the Application
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Production JRE Image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/chat-app-backend-1.0.0.jar app.jar

# Cloud providers (Render, Railway, Heroku) inject $PORT
ENV PORT=5000
EXPOSE 5000

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-5000}"]
