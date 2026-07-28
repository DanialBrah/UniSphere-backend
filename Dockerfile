# ■■ Stage 1: Build the JAR with Maven ■■■■■■■■■■■■■■■■■■■■■■■■■■■
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw clean package -DskipTests

# ■■ Stage 2: Run with lightweight JRE ■■■■■■■■■■■■■■■■■■■■■■■■■■■
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=builder /app/target/unisphere-backend-0.0.1-SNAPSHOT.jar app.jar
RUN chown spring:spring app.jar
USER spring
EXPOSE ${PORT:-8081}
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
