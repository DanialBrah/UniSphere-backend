# ■■ Stage 1: Build the JAR with Maven ■■■■■■■■■■■■■■■■■■■■■■■■■■■
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw clean package -DskipTests

# ■■ Stage 2: Run with lightweight JRE ■■■■■■■■■■■■■■■■■■■■■■■■■■■
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/unisphere-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
